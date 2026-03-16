package com.liang.drugagent.domain.resp;

/**
 * 统一 Agent 证据项
 */
public class EvidenceItem {

    private String title;
    private String content;
    private String source;

    public EvidenceItem() {
    }

    public EvidenceItem(String title, String content, String source) {
        this.title = title;
        this.content = content;
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
