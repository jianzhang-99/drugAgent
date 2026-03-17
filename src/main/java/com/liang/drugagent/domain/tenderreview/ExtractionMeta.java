package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

/**
 * 结构化提取元信息。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class ExtractionMeta {

    /** 结构化数据 schema 版本。 */
    private String schemaVersion;
    /** 解析器版本。 */
    private String parserVersion;
    /** 本次解析是否成功。 */
    private Boolean parseSuccess;
}
