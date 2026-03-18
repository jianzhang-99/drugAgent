package com.liang.drugagent.tenderreview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionMeta {

    /** 数据结构 schema 版本 */
    private String schemaVersion;

    /** 解析器版本 */
    private String parserVersion;

    /** 是否解析成功 */
    private boolean parseSuccess;
}
