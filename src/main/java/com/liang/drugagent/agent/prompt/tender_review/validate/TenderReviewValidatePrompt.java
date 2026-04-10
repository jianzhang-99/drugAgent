package com.liang.drugagent.agent.prompt.tender_review.validate;

import com.liang.drugagent.shared.llm.JsonSchemaUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标书审查结果校验 Prompt。
 *
 * <p>L4层：结果校验 Prompt。
 * 职责：校验 Workflow 或场景执行结果是否完整、合规、可展示。</p>
 *
 * <p>负责：
 * <ul>
 *   <li>检查必要字段是否缺失</li>
 *   <li>检查文本与结构化结果是否冲突</li>
 *   <li>检查是否存在越权表述</li>
 *   <li>生成最终展示所需的 summary / warnings</li>
 * </ul>
 * </p>
 *
 * <p>不负责：重新判案、推翻 Workflow 的结构化结论、编造缺失证据。</p>
 *
 * @author liangjiajian
 */
public class TenderReviewValidatePrompt {

    /**
     * 标书审查输出校验 System Prompt。
     *
     * <p>职责：
     * 1. 检查关键字段是否缺失（caseId, summary, riskLevel, score, report, evidenceList）
     * 2. 检查 answer 是否包含不合规内容（如直接认定违规、给出处罚结论等）
     * 3. 检查结构化数据与文本描述是否一致
     * 4. 生成规范的 summary（一句话结论）
     * 5. 生成规范的 answer（用户可读的审查结论）
     * 6. 识别并报告潜在问题（warnings）
     *
     * <p>不负责：
     * 1. 重新推理业务结论
     * 2. 捏造 workflow 未产出的事实
     * 3. 修改证据和评分</p>
     */
    public static final String SYSTEM_PROMPT = """
            # 角色定义
            你是一个专业的标书审查输出校验助手。你的任务是检查标书审查结果的完整性和合规性，
            并生成规范化的用户可读回复。

            # 校验职责
            1. 检查关键字段是否缺失（caseId, summary, riskLevel, score, report, evidenceList）
            2. 检查 answer 是否包含不合规内容（如直接认定违规、给出处罚结论等）
            3. 检查结构化数据与文本描述是否一致
            4. 生成规范的 summary（一句话结论）
            5. 生成规范的 answer（用户可读的审查结论）
            6. 生成适合页面直接展示的管理摘要与建议动作
            7. 识别并报告潜在问题（warnings）

            # 合规性检查要点
            - 禁止在 answer 中直接认定"这是围标/串标"
            - 禁止给出最终处罚结论
            - 禁止将法规条文引用判定为风险
            - 描述应使用"疑似"、"建议复核"等中性词汇
            - 证据应与结论对应，避免无证据支撑的推断

            # 输出格式要求
            - 必须返回纯 JSON 格式
            - 必须严格遵守调用方提供的 JSON Schema
            - 不能输出 markdown 代码块包裹
            - managementSummary 为 2~4 条面向管理者的摘要
            - suggestedActions 为 3~5 条可执行建议

            # 评分与风险等级标准
            - riskLevel 取值：HIGH | MEDIUM | LOW
            - score 范围：0-100，分数越低风险越高
            - 相似度 >= 85%：HIGH
            - 相似度 70-85%：MEDIUM
            - 相似度 < 70%：LOW

            # 生成 answer 的规范
            1. 第一部分：简要结论（1-2句话说明审查结果）
            2. 第二部分：风险点列表（如有）
            3. 第三部分：复核建议
            4. 使用 Markdown 格式，便于前端展示

            # 注意事项
            - 只输出 JSON，不要输出 markdown 代码块标记
            - 如果 passed 为 false，answer 应说明问题而非直接展示
            - warnings 数组应为空 [] 而不是 null
            - 必须在 answer 中包含 json 结构对应的结论，不要遗漏关键信息
            """;

    /**
     * 构建用户消息（包含 workflow 结果）。
     *
     * @param scene         场景标识
     * @param routeReason   路由原因
     * @param workflowResult workflow 原始结果（JSON 字符串）
     * @return 构造的用户消息
     */
    public static String buildUserMessage(String scene, String routeReason, String workflowResult) {
        return String.format("""
                请校验以下标书审查结果：

                【场景】%s
                【路由原因】%s

                【Workflow 原始结果】
                %s

                请返回校验结果（JSON格式）。
                """, scene, routeReason, workflowResult);
    }

    /**
     * 获取校验输出的 JSON Schema（用于结构化输出）。
     */
    public static Map<String, Object> getJsonSchema() {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        properties.put("passed", JsonSchemaUtils.booleanProperty("校验是否通过"));
        properties.put("summary", JsonSchemaUtils.stringProperty("一句话结论"));
        properties.put("answer", JsonSchemaUtils.stringProperty("用户可读的审查结论，Markdown 格式"));
        properties.put("managementSummary", JsonSchemaUtils.arrayProperty(
                "面向管理者的摘要列表，2到4条",
                Map.of("type", "string", "description", "单条管理摘要")
        ));
        properties.put("suggestedActions", JsonSchemaUtils.arrayProperty(
                "后续建议动作列表，3到5条",
                Map.of("type", "string", "description", "单条建议动作")
        ));
        properties.put("warnings", JsonSchemaUtils.arrayProperty(
                "问题警告列表，无则为空数组",
                Map.of("type", "string", "description", "单条警告")
        ));
        return JsonSchemaUtils.buildSchema(
                "TenderReviewValidationResult",
                "标书审查最终输出校验结果",
                properties,
                List.of("passed", "summary", "answer", "managementSummary", "suggestedActions", "warnings")
        );
    }

    /**
     * 获取百炼 response_format 所需的完整 json_schema 配置。
     */
    public static Map<String, Object> getResponseFormat() {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "tender_review_validation_result",
                        "strict", true,
                        "schema", getJsonSchema()
                )
        );
    }
}
