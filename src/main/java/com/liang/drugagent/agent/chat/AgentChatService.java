package com.liang.drugagent.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 主服务 - 上层会话编排服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>面向前端接住一次对话请求</li>
 *   <li>把请求整理成统一上下文</li>
 *   <li>驱动路由、场景分发、降级和结果回写</li>
 *   <li>将后端复杂执行链路包装成前端可消费的统一响应</li>
 * </ul>
 *
 * <p>标准主流程：会话中提取前文信息 -> 构建上下文 -> AgentSceneService.decideAndExecute -> 结果回写 -> 响应返回
 *
 * @author liangjiajian
 * @see AgentSceneService
 * @see AgentResponseService
 * @see AgentSessionService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentSceneService agentSceneService;
    private final AgentSessionService agentSessionService;
    private final AgentResponseService agentResponseService;
    private final AgentMessageService agentMessageService;
    private final TencentCosStorageService cosStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * 同步对话处理主入口。
     *
     * <p>完整流程：
     * 1. 通过 AgentSessionService 加载会话上下文
     * 2. 保存本轮上传文件
     * 3. 构建本轮执行上下文
     * 4. 调用 AgentSceneService 执行场景判断与分发
     * 5. 保存用户消息和助手消息
     * 6. 返回统一响应
     *
     * @param req 对话请求
     * @return 统一响应
     */
    public AgentChatResp chat(AgentChatReq req) {
        log.info("[AgentChatService] 收到对话请求: sessionId={}",
                req != null ? req.getSessionId() : null);

        try {
            // 1. 获取或创建会话
            ChatSession session = agentSessionService.getOrCreateSession(req.getSessionId());
            String sessionId = session.getId();

            // 2. 保存本轮上传文件
            List<OssFile> uploadedFiles = saveUploadedFiles(sessionId, req);

            // 3. 合并 fileIds：将本轮上传文件的 ID 也加入
            mergeFileIds(req, uploadedFiles);

            // 4. 读取最近消息和摘要，构建上下文
            List<ChatMessage> recentMessages = agentMessageService.getRecentMessages(sessionId, 20);
            AgentChatContext context = AgentChatContext.from(req, sessionId);
            context.setSession(session);
            context.setHistoryMessages(recentMessages);
            context.setRecentSummary(session.getSummary());
            context.setUploadedFiles(uploadedFiles);

            log.info("[AgentChatService] 构建执行上下文: sessionId={}, traceId={}, historyCount={}, uploadedFilesCount={}, req.fileIds={}",
                    sessionId, context.getTraceId(), recentMessages.size(), uploadedFiles.size(), req.getFileIds());

            // 5. 调用 AgentSceneService 执行场景判断与分发
            AgentSceneService.AgentSceneExecution execution = agentSceneService.decideAndExecute(context, req);

            // 4. 如需要澄清，先保存用户消息再返回澄清响应（避免消息丢失）
            if (execution.isNeedsClarification()) {
                agentMessageService.saveUserMessage(sessionId, req.getQuery(), null);
                agentSessionService.increaseMessageCount(sessionId, 1);
                return buildClarificationResp(context, execution.getDecision(), execution.getClarificationQuestion());
            }

            AgentExecutionResult executionResult = execution.getExecutionResult();

            // 5. 保存用户消息
            agentMessageService.saveUserMessage(sessionId, req.getQuery(), null);

            // 6. 保存助手消息，根据执行结果决定消息类型和 metadata
            String assistantContent = executionResult.getAnswer() != null ? executionResult.getAnswer() : executionResult.getSummary();
            String messageType;
            String metadataJson = null;
            if (executionResult.isNeedsFallback()) {
                messageType = "assistant_clarify";
            } else if (executionResult.getReport() != null || executionResult.getRiskLevel() != null) {
                // 标书审查等结构化结果场景：存储为结果卡片类型，并将完整响应序列化为 metadata
                messageType = "assistant_result_card";
                try {
                    AgentChatResp respForMetadata = agentResponseService.buildResponse(context, execution.getDecision(), executionResult);
                    metadataJson = objectMapper.writeValueAsString(respForMetadata);
                } catch (Exception ex) {
                    log.warn("[AgentChatService] 序列化 metadata 失败，将以空 metadata 保存: {}", ex.getMessage());
                }
            } else if (executionResult.getReasoningContent() != null && !executionResult.getReasoningContent().isBlank()) {
                messageType = "assistant_text";
                try {
                    AgentChatResp respForMetadata = agentResponseService.buildResponse(context, execution.getDecision(), executionResult);
                    metadataJson = objectMapper.writeValueAsString(respForMetadata);
                } catch (Exception ex) {
                    log.warn("[AgentChatService] 序列化 reasoning metadata 失败，将以空 metadata 保存: {}", ex.getMessage());
                }
            } else {
                messageType = "assistant_text";
            }
            agentMessageService.saveAssistantMessage(sessionId, assistantContent, metadataJson, messageType);

            // 7. 更新会话聚合状态
            String scene = execution.getDecision() != null ? execution.getDecision().getScene().name() : null;
            agentSessionService.touchSession(sessionId, scene);
            agentSessionService.increaseMessageCount(sessionId, 2);

            // 8. 如需要更新标题
            if (executionResult.isShouldUpdateTitle()) {
                // 优先使用 LLM 生成的标题，否则用 query 截取
                String title = executionResult.getGeneratedTitle();
                if (title == null || title.isBlank()) {
                    title = req.getQuery();
                }
                agentSessionService.updateSessionTitleIfNeeded(sessionId, title);
            }

            // 9. 更新会话摘要（阈值策略可后续优化）
            if (executionResult.getSummary() != null) {
                agentSessionService.updateSessionSummary(sessionId, executionResult.getSummary());
            }

            // 10. 返回统一响应
            return agentResponseService.buildResponse(context, execution.getDecision(), executionResult);
        } catch (Exception e) {
            log.error("[AgentChatService] 对话执行失败: {}", e.getMessage(), e);
            return fallback(req, null, e);
        }
    }

    /**
     * 流式对话处理（Phase 1 实现）。
     *
     * <p>当前实现：对于 DEFAULT 场景，使用 LLM 流式输出；
     * 对于复杂场景（如标书审查），降级为同步处理。
     *
     * <p>后续优化方向：
     * <ul>
     *   <li>Phase 2: LLM 层真流式</li>
     *   <li>Phase 3: 全链路流式编排</li>
     * </ul>
     *
     * @param req 对话请求
     * @return 流式响应
     */
    public Flux<ServerSentEvent<AgentChatResp>> streamChat(AgentChatReq req) {
        log.info("[AgentChatService] 收到流式对话请求: sessionId={}, stream={}",
                req != null ? req.getSessionId() : null,
                req != null ? req.getStream() : false);

        // 复杂场景降级为同步处理
        if (req.getSceneHint() == null && !shouldUseStreaming(req)) {
            log.info("[AgentChatService] 流式请求检测到复杂场景，降级为同步处理");
            return Flux.create(sink -> {
                AgentChatResp resp = chat(req);
                sink.next(ServerSentEvent.<AgentChatResp>builder()
                        .event("message")
                        .data(resp)
                        .build());
                sink.complete();
            });
        }

        // 流式处理 DEFAULT 场景
        return streamChatDefault(req);
    }

    /**
     * 判断是否应该使用流式处理。
     */
    private boolean shouldUseStreaming(AgentChatReq req) {
        // 有上传文件或明确指定标书审查场景，不使用流式
        if (req.getFiles() != null && req.getFiles().length > 0) {
            return false;
        }
        if ("TENDER_REVIEW".equalsIgnoreCase(req.getSceneHint())) {
            return false;
        }
        // 有 fileIds，可能是标书审查相关，不使用流式
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 流式处理 DEFAULT 场景。
     */
    private Flux<ServerSentEvent<AgentChatResp>> streamChatDefault(AgentChatReq req) {
        return Flux.create(sink -> {
            try {
                // 1. 获取或创建会话
                ChatSession session = agentSessionService.getOrCreateSession(req.getSessionId());
                String sessionId = session.getId();

                // 2. 保存用户消息
                agentMessageService.saveUserMessage(sessionId, req.getQuery(), null);

                // 3. 构建上下文并执行场景判断
                List<ChatMessage> recentMessages = agentMessageService.getRecentMessages(sessionId, 20);
                AgentChatContext context = AgentChatContext.from(req, sessionId);
                context.setSession(session);
                context.setHistoryMessages(recentMessages);
                context.setRecentSummary(session.getSummary());

                log.info("[AgentChatService] 流式对话执行: sessionId={}, traceId={}, historyCount={}",
                        sessionId, context.getTraceId(), recentMessages.size());

                // 4. 执行场景路由判断
                WorkflowRouteDecision decision = agentSceneService.decideRoute(context, req);

                // 5. 复杂场景或需要澄清，降级为同步
                if (decision == null || decision.isRequiresClarification() || decision.getScene() != SceneEnum.DEFAULT) {
                    log.info("[AgentChatService] 流式检测到非DEFAULT场景或需要澄清，降级为同步");
                    AgentSceneService.AgentSceneExecution execution = agentSceneService.decideAndExecute(context, req);
                    AgentChatResp resp = agentResponseService.buildResponse(context,
                            execution.getDecision(), execution.getExecutionResult());
                    sink.next(ServerSentEvent.<AgentChatResp>builder()
                            .event("message")
                            .data(resp)
                            .build());
                    sink.complete();
                    return;
                }

                // 6. 开启真流式返回 LLM 响应
                final String finalSessionId = sessionId;
                final String finalTraceId = context.getTraceId();
                Flux<LlmResponse> tokenStream = agentSceneService.streamGeneralChat(context);
                StringBuilder fullAnswer = new StringBuilder();
                StringBuilder fullReasoning = new StringBuilder();

                tokenStream.subscribe(chunk -> {
                    if (chunk == null) {
                        return;
                    }

                    String answerChunk = chunk.getContent();
                    String reasoningChunk = chunk.getReasoningContent();
                    boolean hasAnswer = answerChunk != null && !answerChunk.isEmpty();
                    boolean hasReasoning = reasoningChunk != null && !reasoningChunk.isEmpty();

                    if (!hasAnswer && !hasReasoning) {
                        return;
                    }

                    if (hasAnswer) {
                        fullAnswer.append(answerChunk);
                    }
                    if (hasReasoning) {
                        fullReasoning.append(reasoningChunk);
                    }

                    AgentChatResp resp = AgentChatResp.builder()
                            .sessionId(finalSessionId)
                            .traceId(finalTraceId)
                            .scene(SceneEnum.DEFAULT.name())
                            .answer(hasAnswer ? answerChunk : null)
                            .reasoningContent(hasReasoning ? reasoningChunk : null)
                            .streamed(true)
                            .build();
                    sink.next(ServerSentEvent.<AgentChatResp>builder().event("message").data(resp).build());
                }, error -> {
                    log.error("[AgentChatService] 流式传输失败", error);
                    sink.error(error);
                }, () -> {
                    // 发送完成信号
                    sink.next(ServerSentEvent.<AgentChatResp>builder()
                            .event("done")
                            .data(AgentChatResp.builder()
                                    .sessionId(finalSessionId)
                                    .traceId(finalTraceId)
                                    .scene(SceneEnum.DEFAULT.name())
                                    .answer("")
                                    .streamed(true)
                                    .build())
                            .build());

                    // 保存助手消息，落库
                    String metadataJson = null;
                    try {
                        AgentChatResp respForMetadata = AgentChatResp.builder()
                                .sessionId(finalSessionId)
                                .traceId(finalTraceId)
                                .scene(SceneEnum.DEFAULT.name())
                                .answer(fullAnswer.toString())
                                .reasoningContent(fullReasoning.toString())
                                .streamed(true)
                                .build();
                        metadataJson = objectMapper.writeValueAsString(respForMetadata);
                    } catch (Exception ex) {
                        log.warn("[AgentChatService] 序列化流式通用对话 metadata 失败，将以空 metadata 保存: {}", ex.getMessage());
                    }
                    agentMessageService.saveAssistantMessage(finalSessionId, fullAnswer.toString(), metadataJson, "assistant_text");
                    agentSessionService.touchSession(finalSessionId, SceneEnum.DEFAULT.name());
                    agentSessionService.increaseMessageCount(finalSessionId, 2);

                    sink.complete();
                });

            } catch (Exception e) {
                log.error("[AgentChatService] 流式对话异常: {}", e.getMessage(), e);
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 废弃的模拟推流逻辑已删除。
     */

    /**
     * 构建澄清响应。
     */
    private AgentChatResp buildClarificationResp(AgentChatContext context,
                                                 WorkflowRouteDecision decision,
                                                 String clarifyQuestion) {
        log.info("[AgentChatService] 需要澄清: sessionId={}, question={}",
                context.getSessionId(), clarifyQuestion);

        AgentExecutionResult clarifyResult = AgentExecutionResult.builder()
                .success(false)
                .scene(decision != null ? decision.getScene() : SceneEnum.UNKNOWN)
                .errorMessage(clarifyQuestion)
                .clarificationQuestion(clarifyQuestion)
                .needsFallback(true)
                .build();

        return agentResponseService.buildResponse(context, decision, clarifyResult);
    }

    /**
     * 降级处理。
     */
    private AgentChatResp fallback(AgentChatReq req, WorkflowRouteDecision decision, Exception e) {
        log.warn("[AgentChatService] 执行降级处理: {}", e.getMessage());

        AgentChatResp resp = new AgentChatResp();
        resp.setSessionId(req != null ? req.getSessionId() : null);
        resp.setScene(SceneEnum.UNKNOWN.name());
        resp.setAnswer("系统处理遇到问题，请稍后重试。");
        resp.setSummary("系统异常");
        resp.setRiskLevel("UNKNOWN");
        resp.setScore(0);
        resp.setRequiresClarification(true);
        resp.setClarificationQuestion("系统处理遇到问题，请稍后重试或联系管理员。");

        return resp;
    }

    /**
     * 保存本轮上传的文件。
     *
     * @param sessionId 会话ID
     * @param req       对话请求
     * @return 保存成功的文件列表
     */
    private List<OssFile> saveUploadedFiles(String sessionId, AgentChatReq req) {
        if (req.getFiles() == null || req.getFiles().length == 0) {
            return List.of();
        }
        try {
            return cosStorageService.saveUploadedFiles(sessionId, req.getFiles());
        } catch (Exception e) {
            log.error("[AgentChatService] 保存上传文件失败，sessionId={}", sessionId, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 将本轮上传文件的 ID 合并到请求的 fileIds 中。
     *
     * @param req          对话请求
     * @param uploadedFiles 本轮上传的文件列表
     */
    private void mergeFileIds(AgentChatReq req, List<OssFile> uploadedFiles) {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            return;
        }
        List<String> uploadedFileIds = uploadedFiles.stream()
                .map(OssFile::getId)
                .collect(Collectors.toList());

        // 合并到请求的 fileIds 中（去重）
        List<String> existingFileIds = req.getFileIds();
        if (existingFileIds == null) {
            req.setFileIds(new ArrayList<>(uploadedFileIds));
        } else {
            for (String fileId : uploadedFileIds) {
                if (!existingFileIds.contains(fileId)) {
                    existingFileIds.add(fileId);
                }
            }
        }
    }

}
