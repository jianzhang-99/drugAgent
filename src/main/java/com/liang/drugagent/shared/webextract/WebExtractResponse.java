package com.liang.drugagent.shared.webextract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Web Extractor 响应。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebExtractResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 提取到的网页内容
     */
    private String content;

    /**
     * 网页标题（如果可用）
     */
    private String title;

    /**
     * 网页 URL
     */
    private String url;

    /**
     * 错误码（失败时）
     */
    private String errorCode;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建错误响应。
     */
    public static WebExtractResponse error(String errorCode, String errorMessage) {
        return WebExtractResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
