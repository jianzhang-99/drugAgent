package com.liang.drugagent.agent.prompt.shared.constant;

/**
 * 共享 JSON Schema 常量。
 *
 * <p>L0层：定义所有场景共用的 JSON 输出 Schema 模板。</p>
 *
 * @author liangjiajian
 */
public final class SharedJsonSchema {

    private SharedJsonSchema() {
        // 工具类，禁止实例化
    }

    /**
     * JSON 代码块前缀
     */
    public static final String JSON_PREFIX = "```json";

    /**
     * JSON 代码块后缀
     */
    public static final String JSON_SUFFIX = "```";

    /**
     * 统一JSON基础Schema。
     *
     * <p>适用于所有场景的结构化输出，包含：
     * <ul>
     *   <li>scene: 场景标识</li>
     *   <li>timestamp: ISO8601时间戳</li>
     *   <li>thinking: 思维链推理过程</li>
     *   <li>riskLevel: 风险等级</li>
     *   <li>score: 评分</li>
     *   <li>summary: 一句话结论</li>
     *   <li>findings: 发现列表</li>
     *   <li>suggestions: 建议列表</li>
     * </ul>
     */
    public static final String BASE_JSON_SCHEMA = """
            {
              "scene": "STRING, 场景标识",
              "timestamp": "STRING, ISO8601时间戳",
              "thinking": {
                "step1": "STRING, 第一步推理",
                "step2": "STRING, 第二步推理",
                "step3": "STRING, 第三步推理"
              },
              "riskLevel": "STRING, LOW|MEDIUM|HIGH|CRITICAL",
              "score": "INTEGER, 0-100评分",
              "summary": "STRING, 一句话结论",
              "findings": "ARRAY, 发现列表",
              "suggestions": "ARRAY, 建议列表"
            }
            """;

    /**
     * 完整的 JSON 输出模板（包含代码块标记）。
     */
    public static final String BASE_JSON_OUTPUT = JSON_PREFIX + "\n" + BASE_JSON_SCHEMA + "\n" + JSON_SUFFIX;
}
