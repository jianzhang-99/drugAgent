package com.liang.drugagent.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流证据项。
 *
 * <p>用于描述回答或分析结果背后的说明、来源和辅助信息。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceItem {

    private String title;
    private String content;
    private String source;

}
