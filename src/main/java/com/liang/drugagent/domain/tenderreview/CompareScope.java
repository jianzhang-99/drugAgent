package com.liang.drugagent.domain.tenderreview;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档比对范围。
 *
 * @author liangjiajian
 */
@Getter
@Setter
public class CompareScope {

    /** 比对范围唯一 ID。 */
    private String scopeId;
    /** 比对范围类型，例如 full_bid_compare。 */
    private String scopeType;
    /** 参与本次比对的文档 ID 列表。 */
    private List<String> documentIds = new ArrayList<>();
}
