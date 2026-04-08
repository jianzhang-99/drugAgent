package com.liang.drugagent.shared.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 配置。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpConfig {

    /**
     * MCP Server 名称
     */
    private String name;

    /**
     * MCP Server 类型：search / code_interpreter / Wanx / ...
     */
    private String type;

    /**
     * Server URL
     */
    private String url;

    /**
     * 认证类型：api_key / user_token / dashscope_plugin
     */
    private String authType;

    /**
     * 认证配置
     */
    private Map<String, String> authConfig;

    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * MCP Server 定义。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpServer {
        private String name;
        private String type;
        private String url;
        private String authType;
        private Map<String, String> authConfig;
    }

    /**
     * MCP 工具。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class McpTool {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }
}
