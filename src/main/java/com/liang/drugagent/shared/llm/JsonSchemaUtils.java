package com.liang.drugagent.shared.llm;

import java.util.List;
import java.util.Map;

/**
 * JSON Schema 工具类。
 *
 * <p>用于构建百炼 API 所需的 JSON Schema 定义，
 * 支持严格结构化输出场景。</p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
public final class JsonSchemaUtils {

    private JsonSchemaUtils() {
        // 工具类
    }

    /**
     * 构建简单的 JSON Object Schema（无复杂结构）。
     */
    public static Map<String, Object> jsonObjectSchema() {
        return Map.of("type", "object");
    }

    /**
     * 构建带参数的 JSON Schema。
     *
     * @param schemaName  schema 名称
     * @param description schema 描述
     * @param properties  属性定义
     * @param required    必填字段列表
     */
    public static Map<String, Object> buildSchema(String schemaName, String description,
                                                   Map<String, Map<String, Object>> properties,
                                                   List<String> required) {
        return Map.of(
                "type", "object",
                "properties", buildProperties(properties),
                "required", required != null ? required : List.of(),
                "additionalProperties", false
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildProperties(Map<String, Map<String, Object>> properties) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        for (var entry : properties.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 构建字符串属性定义。
     */
    public static Map<String, Object> stringProperty(String description) {
        return Map.of(
                "type", "string",
                "description", description
        );
    }

    /**
     * 构建字符串属性定义（带枚举值）。
     */
    public static Map<String, Object> stringProperty(String description, List<String> enumValues) {
        return Map.of(
                "type", "string",
                "description", description,
                "enum", enumValues
        );
    }

    /**
     * 构建整数属性定义。
     */
    public static Map<String, Object> integerProperty(String description) {
        return Map.of(
                "type", "integer",
                "description", description
        );
    }

    /**
     * 构建布尔属性定义。
     */
    public static Map<String, Object> booleanProperty(String description) {
        return Map.of(
                "type", "boolean",
                "description", description
        );
    }

    /**
     * 构建数组属性定义。
     */
    public static Map<String, Object> arrayProperty(String description, Map<String, Object> items) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", items
        );
    }

    /**
     * 构建对象属性定义。
     */
    public static Map<String, Object> objectProperty(String description, Map<String, Map<String, Object>> properties) {
        return Map.of(
                "type", "object",
                "description", description,
                "properties", buildProperties(properties)
        );
    }

    // ==================== 业务场景 Schema ====================

    /**
     * 审查结果 Schema。
     * 用于标书审查、合同预审等场景的结构化输出。
     */
    public static Map<String, Object> reviewResultSchema() {
        return buildSchema("ReviewResult", "审查结果",
                Map.of(
                        "riskLevel", stringProperty("风险等级：high/medium/low"),
                        "score", integerProperty("风险分，0-100"),
                        "summary", stringProperty("审查摘要"),
                        "mainRisks", arrayProperty("主要风险列表",
                                Map.of("type", "string", "description", "风险项描述")),
                        "evidenceCount", integerProperty("证据数量")
                ),
                List.of("riskLevel", "summary")
        );
    }

    /**
     * 问答结果 Schema。
     * 用于通用问答场景的结构化输出。
     */
    public static Map<String, Object> qaResultSchema() {
        return buildSchema("QAResult", "问答结果",
                Map.of(
                        "answer", stringProperty("回答内容"),
                        "confidence", stringProperty("置信度：high/medium/low"),
                        "source", stringProperty("答案来源")
                ),
                List.of("answer")
        );
    }

    /**
     * 风险项 Schema。
     * 用于风险识别场景的结构化输出。
     */
    public static Map<String, Object> riskItemSchema() {
        return buildSchema("RiskItem", "风险项",
                Map.of(
                        "name", stringProperty("风险名称"),
                        "level", stringProperty("风险等级：high/medium/low"),
                        "description", stringProperty("风险描述"),
                        "mitigation", stringProperty("缓解措施")
                ),
                List.of("name", "level")
        );
    }
}
