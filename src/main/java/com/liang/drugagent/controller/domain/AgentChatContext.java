package com.liang.drugagent.controller.domain;

import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 对话上下文。
 *
 * <p>保存请求参数与执行状态，确保链路中上下文不丢失。
 * 使用明确的成员变量替代 Map 结构，保证类型安全。
 *
 * @author liangjiajian
 */
@Getter
public class AgentChatContext {

    /** 链路追踪ID。 */
    private final String traceId;
    /** 会话ID。 */
    private final String sessionId;
    /** 用户query。 */
    private final String query;
    /** 文件ID列表。 */
    private final List<String> fileIds;
    /** 请求元数据。 */
    private final Map<String, Object> metadata;

    /** 场景类型。 */
    @Setter
    private SceneEnum sceneType;

    /** 会话信息。 */
    @Setter
    private ChatSession session;

    /** 历史消息列表。 */
    @Setter
    private List<ChatMessage> historyMessages;

    /** 最近会话摘要。 */
    @Setter
    private String recentSummary;

    /** 扩展属性（用于临时存储非通用数据）。 */
    @Setter
    private Map<String, Object> attributes;

    private AgentChatContext(String traceId, String sessionId, String query, List<String> fileIds,
                             Map<String, Object> metadata) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.query = query;
        this.fileIds = fileIds;
        this.metadata = metadata == null ? Map.of() : metadata;
    }

    /**
     * 从请求构建上下文。
     *
     * <p>sessionId 为空时使用默认值，保证链路稳定。
     */
    public static AgentChatContext from(AgentChatReq req) {
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? "default-drug-session"
                : req.getSessionId();
        return new AgentChatContext(
                UUID.randomUUID().toString(),
                sessionId,
                req.getQuery(),
                req.getFileIds(),
                req.getMetadata()
        );
    }

    /**
     * 从标书审查请求构建上下文。
     */
    public static AgentChatContext fromToolRequest(String sessionId, String query,
                                                    List<String> fileIds, Map<String, Object> metadata) {
        return new AgentChatContext(
                UUID.randomUUID().toString(),
                sessionId != null ? sessionId : "default-drug-session",
                query,
                fileIds != null ? fileIds : List.of(),
                metadata
        );
    }

}
