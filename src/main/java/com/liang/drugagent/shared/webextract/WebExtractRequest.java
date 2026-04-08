package com.liang.drugagent.shared.webextract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Extractor 请求。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebExtractRequest {

    /**
     * 要提取的网页 URL
     */
    private String url;

    /**
     * 内容提取提示词，指定要从网页中提取的信息
     */
    private String prompt;

    /**
     * 模型名称（可选，默认使用 qwen3.5-plus）
     */
    private String model;
}
