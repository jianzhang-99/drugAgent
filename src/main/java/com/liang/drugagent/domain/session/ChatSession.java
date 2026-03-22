package com.liang.drugagent.domain.session;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天会话实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    /**
     * 会话唯一标识
     */
    private String id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 日期分组：今天、昨天、过去7天
     */
    private String dateGroup;

    /**
     * 场景类型
     */
    private String scene;

    /**
     * 消息列表
     */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 聊天消息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatMessage {
        /**
         * 消息角色：user/assistant
         */
        private String role;

        /**
         * 消息内容
         */
        private String content;

        /**
         * 消息时间
         */
        private String time;

        /**
         * 附件列表
         */
        @Builder.Default
        private List<String> attachments = new ArrayList<>();

        /**
         * Agent 结果
         */
        private AgentResult result;
    }

    /**
     * Agent 结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgentResult {
        private String scene;
        private String riskLevel;
        private Integer score;
        private Integer docCount;
        private String summary;
        private List<String> managementSummary;
        private List<String> suggestedActions;
        private List<String> steps;
        private String traceId;
    }
}
