package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.workflow.SceneType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drug Agent 执行上下文。
 *
 * <p>这个对象贯穿一次完整的 Agent 调用链，负责承载：</p>
 * <ul>
 *     <li>请求中的稳定字段，如会话、用户、问题、附件</li>
 *     <li>路由阶段识别出来的场景类型</li>
 *     <li>工作流执行过程中产生的中间结果</li>
 * </ul>
 *
 * <p>MVP 阶段先用一个松散的 attributes 存储中间结果，
 * 后续如果场景稳定，再逐步收敛成强类型对象。</p>
 *
 * @author liangjiajian
 */
public class AgentContext {

    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final String query;
    private final List<String> fileIds;
    private SceneType sceneType;
    private final Map<String, Object> attributes = new HashMap<>();

    private AgentContext(String traceId, String sessionId, String userId, String query, List<String> fileIds) {
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.query = query;
        this.fileIds = fileIds;
    }

    public static AgentContext from(DrugAgentReq req) {
        // 统一入口没有传 sessionId 时，给一个稳定默认值，避免记忆链路报空。
        String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? "default-drug-session"
                : req.getSessionId();
        return new AgentContext(
                UUID.randomUUID().toString(),
                sessionId,
                req.getUserId(),
                req.getQuery(),
                req.getFileIds()
        );
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getQuery() {
        return query;
    }

    public List<String> getFileIds() {
        return fileIds;
    }

    public SceneType getSceneType() {
        return sceneType;
    }

    public void setSceneType(SceneType sceneType) {
        this.sceneType = sceneType;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
