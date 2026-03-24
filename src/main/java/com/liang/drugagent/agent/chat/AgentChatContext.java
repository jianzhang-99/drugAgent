package com.liang.drugagent.agent.chat;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.controller.domain.request.agent.DrugAgentReq;
import com.liang.drugagent.agent.route.AgentRouteContext;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行上下文。
 *
 * <p>保存请求参数与执行状态，确保链路中上下文不丢失。
 *
 * @author liangjiajian
 */
@Getter
public class AgentChatContext {

    /** 链路追踪ID。 */
    private final String traceId;
    /** 会话ID。 */
    private final String sessionId;
    /** 用户ID。 */
    private final String userId;
    /** 用户query。 */
    private final String query;
    /** 文件ID列表。 */
    private final List<String> fileIds;
    /** 请求元数据。 */
    private final Map<String, Object> metadata;
    /** 场景类型。 */
    @Setter
    private SceneEnum sceneType;
    /** 路由上下文。 */
    @Setter
    private AgentRouteContext intentContext;
    /** 扩展属性。 */
    private final Map<String, Object> attributes = new HashMap<>();

    private AgentChatContext(String traceId, String sessionId, String userId, String query, List<String> fileIds,
                             Map<String, Object> metadata) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.query = query;
        this.fileIds = fileIds;
        this.metadata = metadata == null ? Map.of() : metadata;
    }

    /**
     * 从请求构建上下文。
     *
     * <p>sessionId 为空时使用默认值，保证链路稳定。
     */
    public static AgentChatContext from(DrugAgentReq req) {
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? "default-drug-session"
                : req.getSessionId();
        return new AgentChatContext(
                UUID.randomUUID().toString(),
                sessionId,
                req.getUserId(),
                req.getQuery(),
                req.getFileIds(),
                req.getMetadata()
        );
    }

}
