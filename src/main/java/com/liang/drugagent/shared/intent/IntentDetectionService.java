package com.liang.drugagent.shared.intent;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.llm.DashScopeLlmClient;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图检测服务 - 使用阿里云百炼 tongyi-intent-detect-v3 模型。
 *
 * <p>专门用于判断用户query所属的业务场景，作为规则匹配的补充。
 * 当规则匹配无法确定场景时，调用此服务做LLM兜底判断。
 *
 * <p>支持的场景：
 * <ul>
 *   <li>TENDER_REVIEW - 标书审查</li>
 *   <li>CONTRACT_PRECHECK - 合同预审</li>
 *   <li>RISK_ALERT - 风险预警</li>
 *   <li>DEFAULT - 通用对话</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentDetectionService {

    private final DashScopeLlmClient dashScopeLlmClient;

    /**
     * 意图检测模型名称
     */
    @Value("${aliyun.dashscope.intent-model:tongyi-intent-detect-v3}")
    private String intentModel;

    /**
     * 检测用户query的意图场景。
     *
     * @param query 用户输入的query
     * @return 识别到的场景枚举
     */
    public SceneEnum detectIntent(String query) {
        if (query == null || query.isBlank()) {
            return SceneEnum.DEFAULT;
        }

        try {
            log.info("[IntentDetectionService] 开始意图检测 - model={}, query={}", intentModel, truncateQuery(query));

            LlmRequest request = LlmRequest.builder()
                    .provider(com.liang.drugagent.shared.llm.LlmProviderType.DASHSCOPE)
                    .model(intentModel)
                    .systemPrompt(buildIntentSystemPrompt())
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(query)
                            .build()))
                    .build();

            LlmResponse response = dashScopeLlmClient.chat(request);

            if (Boolean.TRUE.equals(response.getSuccess()) && response.getContent() != null) {
                SceneEnum scene = parseIntentResponse(response.getContent());
                log.info("[IntentDetectionService] 意图检测完成 - query={}, detected={}", truncateQuery(query), scene);
                return scene;
            }

            log.warn("[IntentDetectionService] 意图检测返回失败，使用默认场景 - error={}", response.getErrorMessage());
            return SceneEnum.DEFAULT;

        } catch (Exception e) {
            log.error("[IntentDetectionService] 意图检测异常 - query={}, error={}", truncateQuery(query), e.getMessage());
            return SceneEnum.DEFAULT;
        }
    }

    /**
     * 构建意图检测的system prompt。
     */
    private String buildIntentSystemPrompt() {
        return "你是一个意图分类专家。你的任务是根据用户输入判断用户想要进行的业务操作。\n\n" +
                "请从以下场景中选择一个最匹配的：\n" +
                "1. TENDER_REVIEW（标书审查）- 用户想要审查标书、检测围标串标风险、比对标书内容等\n" +
                "2. CONTRACT_PRECHECK（合同预审）- 用户想要审查合同条款、检测合同风险、核对合同合规性等\n" +
                "3. RISK_ALERT（风险预警）- 用户想要进行风险识别、获取风险提示、查看合规风险等\n" +
                "4. DEFAULT（通用对话）- 用户想要进行普通问答、知识查询、闲聊等不属于上述业务场景的请求\n\n" +
                "注意：\n" +
                "- 如果用户提到了\"标书\"、\"围标\"、\"串标\"、\"投标文件\"、\"竞标\"等关键词，优先考虑TENDER_REVIEW\n" +
                "- 如果用户提到了\"合同\"、\"条款\"、\"合同风险\"等关键词，优先考虑CONTRACT_PRECHECK\n" +
                "- 如果用户提到了\"风险\"、\"预警\"、\"合规\"、\"违规\"等关键词，优先考虑RISK_ALERT\n" +
                "- 如果只是普通问答、问候、知识查询，选择DEFAULT\n\n" +
                "请直接输出场景名称，不要输出其他内容。";
    }

    /**
     * 解析LLM返回的意图结果。
     */
    private SceneEnum parseIntentResponse(String content) {
        if (content == null || content.isBlank()) {
            return SceneEnum.DEFAULT;
        }

        String trimmed = content.trim().toUpperCase();

        if (trimmed.contains("TENDER_REVIEW") || trimmed.contains("标书审查")) {
            return SceneEnum.TENDER_REVIEW;
        }
        if (trimmed.contains("CONTRACT_PRECHECK") || trimmed.contains("合同预审")) {
            return SceneEnum.CONTRACT_PRECHECK;
        }
        if (trimmed.contains("RISK_ALERT") || trimmed.contains("风险预警")) {
            return SceneEnum.RISK_ALERT;
        }

        return SceneEnum.DEFAULT;
    }

    /**
     * 截断query用于日志展示。
     */
    private String truncateQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.length() > 50 ? query.substring(0, 50) + "..." : query;
    }
}
