package com.liang.drugagent.scene.tender_review.orchestrator;

import com.liang.drugagent.controller.domain.AgentChatContext;
import com.liang.drugagent.shared.domain.model.AgentExecutionResult;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.tool.ReviewTenderToolResultMapper;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.scene.tender_review.tool.ReviewTenderTool;
import com.liang.drugagent.tool.dto.ReviewTenderToolReq;
import com.liang.drugagent.scene.tender_review.tool.ReviewTenderToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书审查工具编排器。
 *
 * <p>负责标书场景下的 Tool 调用编排，是标书审查场景的主入口。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>注册 reviewTenderTool 给 LLM</li>
 *   <li>向 LLM 提供工具调用上下文</li>
 *   <li>接受工具执行结果 ReviewTenderToolResult</li>
 *   <li>调用 LLM 整理最终回复</li>
 *   <li>输出统一结果对象 AgentExecutionResult</li>
 * </ul>
 *
 * <p>调用链路：
 * <pre>
 * AgentSceneService -> TenderReviewToolOrchestrator
 *   -> ReviewTenderTool -> TenderReviewWorkflow
 * </pre>
 *
 * @author liangjiajian
 * @see ReviewTenderTool
 * @see AgentExecutionResult
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenderReviewToolOrchestrator {

    private final ReviewTenderTool reviewTenderTool;
    private final ReviewTenderToolResultMapper resultMapper;
    private final LlmService llmService;

    /**
     * 工具名称（与 ReviewTenderTool 一致）。
     */
    public static final String TOOL_NAME = "review_tender";

    /**
     * 执行标书审查编排。
     *
     * <p>完整流程：
     * <ol>
     *   <li>注册 reviewTenderTool 给 LLM</li>
     *   <li>向 LLM 提供工具调用上下文</li>
     *   <li>LLM 决定是否调用工具</li>
     *   <li>接受工具执行结果 ReviewTenderToolResult</li>
     *   <li>调用 LLM 整理最终回复</li>
     *   <li>返回 AgentExecutionResult</li>
     * </ol>
     *
     * @param tenderReviewData 标书审查数据（已由 TenderReviewPreparationService 构建）
     * @param userQuestion     用户问题
     * @param context          Agent 上下文
     * @return Agent 执行结果
     */
    public AgentExecutionResult orchestrate(TenderReviewData tenderReviewData,
                                            String userQuestion,
                                            AgentChatContext context) {
        long startTime = System.currentTimeMillis();
        log.info("开始标书审查编排");

        // 1. 参数校验
        if (tenderReviewData == null || tenderReviewData.getDocuments() == null || tenderReviewData.getDocuments().isEmpty()) {
            log.warn("标书审查数据为空");
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "无标书审查数据，请先上传文件");
        }

        try {
            // 2. 构建工具请求
            ReviewTenderToolReq toolRequest = buildToolRequest(context, tenderReviewData);

            // 3. 执行工具
            ReviewTenderToolResult toolResult = reviewTenderTool.execute(toolRequest, tenderReviewData);

            if (!toolResult.success()) {
                log.warn("工具执行失败: {}", toolResult.message());
                return AgentExecutionResult.builder()
                        .success(false)
                        .errorMessage(toolResult.message())
                        .scene(SceneEnum.TENDER_REVIEW)
                        .build();
            }

            // 4. LLM 润色结果
            String polishedAnswer = polishResult(toolResult, userQuestion);

            // 5. 构建并返回 AgentExecutionResult
            long executionTimeMs = System.currentTimeMillis() - startTime;
            return AgentExecutionResult.builder()
                    .success(true)
                    .scene(SceneEnum.TENDER_REVIEW)
                    .answer(polishedAnswer)
                    .summary(toolResult.summary())
                    .riskLevel(toolResult.riskLevel())
                    .score(toolResult.score())
                    .report(toolResult.report())
                    .evidenceList(toolResult.evidenceList())
                    .caseId(toolResult.caseId())
                    .steps(toolResult.steps())
                    .executionTimeMs(executionTimeMs)
                    .shouldUpdateTitle(true)
                    .build();

        } catch (Exception e) {
            log.error("标书审查编排失败", e);
            return AgentExecutionResult.failure(SceneEnum.TENDER_REVIEW, "标书审查执行失败: " + e.getMessage());
        }
    }

    /**
     * 仅执行工具（不进行 LLM 润色）。
     *
     * <p>适用于调试、测试、或需要自行处理润色的场景。</p>
     *
     * @param tenderReviewData 标书审查数据
     * @param context          Agent 上下文
     * @return 工具原始执行结果
     */
    public ReviewTenderToolResult executeToolOnly(TenderReviewData tenderReviewData, AgentChatContext context) {
        if (tenderReviewData == null) {
            return ReviewTenderToolResult.failure("标书审查数据不能为空");
        }

        ReviewTenderToolReq toolRequest = buildToolRequest(context, tenderReviewData);
        return reviewTenderTool.execute(toolRequest, tenderReviewData);
    }

    /**
     * 构建工具请求。
     */
    private ReviewTenderToolReq buildToolRequest(AgentChatContext context, TenderReviewData tenderReviewData) {
        List<String> fileIds = extractFileIds(tenderReviewData);

        return ReviewTenderToolReq.of(
                context.getSessionId(),
                fileIds,
                extractReviewFocus(context.getQuery()),
                extractUserInstruction(context.getQuery()),
                shouldGenerateReport(context.getQuery())
        );
    }

    /**
     * 从 TenderReviewData 提取文件ID列表。
     */
    private List<String> extractFileIds(TenderReviewData tenderReviewData) {
        if (tenderReviewData.getDocuments() == null) {
            return new ArrayList<>();
        }
        return tenderReviewData.getDocuments().stream()
                .map(doc -> doc.getDocumentId())
                .toList();
    }

    /**
     * 从查询中提取审查重点。
     */
    private String extractReviewFocus(String query) {
        if (query == null) {
            return null;
        }
        if (query.contains("围标") || query.contains("串标")) {
            return "围标风险";
        }
        if (query.contains("技术方案") || query.contains("雷同") || query.contains("抄袭")) {
            return "技术方案雷同";
        }
        if (query.contains("商务条款") || query.contains("商务")) {
            return "商务条款";
        }
        return null;
    }

    /**
     * 从查询中提取用户补充说明。
     */
    private String extractUserInstruction(String query) {
        // 简单实现，实际可解析更复杂
        return null;
    }

    /**
     * 判断是否需要生成完整报告。
     */
    private Boolean shouldGenerateReport(String query) {
        if (query == null) {
            return true;
        }
        return query.contains("完整报告") || query.contains("详细报告") || query.contains("正式报告");
    }

    /**
     * LLM 润色审查结果。
     *
     * <p>将工具执行结果转换为 LLM 可理解的格式，
     * 让 LLM 生成更人性化的最终回复。</p>
     *
     * @param toolResult   工具执行结果
     * @param userQuestion 用户原始问题
     * @return 润色后的回复
     */
    private String polishResult(ReviewTenderToolResult toolResult, String userQuestion) {
        log.info("[TenderReviewToolOrchestrator] 开始用LLM润色审查结果, riskLevel={}",
                toolResult.riskLevel());

        String systemPrompt = buildPolishSystemPrompt(toolResult);
        String userMessage = buildPolishUserMessage(toolResult, userQuestion);

        try {
            LlmResponse llmResponse = llmService.chat(LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(userMessage)
                            .build()))
                    .sessionId(toolResult.caseId())
                    .temperature(0.7f)
                    .build());

            if (Boolean.TRUE.equals(llmResponse.getSuccess())) {
                return llmResponse.getContent();
            } else {
                log.warn("[TenderReviewToolOrchestrator] LLM润色失败，回退到原始结果: {}",
                        llmResponse.getErrorMessage());
                return buildFallbackAnswer(toolResult);
            }
        } catch (Exception e) {
            log.error("[TenderReviewToolOrchestrator] LLM润色异常，回退到原始结果", e);
            return buildFallbackAnswer(toolResult);
        }
    }

    /**
     * 构建润色用的系统提示词。
     */
    private String buildPolishSystemPrompt(ReviewTenderToolResult toolResult) {
        return """
                你是一个专业的标书审查助手，负责将标书审查结果转化为清晰、易懂的回复。

                审查结果概要：
                - 风险等级：%s
                - 风险分数：%d
                - 有效命中规则数：%d

                请根据上述审查结果，用专业但易懂的语言向用户解释：
                1. 标书是否存在风险，如果存在，主要风险点是什么
                2. 建议后续如何处理
                3. 需要人工重点关注的地方

                回复要求：
                - 语言简洁专业，避免过于技术化的术语
                - 如果风险较高，要明确指出并给出建议
                - 如果风险较低，可以适当安抚并说明通过原因
                - 字数控制在 200-500 字之间
                """.formatted(
                toolResult.riskLevel(),
                toolResult.score() != null ? toolResult.score() : 0,
                toolResult.steps() != null ? toolResult.steps().size() : 0
        );
    }

    /**
     * 构建润色用的用户消息。
     */
    private String buildPolishUserMessage(ReviewTenderToolResult toolResult, String userQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户原始问题：").append(userQuestion != null ? userQuestion : "标书审查").append("\n\n");

        if (toolResult.report() != null && toolResult.report().getMarkdownContent() != null) {
            sb.append("详细审查报告：\n").append(toolResult.report().getMarkdownContent());
        } else {
            sb.append("审查摘要：").append(toolResult.summary());
        }

        return sb.toString();
    }

    /**
     * 构建降级回答（LLM 润色失败时使用）。
     */
    private String buildFallbackAnswer(ReviewTenderToolResult toolResult) {
        StringBuilder sb = new StringBuilder();

        if (toolResult.report() != null) {
            return toolResult.report().getMarkdownContent();
        }

        sb.append("## 标书审查结果\n\n");
        sb.append("**风险等级**: ").append(formatRiskLevel(toolResult.riskLevel())).append("\n");
        if (toolResult.score() != null) {
            sb.append("**风险分数**: ").append(toolResult.score()).append("\n\n");
        }
        if (toolResult.summary() != null) {
            sb.append("## 摘要\n\n").append(toolResult.summary()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化风险等级显示。
     */
    private String formatRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "未知";
        }
        return switch (riskLevel.toUpperCase()) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            case "NONE" -> "无风险";
            default -> riskLevel;
        };
    }
}
