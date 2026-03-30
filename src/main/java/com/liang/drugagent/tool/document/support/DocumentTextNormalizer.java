package com.liang.drugagent.tool.document.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 轻量文本清洗工具。
 *
 * <p>职责：
 * <ul>
 *   <li>统一换行符</li>
 *   <li>去掉明显多余空白</li>
 *   <li>清理常见脏字符</li>
 * </ul>
 *
 * <p>当前阶段不做复杂语义纠错、章节识别、OCR。</p>
 */
@Slf4j
@Component
public class DocumentTextNormalizer {

    /**
     * 连续空白字符（空格、制表符、多个换行）合并为一个换行或空格。
     */
    private static final Pattern MULTIPLE_BLANKS = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[\\u00A0\\u2000-\\u200B\\u3000]");

    /**
     * 清洗给定文本。
     *
     * @param text 原始文本
     * @return 清洗后文本
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        // 统一换行符为 \n
        result = result.replace("\r\n", "\n").replace("\r", "\n");

        // 清理特殊空白字符（如不间断空格）
        result = SPECIAL_CHARS.matcher(result).replaceAll(" ");

        // 合并多个连续空格/制表符为单个空格
        result = MULTIPLE_BLANKS.matcher(result).replaceAll(" ");

        // 合并多个连续换行为最多两个换行（保留段落分隔）
        result = MULTIPLE_NEWLINES.matcher(result).replaceAll("\n\n");

        // 去掉首尾空白
        result = result.trim();

        return result;
    }
}
