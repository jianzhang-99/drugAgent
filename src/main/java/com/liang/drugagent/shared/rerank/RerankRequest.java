package com.liang.drugagent.shared.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rerank 请求对象。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {

    /**
     * 查询文本
     */
    private String query;

    /**
     * 待排序的文档列表
     */
    private List<String> documents;

    /**
     * 返回数量（默认返回所有文档，按相关性排序）
     */
    private Integer topN;

    /**
     * 额外参数
     */
    private String returnVector;
}
