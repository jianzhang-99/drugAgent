package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.enums.SceneEnum;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drug Agent 执行上下文。
 *
 *
 * 保存本次请求的上下文 防止后续丢失上下文记忆
 *
 * @author liangjiajian
 */
@Getter
public class AgentContext {

    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final String query;
    private final List<String> fileIds;
    @Setter
    private SceneEnum sceneType;
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

}
