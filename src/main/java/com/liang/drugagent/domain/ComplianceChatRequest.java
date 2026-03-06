package com.liang.drugagent.domain;

import lombok.Data;

public class ComplianceChatRequest {
    private String sessionId;
    private String fileId;
    private String message;
    private String type; // 场景类型

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
