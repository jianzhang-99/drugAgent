package com.liang.drugagent.scene.tender_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档比对范围。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompareScope {

    /** 比对范围唯一 ID。 */
    private String scopeId;
    /** 比对范围类型，例如 full_bid_compare。 */
    private String scopeType;
    /** 参与本次比对的文档 ID 列表。 */
    private List<String> documentIds = new ArrayList<>();
}
