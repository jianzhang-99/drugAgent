package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.Anchor;
import com.liang.drugagent.domain.tenderreview.Block;
import com.liang.drugagent.domain.tenderreview.ExtractionMeta;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import com.liang.drugagent.domain.tenderreview.TenderSectionNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenderTextStructureSupport {

    private static final Set<Character> CHINESE_NUMERALS = Set.of(
            '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'
    );
    private static final Set<Character> SECTION_SEPARATORS = Set.of('、', ' ', '\u3000');

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

    static final String SCHEMA_VERSION = "tender-review-struct-v1";
    static final String PARSER_VERSION = "v1.1.0";

    public TenderDocumentParseResult buildFromParagraphs(List<String> paragraphs, String docId, String parserVersion) {
        List<Block> paragraphBlocks = new ArrayList<>();
        List<Block> tableBlocks = new ArrayList<>();
        List<TenderSectionNode> sectionTree = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        String[] currentChapter = {""};

        for (int i = 0; i < paragraphs.size(); i++) {
            String raw = defaultString(paragraphs.get(i));
            String content = normalizeText(raw);
            if (content.isBlank()) {
                continue;
            }

            if (isSectionHeader(content)) {
                currentChapter[0] = String.valueOf(content.charAt(0));
                sectionTree.add(TenderSectionNode.builder()
                        .title(content)
                        .level(1)
                        .children(new ArrayList<>())
                        .build());
            }

            Block block = Block.builder()
                    .blockId(UUID.randomUUID().toString())
                    .documentId(docId)
                    .blockType("PARAGRAPH")
                    .chapterPath(currentChapter[0])
                    .content(content)
                    .rawContent(raw)
                    .anchor(Anchor.builder()
                            .chapterPath(currentChapter[0])
                            .paragraphIndex(i)
                            .paragraphNo(i + 1)
                            .tableIndex(-1)
                            .build())
                    .featureTags(detectFieldTags(content))
                    .build();
            paragraphBlocks.add(block);
            fields.addAll(extractFieldsFromBlock(block));
        }

        ExtractionMeta meta = ExtractionMeta.builder()
                .schemaVersion(SCHEMA_VERSION)
                .parserVersion(parserVersion)
                .parseSuccess(true)
                .build();

        return TenderDocumentParseResult.builder()
                .docId(docId)
                .sectionTree(sectionTree)
                .paragraphBlocks(paragraphBlocks)
                .tableBlocks(tableBlocks)
                .fields(fields)
                .extractionMeta(meta)
                .build();
    }

    public List<Field> extractFieldsFromBlock(Block block) {
        List<Field> result = new ArrayList<>();
        String content = block.getContent();
        if (content == null || content.isBlank()) return result;

        Matcher phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            String value = phoneMatcher.group();
            result.add(buildField(block, "contact_phone", "联系电话", value,
                    value, "phone:" + value, 0.99));
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            String value = emailMatcher.group();
            result.add(buildField(block, "contact_email", "联系邮箱", value,
                    value.toLowerCase(), "email:" + value.toLowerCase(), 0.99));
        }

        Matcher priceMatcher = PRICE_NUMBER_PATTERN.matcher(content);
        while (priceMatcher.find()) {
            String value = priceMatcher.group();
            String normalized = value.replaceAll("[¥￥,，元万]", "").trim();
            result.add(buildField(block, "bid_price", "投标报价", value,
                    normalized, "quote_total:" + normalized, 0.85));
        }
        if (result.stream().noneMatch(f -> "bid_price".equals(f.getFieldType()))
                && PRICE_MULTI.matcher(content).find()) {
            result.add(buildField(block, "bid_price", "投标报价", content,
                    "", "quote_total:", 0.60));
        }

        Matcher teamMatcher = TEAM_NAME_PATTERN.matcher(content);
        while (teamMatcher.find()) {
            String role = teamMatcher.group(1);
            String name = teamMatcher.group(2);
            result.add(buildField(block, "team_member", role, name,
                    name, "person:" + name, 0.90));
        }

        return result;
    }

    public String normalizeText(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    public boolean isSectionHeader(String content) {
        if (content == null || content.length() < 2) return false;
        char first = content.charAt(0);
        char second = content.charAt(1);
        return CHINESE_NUMERALS.contains(first) && SECTION_SEPARATORS.contains(second);
    }

    public List<String> detectFieldTags(String content) {
        List<String> tags = new ArrayList<>();
        if (content == null || content.isBlank()) return tags;
        if (PHONE_PATTERN.matcher(content).find()) tags.add("PHONE_FIELD");
        if (EMAIL_PATTERN.matcher(content).find()) tags.add("EMAIL_FIELD");
        if (PRICE_MULTI.matcher(content).find() || content.contains("元")) tags.add("PRICE_FIELD");
        if (TEAM_KEYWORDS.matcher(content).find()) tags.add("TEAM_FIELD");
        return tags;
    }

    private Field buildField(Block block, String fieldType, String fieldName,
                             String fieldValue, String normalizedValue,
                             String normalizedKey, double confidence) {
        return Field.builder()
                .fieldId(UUID.randomUUID().toString())
                .documentId(block.getDocumentId())
                .blockId(block.getBlockId())
                .fieldType(fieldType)
                .fieldName(fieldName)
                .fieldValue(fieldValue)
                .normalizedValue(normalizedValue)
                .normalizedKey(normalizedKey)
                .chapterPath(block.getChapterPath())
                .anchor(block.getAnchor())
                .confidence(confidence)
                .build();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
