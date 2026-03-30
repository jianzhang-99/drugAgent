package com.liang.drugagent.agent.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.model.AgentExecutionResult;
import com.liang.drugagent.shared.model.WorkflowRouteDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 输出校验服务。
 *
 * <p>在 workflow 返回结果之后、适配为前端响应之前，调用 LLM 做输出规范校验。
 *
 * <p>职责：
 * <ul>
 *   <li>检查结果是否完整</li>
 *   <li>检查表达是否符合输出规范</li>
 *   <li>检查是否和结构化结果冲突</li>
 *   <li>生成最终展示用 summary / answer</li>
 * </ul>
 *
 * <p>不负责：
 * <ul>
 *   <li>重新推理业务结论</li>
 *   <li>捏造 workflow 未产出的事实</li>
 *   <li>修改证据和评分</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputValidationService {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 输出校验结果。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResult {
        private boolean passed;
        private String summary;
        private String answer;
        private java.util.List<String> warnings;
    }

    /**
     * 对 workflow 执行结果进行输出校验。
     *
     * <p>如果校验发现问题：
     * <ul>
     *   <li>轻问题：允许生成规范化展示文案，并带 warning</li>
     *   <li>重问题：返回 passed=false，由调用方走降级</li>
     * </ul>
     *
     * @param scene           场景类型
     * @param routeReason     路由原因
     * @param executionResult workflow 执行结果
     * @return 校验结果
     */
    public ValidationResult validate(SceneEnum scene, String routeReason, AgentExecutionResult executionResult) {
        log.info("[OutputValidationService] 开始输出校验: scene={}", scene);

        if (executionResult == null) {
            return ValidationResult.builder()
                    .passed(false)
                    .summary("执行结果为空")
                    .answer("系统处理异常，未获取到有效结果")
                    .warnings(java.util.List.of("workflow 执行结果为空"))
                    .build();
        }

        // 如果执行失败，直接返回失败信息，不做校验
        if (!executionResult.isSuccess()) {
            return ValidationResult.builder()
                    .passed(true)
                    .summary(executionResult.getSummary())
                    .answer(executionResult.getAnswer())
                    .warnings(java.util.List.of("执行失败: " + executionResult.getErrorMessage()))
                    .build();
        }

        try {
            // 调用 LLM 做校验
            String prompt = buildValidationPrompt(scene, routeReason, executionResult);
            String systemPrompt = "你是一个专业的医疗监管合规审查助手，负责对AI输出的结果进行规范校验。";

            String llmResponse = llmService.chat(prompt, systemPrompt, "output-validation");

            // 解析 LLM 响应
            return parseValidationResponse(llmResponse, executionResult);

        } catch (Exception e) {
            log.warn("[OutputValidationService] 输出校验异常，使用原始结果: {}", e.getMessage());
            // 校验失败时，使用原始结果但标记有警告
            return ValidationResult.builder()
                    .passed(true)
                    .summary(executionResult.getSummary())
                    .answer(executionResult.getAnswer())
                    .warnings(java.util.List.of("输出校验异常: " + e.getMessage() + "，使用原始结果"))
                    .build();
        }
    }

    /**
     * 构建校验 prompt。
     */
    private String buildValidationPrompt(SceneEnum scene, String routeReason, AgentExecutionResult result) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的医疗监管合规审查助手，请对以下AI输出结果进行规范校验。\n\n");

        prompt.append("【场景】: ").append(scene != null ? scene.name() : "UNKNOWN").append("\n");
        prompt.append("【路由原因】: ").append(routeReason != null ? routeReason : "未知").append("\n\n");

        prompt.append("【原始输出】:\n");
        prompt.append("- answer: ").append(result.getAnswer() != null ? result.getAnswer() : "").append("\n");
        prompt.append("- summary: ").append(result.getSummary() != null ? result.getSummary() : "").append("\n");
        prompt.append("- riskLevel: ").append(result.getRiskLevel() != null ? result.getRiskLevel() : "").append("\n");
        prompt.append("- score: ").append(result.getScore() != null ? result.getScore() : "无").append("\n");
        prompt.append("- evidenceList大小: ").append(result.getEvidenceList() != null ? result.getEvidenceList().size() : 0).append("\n");
        prompt.append("- steps: ").append(result.getSteps() != null ? result.getSteps() : java.util.List.of()).append("\n");

        if (result.getReport() != null) {
            try {
                prompt.append("- report: ").append(objectMapper.writeValueAsString(result.getReport())).append("\n");
            } catch (Exception e) {
                prompt.append("- report: [无法序列化]").append("\n");
            }
        }

        prompt.append("\n【校验要求】:\n");
        prompt.append("1. 检查 answer 是否完整、表达是否合规\n");
        prompt.append("2. 检查是否和结构化结果（riskLevel、score、evidenceList）冲突\n");
        prompt.append("3. 检查是否捏造了 workflow 未产出的事实\n");
        prompt.append("4. 不可重新推理业务结论或修改证据评分\n\n");

        prompt.append("【输出格式】(只输出JSON，不要其他内容):\n");
        prompt.append("{\n");
        prompt.append("  \"passed\": true或false,\n");
        prompt.append("  \"summary\": \"校验后的摘要（如需修改）\",\n");
        prompt.append("  \"answer\": \"校验后的回答（如需修改）\",\n");
        prompt.append("  \"warnings\": [\"警告1\", \"警告2\"]\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 解析校验响应。
     */
    private ValidationResult parseValidationResponse(String llmResponse, AgentExecutionResult original) {
        try {
            // 尝试解析 LLM 返回的 JSON
            var node = objectMapper.readTree(llmResponse);
            boolean passed = node.has("passed") && node.get("passed").asBoolean();
            String summary = node.has("summary") ? node.get("summary").asText() : original.getSummary();
            String answer = node.has("answer") ? node.get("answer").asText() : original.getAnswer();

            java.util.List<String> warnings = new java.util.ArrayList<>();
            if (node.has("warnings") && node.get("warnings").isArray()) {
                node.get("warnings").forEach(w -> warnings.add(w.asText()));
            }

            log.info("[OutputValidationService] 校验完成: passed={}, warningsCount={}", passed, warnings.size());

            return ValidationResult.builder()
                    .passed(passed)
                    .summary(summary)
                    .answer(answer)
                    .warnings(warnings)
                    .build();

        } catch (Exception e) {
            log.warn("[OutputValidationService] 解析校验响应失败，使用原始结果: {}", e.getMessage());
            // 解析失败时，使用原始结果
            return ValidationResult.builder()
                    .passed(true)
                    .summary(original.getSummary())
                    .answer(original.getAnswer())
                    .warnings(java.util.List.of("校验响应解析失败，使用原始结果"))
                    .build();
        }
    }
}
