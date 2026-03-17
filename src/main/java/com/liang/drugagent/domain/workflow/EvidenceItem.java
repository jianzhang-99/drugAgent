package com.liang.drugagent.domain.workflow;

import lombok.Getter;
import lombok.Setter;

/**
 * 工作流证据项。
 *
 * <p>用于描述回答或分析结果背后的说明、来源和辅助信息。</p>
 *
 * @author liangjiajian
 */
@Setter
@Getter
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

}
