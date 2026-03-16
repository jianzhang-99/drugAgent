package com.liang.drugagent.domain.req;

import java.util.List;
import java.util.Map;

/**
 * Drug Agent 请求对象
 */
public class DrugAgentReq {

    private String sessionId;
    private String userId;
    private String query;
    private String sceneHint;
    private List<String> fileIds;
    private Map<String, Object> metadata;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSceneHint() {
        return sceneHint;
    }

    public void setSceneHint(String sceneHint) {
        this.sceneHint = sceneHint;
    }

    public List<String> getFileIds() {
        return fileIds;
    }

    public void setFileIds(List<String> fileIds) {
        this.fileIds = fileIds;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
