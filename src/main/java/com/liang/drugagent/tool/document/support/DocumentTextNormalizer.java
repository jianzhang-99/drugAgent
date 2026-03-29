package com.liang.drugagent.tool.document.support;

import org.springframework.stereotype.Component;

/**
 * 文档文本规范化工具。
 *
 * @author liangjiajian
 */
@Component
public class DocumentTextNormalizer {

    /**
     * 规范化文本：去首尾空格，合并连续空白符。
     *
     * @param raw 原始文本
     * @return 规范化后的文本
     */
    public String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }
}
