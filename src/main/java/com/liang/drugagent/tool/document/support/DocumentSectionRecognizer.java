package com.liang.drugagent.tool.document.support;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 文档章节识别工具。
 *
 * @author liangjiajian
 */
@Component
public class DocumentSectionRecognizer {

    /** 中文数字（一～十）。 */
    private static final Set<Character> CHINESE_NUMERALS = Set.of(
            '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'
    );
    /** 章节分隔符。 */
    private static final Set<Character> SECTION_SEPARATORS = Set.of('、', ' ', '\u3000');

    /**
     * 判断是否为章节标题（如"一、xxx"）。
     *
     * @param content 文本内容
     * @return 是否为章节标题
     */
    public boolean isSectionHeader(String content) {
        if (content == null || content.length() < 2) return false;
        char first = content.charAt(0);
        char second = content.charAt(1);
        return CHINESE_NUMERALS.contains(first) && SECTION_SEPARATORS.contains(second);
    }
}
