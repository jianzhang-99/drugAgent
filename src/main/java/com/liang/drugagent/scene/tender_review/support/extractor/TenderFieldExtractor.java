package com.liang.drugagent.scene.tender_review.support.extractor;

import com.liang.drugagent.scene.tender_review.model.Field;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标书领域字段抽取器。
 *
 * <p>从文档块中抽取招采领域特定字段，如：
 * <ul>
 *   <li>联系人电话</li>
 *   <li>联系邮箱</li>
 *   <li>投标报价</li>
 *   <li>团队成员</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Component
public class TenderFieldExtractor {

    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PRICE_MULTI = Pattern.compile("报价|合计|金额");
    private static final Pattern TEAM_KEYWORDS =
            Pattern.compile("项目经理|技术负责人|成员|工程师");
    private static final Pattern PRICE_NUMBER_PATTERN =
            Pattern.compile("[¥￥]?[\\d,，]+(?:\\.\\d+)?\\s*(?:万?元|万)|[¥￥][\\d,，]+(?:\\.\\d+)?");
    private static final Pattern TEAM_NAME_PATTERN =
            Pattern.compile("(项目经理|技术负责人|工程师|成员)[：:：]\\s*([\\u4e00-\\u9fa5]{2,4})");

    /**
     * 从文本内容中检测字段类型标签。
     *
     * @param content 文本内容
     * @return 字段类型标签列表
     */
    public List<String> detectFieldTags(String content) {
        List<String> tags = new ArrayList<>();
        if (content == null || content.isBlank()) return tags;
        if (PHONE_PATTERN.matcher(content).find()) tags.add("PHONE_FIELD");
        if (EMAIL_PATTERN.matcher(content).find()) tags.add("EMAIL_FIELD");
        if (PRICE_MULTI.matcher(content).find() || content.contains("元")) tags.add("PRICE_FIELD");
        if (TEAM_KEYWORDS.matcher(content).find()) tags.add("TEAM_FIELD");
        return tags;
    }

    /**
     * 从文本内容中提取结构化字段。
     *
     * @param content       文本内容
     * @param documentId   文档 ID
     * @param blockId      块 ID
     * @param chapterPath  章节路径
     * @return 字段列表
     */
    public List<TenderField> extractFields(String content, String documentId, String blockId, String chapterPath) {
        List<TenderField> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;

        // 提取电话
        Matcher phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            String value = phoneMatcher.group();
            result.add(TenderField.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .blockId(blockId)
                    .fieldType("contact_phone")
                    .fieldName("联系电话")
                    .fieldValue(value)
                    .normalizedValue(value)
                    .normalizedKey("phone:" + value)
                    .chapterPath(chapterPath)
                    .confidence(0.99)
                    .build());
        }

        // 提取邮箱
        Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            String value = emailMatcher.group();
            result.add(TenderField.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .blockId(blockId)
                    .fieldType("contact_email")
                    .fieldName("联系邮箱")
                    .fieldValue(value)
                    .normalizedValue(value.toLowerCase())
                    .normalizedKey("email:" + value.toLowerCase())
                    .chapterPath(chapterPath)
                    .confidence(0.99)
                    .build());
        }

        // 提取报价
        Matcher priceMatcher = PRICE_NUMBER_PATTERN.matcher(content);
        while (priceMatcher.find()) {
            String value = priceMatcher.group();
            String normalized = value.replaceAll("[¥￥,，元万]", "").trim();
            result.add(TenderField.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .blockId(blockId)
                    .fieldType("bid_price")
                    .fieldName("投标报价")
                    .fieldValue(value)
                    .normalizedValue(normalized)
                    .normalizedKey("quote_total:" + normalized)
                    .chapterPath(chapterPath)
                    .confidence(0.85)
                    .build());
        }
        if (result.stream().noneMatch(f -> "bid_price".equals(f.getFieldType()))
                && PRICE_MULTI.matcher(content).find()) {
            result.add(TenderField.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .blockId(blockId)
                    .fieldType("bid_price")
                    .fieldName("投标报价")
                    .fieldValue(content)
                    .normalizedValue("")
                    .normalizedKey("quote_total:")
                    .chapterPath(chapterPath)
                    .confidence(0.60)
                    .build());
        }

        // 提取团队成员
        Matcher teamMatcher = TEAM_NAME_PATTERN.matcher(content);
        while (teamMatcher.find()) {
            String role = teamMatcher.group(1);
            String name = teamMatcher.group(2);
            result.add(TenderField.builder()
                    .fieldId(UUID.randomUUID().toString())
                    .documentId(documentId)
                    .blockId(blockId)
                    .fieldType("team_member")
                    .fieldName(role)
                    .fieldValue(name)
                    .normalizedValue(name)
                    .normalizedKey("person:" + name)
                    .chapterPath(chapterPath)
                    .confidence(0.90)
                    .build());
        }

        return result;
    }

    /**
     * 标书字段值对象。
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TenderField {
        private String fieldId;
        private String documentId;
        private String blockId;
        private String fieldType;
        private String fieldName;
        private String fieldValue;
        private String normalizedValue;
        private String normalizedKey;
        private String chapterPath;
        private Double confidence;
    }
}
