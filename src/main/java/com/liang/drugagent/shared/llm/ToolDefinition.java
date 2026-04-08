package com.liang.drugagent.shared.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具定义（Function Calling）。
 *
 * <p>用于向 LLM 描述可用工具的结构化 schema，
 * 支持百炼平台的 function_call 协议。</p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /**
     * 工具类型，当前固定为 function
     */
    @Builder.Default
    private String type = "function";

    /**
     * 函数定义
     */
    private Function function;

    /**
     * 函数定义内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {

        /**
         * 工具名称，模型根据此名称选择调用
         */
        private String name;

        /**
         * 工具描述，帮助模型理解何时应该调用
         */
        private String description;

        /**
         * 参数 JSON Schema，定义工具入参的结构
         */
        private Map<String, Object> parameters;
    }

    /**
     * 构建一个简单的工具定义（无复杂参数）
     *
     * @param name        工具名
     * @param description 工具描述
     * @return ToolDefinition
     */
    public static ToolDefinition simple(String name, String description) {
        return ToolDefinition.builder()
                .type("function")
                .function(Function.builder()
                        .name(name)
                        .description(description)
                        .parameters(Map.of(
                                "type", "object",
                                "properties", Map.of(),
                                "required", new String[]{}
                        ))
                        .build())
                .build();
    }
}
