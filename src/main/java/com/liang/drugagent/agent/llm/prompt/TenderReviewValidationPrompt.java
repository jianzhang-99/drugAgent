package com.liang.drugagent.agent.llm.prompt;

/**
 * 标书审查输出校验 Prompt 模板。
 *
 * <p>负责检查 TenderReviewResult 的完整性和合规性，
 * 生成规范化的 summary 和 answer，输出 warnings。</p>
 *
 * @author liangjiajian
 */
public class TenderReviewValidationPrompt {

    /**
     * 标书审查输出校验 System Prompt。
     *
     * <p>职责：
     * 1. 检查结果是否完整
     * 2. 检查表达是否符合输出规范
     * 3. 检查是否和结构化结果冲突
     * 4. 生成最终展示用 summary / answer
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
            6. 识别并报告潜在问题（warnings）

            # 合规性检查要点
            - 禁止在 answer 中直接认定"这是围标/串标"
            - 禁止给出最终处罚结论
            - 禁止将法规条文引用判定为风险
            - 描述应使用"疑似"、"建议复核"等中性词汇
            - 证据应与结论对应，避免无证据支撑的推断

            # 输出格式要求
            必须返回纯 JSON 格式：
            {
              "passed": true或false,
              "summary": "一句话结论",
              "answer": "用户可读的审查结论（Markdown格式）",
              "warnings": ["问题1", "问题2"] // 如果有警告则列出，无则为空数组
            }

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
    public static String getJsonSchema() {
        return """
                {
                  "passed": "BOOLEAN, 校验是否通过",
                  "summary": "STRING, 一句话结论",
                  "answer": "STRING, 用户可读的审查结论（Markdown格式）",
                  "warnings": "ARRAY, 问题警告列表，无则为空数组"
                }
                """;
    }
}
