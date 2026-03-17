package com.liang.drugagent.domain.tenderreview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 标书审查结构化输入总对象。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class TenderReviewData {

    /** 任务级信息。 */
    @JsonProperty("case")
    private TenderCase aCase;
    /** 参与审查的文档列表。 */
    private List<TenderDocument> documents = new ArrayList<>();
    /** 文档比对范围列表。 */
    private List<CompareScope> compareScopes = new ArrayList<>();
    /** 原文内容块列表。 */
    private List<Block> blocks = new ArrayList<>();
    /** 结构化字段列表。 */
    private List<Field> fields = new ArrayList<>();
    /** 提取过程元信息。 */
    private ExtractionMeta extractionMeta;
}
