package com.liang.drugagent.scene.tender_review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.prompt.tender_review.judge.TenderReviewJudgePrompt;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticEvidence;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeReq;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeResp;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * 语义裁决服务。
 * 负责组织 prompt、调用 LLM、解析结构化 JSON 结果，并处理超时降级。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据规则编码构建带 JSON Schema 说明的 prompt</li>
 *   <li>调用 {@link LlmService} 获取 LLM 判断结果</li>
 *   <li>解析 LLM 返回的 JSON 为 {@link TenderSemanticJudgeResp}</li>
 *   <li>超时或异常时返回默认低置信度结果（hit=false）</li>
 * </ul>
 *
 * @author architect
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderSemanticReviewService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ExecutorService llmCallExecutor;

    private static final int LLM_TIMEOUT_SECONDS = 60;

    /**
     * 语义裁决入口。
     *
     * @param req 语义裁决请求
     * @return 语义裁决响应
     */
    public TenderSemanticJudgeResp judge(TenderSemanticJudgeReq req) {
        String prompt = buildPrompt(req);
        try {
            // 第一次调用
            LlmResponse response = callLlmWithTimeout(prompt, req.getCaseId());
            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                log.warn("[TenderSemanticReviewService] LLM 调用失败或超时，降级返回低置信度结果 - caseId: {}, ruleCode: {}",
                        req.getCaseId(), req.getRuleCode());
                return buildDefaultDegradedResp(req.getRuleCode());
            }

            // 尝试解析 JSON
            TenderSemanticJudgeResp resp = parseLlmResponse(response.getContent(), req.getRuleCode());
            // 解析成功且非降级结果，直接返回
            if (resp != null && !isDegradedResponse(resp)) {
                return resp;
            }

            // JSON 解析失败或降级，进行一次重试（温度降为 0，prompt 更严格）
            log.warn("[TenderSemanticReviewService] 首次 JSON 解析失败或低置信度，进行一次重试 - caseId: {}, ruleCode: {}, confidence: {}",
                    req.getCaseId(), req.getRuleCode(), resp != null ? resp.getConfidence() : "null");
            LlmResponse retryResponse = callLlmWithRetry(prompt, req.getCaseId(), true);
            if (retryResponse == null || !Boolean.TRUE.equals(retryResponse.getSuccess())) {
                log.warn("[TenderSemanticReviewService] 重试失败，降级返回 - caseId: {}, ruleCode: {}",
                        req.getCaseId(), req.getRuleCode());
                return buildDefaultDegradedResp(req.getRuleCode());
            }
            TenderSemanticJudgeResp retryResp = parseLlmResponse(retryResponse.getContent(), req.getRuleCode());
            return retryResp != null ? retryResp : buildDefaultDegradedResp(req.getRuleCode());
        } catch (Exception e) {
            log.error("[TenderSemanticReviewService] 语义裁决异常，降级返回 - caseId: {}, ruleCode: {}, error: {}",
                    req.getCaseId(), req.getRuleCode(), e.getMessage());
            return buildDefaultDegradedResp(req.getRuleCode());
        }
    }

    /**
     * 判断是否为降级响应（置信度为 0.3 的默认降级结果）。
     */
    private boolean isDegradedResponse(TenderSemanticJudgeResp resp) {
        return resp.getConfidence() != null && resp.getConfidence() <= 0.3;
    }

    /**
     * 带重试的 LLM 调用（JSON 解析失败时触发）。
     */
    private LlmResponse callLlmWithRetry(String prompt, String caseId, boolean isRetry) {
        try {
            Future<LlmResponse> future = llmCallExecutor.submit(() -> {
                LlmRequest request = LlmRequest.builder()
                        // 重试时使用更严格的 system prompt
                        .systemPrompt(isRetry
                                ? "【强制】你必须只输出 ```json ... ``` 代码块内的纯JSON对象，不允许输出任何其他文字。前缀、后缀、解释说明一律禁止。违反将导致系统错误。"
                                : "你是标书审查的语义裁判。你的唯一任务是分析给定内容并输出JSON。输出要求：1) 只输出 ```json ... ``` 代码块内的纯JSON对象；2) 禁止在JSON之前或之后输出任何解释、说明、分析文字；3) 禁止输出任何非JSON内容。违反上述要求将导致系统错误。")
                        .messages(List.of(LlmRequest.ChatMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()))
                        .temperature(isRetry ? 0.0f : 0.1f)
                        .maxTokens(2048)
                        .sessionId(caseId)
                        .build();
                return llmService.chat(request);
            });
            return future.get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[TenderSemanticReviewService] LLM 调用超时 - caseId: {}, timeout: {}s, isRetry: {}", caseId, LLM_TIMEOUT_SECONDS, isRetry);
            return null;
        } catch (Exception e) {
            log.error("[TenderSemanticReviewService] LLM 调用异常 - caseId: {}, error: {}, isRetry: {}", caseId, e.getMessage(), isRetry);
            throw new RuntimeException(e);
        }
    }

    /**
     * 构建 prompt，包含 JSON Schema 说明和 Few-Shot 示例。
     * <p>
     * 通过完整示例引导模型严格遵循 JSON 格式输出，这是目前最有效的
     * 解决 MiniMax 不支持 response_format 强制 JSON 的方案。
     */
    private String buildPrompt(TenderSemanticJudgeReq req) {
        // 拼接左侧候选片段
        StringBuilder leftSnippetsSb = new StringBuilder();
        for (int i = 0; i < req.getLeftSnippets().size(); i++) {
            leftSnippetsSb.append("片段").append(i + 1).append(": ").append(req.getLeftSnippets().get(i)).append("\n");
        }

        // 拼接右侧候选片段
        StringBuilder rightSnippetsSb = new StringBuilder();
        for (int i = 0; i < req.getRightSnippets().size(); i++) {
            rightSnippetsSb.append("片段").append(i + 1).append(": ").append(req.getRightSnippets().get(i)).append("\n");
        }

        return String.format(
                TenderReviewJudgePrompt.SEMANTIC_JUDGE_USER_PROMPT,
                req.getRuleCode(),
                getRuleDescription(req.getRuleCode()),
                req.getCompareTopic(),
                req.getLeftDocumentId(),
                leftSnippetsSb.toString(),
                req.getRightDocumentId(),
                rightSnippetsSb.toString()
        );
    }

    /**
     * 获取规则描述。
     */
    private String getRuleDescription(String ruleCode) {
        return switch (ruleCode) {
            case "W-P1" -> "技术方案抄袭：判断是否属于技术方案的实质同源改写，如共享相同的系统架构骨架、模块划分、业务闭环逻辑。仅行业通用术语不得判定命中。";
            case "W-P4" -> "风险识别抄袭：判断风险项拆解逻辑、风险影响链条、应对措施是否高度同源。轻度改写（如同义替换、句式重写）应判定为同源。";
            case "W-M8" -> "商务条款配合：判断是否存在\"一方完全接受、一方附条件接受\"的互补配合模式，或\"一个强响应、一个柔性偏离\"的协同策略。";
            case "W-P2" -> "实施方法抄袭：判断阶段名称不同但流程骨架是否一致，关键里程碑、交付顺序、组织方式是否同源。";
            case "W-P3" -> "服务承诺抄袭：判断服务等级、时效组合、承诺逻辑是否高度同源，表达不同但服务体系配置基本一致应判定为同源。";
            case "W-M3" -> "核心团队重叠：辅助判断同一人不同岗位包装、简历表达改写但履历骨架一致、团队构成关系相似的情况。";
            default -> "未知规则，请根据语义自行判断。";
        };
    }

    /**
     * 带超时的 LLM 调用。
     */
    private LlmResponse callLlmWithTimeout(String prompt, String caseId) {
        try {
            Future<LlmResponse> future = llmCallExecutor.submit(() -> {
                LlmRequest request = LlmRequest.builder()
                        .systemPrompt(TenderReviewJudgePrompt.SEMANTIC_JUDGE_PROMPT)
                        .messages(List.of(LlmRequest.ChatMessage.builder()
                                .role("user")
                                .content(prompt)
                                .build()))
                        .temperature(0.1f)
                        .maxTokens(2048)
                        .sessionId(caseId)
                        .build();
                return llmService.chat(request);
            });
            return future.get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[TenderSemanticReviewService] LLM 调用超时 - caseId: {}, timeout: {}s", caseId, LLM_TIMEOUT_SECONDS);
            return null;
        } catch (Exception e) {
            log.error("[TenderSemanticReviewService] LLM 调用异常 - caseId: {}, error: {}", caseId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析 LLM 返回的 JSON 响应。
     */
    private TenderSemanticJudgeResp parseLlmResponse(String content, String ruleCode) {
        if (content == null || content.isBlank()) {
            log.warn("[TenderSemanticReviewService] LLM 返回内容为空，降级返回 - ruleCode: {}", ruleCode);
            return buildDefaultDegradedResp(ruleCode);
        }
        try {
            // 尝试提取 JSON（处理可能存在的前后缀）
            String jsonContent = extractJson(content);
            if (jsonContent == null) {
                log.error("[TenderSemanticReviewService] 未找到 JSON 对象，降级返回 - ruleCode: {}, content: {}", ruleCode, content);
                return buildDefaultDegradedResp(ruleCode);
            }
            TenderSemanticJudgeResp resp = objectMapper.readValue(jsonContent, TenderSemanticJudgeResp.class);
            if (resp.getRuleCode() == null) {
                resp.setRuleCode(ruleCode);
            }
            return resp;
        } catch (JsonProcessingException e) {
            log.error("[TenderSemanticReviewService] JSON 解析失败，降级返回 - ruleCode: {}, content: {}", ruleCode, content);
            return buildDefaultDegradedResp(ruleCode);
        }
    }

    /**
     * 从 LLM 返回内容中提取 JSON 对象。
     * 支持：code block 包裹的 JSON、或纯 JSON、或 JSON 混在文本中。
     */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        content = content.trim();

        // 1. 尝试从 ```json ... ``` 代码块中提取
        int codeBlockStart = content.indexOf("```json");
        if (codeBlockStart >= 0) {
            int jsonStart = content.indexOf("{", codeBlockStart);
            int jsonEnd = content.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return content.substring(jsonStart, jsonEnd + 1);
            }
        }

        // 2. 尝试从 ``` ... ``` 普通代码块中提取
        int genericBlockStart = content.indexOf("```");
        if (genericBlockStart >= 0) {
            int afterBlock = genericBlockStart + 3;
            int blockEnd = content.indexOf("```", afterBlock);
            if (blockEnd > afterBlock) {
                String blockContent = content.substring(afterBlock, blockEnd).trim();
                if (blockContent.startsWith("{")) {
                    int js = 0;
                    int je = blockContent.lastIndexOf("}");
                    if (je > 0) {
                        return blockContent.substring(js, je + 1);
                    }
                }
            }
        }

        // 3. 查找第一个 { 和最后一个 }
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 构建默认降级响应。
     * 超时或异常时返回低置信度的非命中结果。
     */
    private TenderSemanticJudgeResp buildDefaultDegradedResp(String ruleCode) {
        return TenderSemanticJudgeResp.builder()
                .hit(false)
                .ruleCode(ruleCode)
                .confidence(0.3)
                .suggestedWeight(0)
                .conclusion("LLM 调用超时/异常，降级返回")
                .reason("因 LLM 服务不可用，无法进行语义判断，返回低置信度不命中结果")
                .evidences(new ArrayList<>())
                .cautionNotes(List.of("本次判断未能执行语义分析，建议人工复核"))
                .build();
    }
}
