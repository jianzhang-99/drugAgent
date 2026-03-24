package com.liang.drugagent.tool.model;

/**
 * 工具结果。
 *
 * @author liangjiajian
 */
public record ToolResult<T>(boolean success, T data, String message) {

    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(true, data, null);
    }

    public static <T> ToolResult<T> failure(String message) {
        return new ToolResult<>(false, null, message);
    }
}
