package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.agent.common.entity.OssFile;
import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.cos.TencentCosStorageService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

            // 3. 读取最近消息和摘要，构建上下文
            List<ChatMessage> recentMessages = agentMessageService.getRecentMessages(sessionId, 20);
            AgentChatContext context = AgentChatContext.from(req, sessionId);
            context.setSession(session);
            context.setHistoryMessages(recentMessages);
            context.setRecentSummary(session.getSummary());
            context.setUploadedFiles(uploadedFiles);

            // 合并 fileIds：将本轮上传文件的 ID 也加入
            mergeFileIds(req, uploadedFiles);

            log.debug("[AgentChatService] 构建执行上下文: sessionId={}, traceId={}, historyCount={}, uploadedFilesCount={}",
                    sessionId, context.getTraceId(), recentMessages.size(), uploadedFiles.size());

            // 3. 调用 AgentSceneService 执行场景判断与分发
            AgentSceneService.AgentSceneExecution execution = agentSceneService.decideAndExecute(context, req);

            // 4. 如需要澄清，直接返回澄清响应
            if (execution.isNeedsClarification()) {
                return buildClarificationResp(context, execution.getDecision(), execution.getClarificationQuestion());
            }

            AgentExecutionResult executionResult = execution.getExecutionResult();

            // 5. 保存用户消息
            agentMessageService.saveUserMessage(sessionId, req.getQuery(), null);

            // 6. 保存助手消息
            String assistantContent = executionResult.getAnswer() != null ? executionResult.getAnswer() : executionResult.getSummary();
            String messageType = executionResult.isNeedsFallback() ? "assistant_clarify" : "assistant_text";
            agentMessageService.saveAssistantMessage(sessionId, assistantContent, null, messageType);

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
