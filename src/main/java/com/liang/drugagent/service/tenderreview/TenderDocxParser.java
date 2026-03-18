package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.Anchor;
import com.liang.drugagent.domain.tenderreview.Block;
import com.liang.drugagent.domain.tenderreview.ExtractionMeta;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import com.liang.drugagent.domain.tenderreview.TenderSectionNode;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TenderDocxParser {

    private static final Set<Character> CHINESE_NUMERALS = Set.of(
            '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'
    );
    private static final Set<Character> SECTION_SEPARATORS = Set.of('、', ' ', '\u3000');

    // ---- 字段检测正则 ----
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[\\w.+\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PRICE_MULTI = Pattern.compile("报价|合计|金额");
    private static final Pattern TEAM_KEYWORDS =
            Pattern.compile("项目经理|技术负责人|成员|工程师");

    // ---- 字段值提取正则 ----
    /** 匹配数字金额，例如 "8,865,000" 或 "100万" 或 "1250000元" */
    private static final Pattern PRICE_NUMBER_PATTERN =
            Pattern.compile("[¥￥]?[\\d,，]+(?:\\.\\d+)?\\s*(?:万?元|万)|[¥￥][\\d,，]+(?:\\.\\d+)?");
    /** 匹配"角色：姓名"形式，例如 "项目经理：张三" */
    private static final Pattern TEAM_NAME_PATTERN =
            Pattern.compile("(项目经理|技术负责人|工程师|成员)[：:：]\\s*([\\u4e00-\\u9fa5]{2,4})");

    static final String SCHEMA_VERSION = "tender-review-struct-v1";
    static final String PARSER_VERSION = "v1.0.0";

    /**
     * 解析 docx 输入流，提取段落块、表格块、章节树和结构化字段。
     *
     * @param inputStream docx 文件流
     * @param docId       对应的文档 ID
     * @return 解析结果
     * @throws IOException 读取失败时抛出
     */
    public TenderDocumentParseResult parse(InputStream inputStream, String docId) throws IOException {
        boolean parseSuccess = false;
        List<Block> paragraphBlocks = new ArrayList<>();
        List<Block> tableBlocks = new ArrayList<>();
        List<TenderSectionNode> sectionTree = new ArrayList<>();
        List<Field> fields = new ArrayList<>();

        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            String[] currentChapter = {""};
            int[] paragraphIndex = {0};
            int[] tableIndex = {0};

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    Block block = processParagraph(p, docId, currentChapter, paragraphIndex, sectionTree);
                    paragraphBlocks.add(block);
                    fields.addAll(extractFieldsFromBlock(block));
                } else if (element instanceof XWPFTable t) {
                    String tableContent = extractTableContent(t);
                    int tIdx = tableIndex[0];
                    Block tableBlock = Block.builder()
                            .blockId(UUID.randomUUID().toString())
                            .documentId(docId)
                            .blockType("TABLE")
                            .chapterPath(currentChapter[0])
                            .content(tableContent)
                            .rawContent(tableContent)
                            .anchor(Anchor.builder()
                                    .chapterPath(currentChapter[0])
                                    .paragraphIndex(-1)
                                    .tableIndex(tIdx)
                                    .tableNo(tIdx + 1)
                                    .build())
                            .featureTags(detectFieldTags(tableContent))
                            .build();
                    tableBlocks.add(tableBlock);
                    fields.addAll(extractFieldsFromBlock(tableBlock));
                    tableIndex[0]++;

                    for (XWPFTableRow row : t.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph cp : cell.getParagraphs()) {
                                Block block = processParagraph(cp, docId, currentChapter, paragraphIndex, sectionTree);
                                paragraphBlocks.add(block);
                                fields.addAll(extractFieldsFromBlock(block));
                            }
                        }
                    }
                }
            }
            parseSuccess = true;
        }

        ExtractionMeta meta = ExtractionMeta.builder()
                .schemaVersion(SCHEMA_VERSION)
                .parserVersion(PARSER_VERSION)
                .parseSuccess(parseSuccess)
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

    private Block processParagraph(XWPFParagraph p, String docId,
                                    String[] currentChapter, int[] paragraphIndex,
                                    List<TenderSectionNode> sectionTree) {
        String raw = p.getText();
        String content = normalizeText(raw);

        if (isSectionHeader(content)) {
            currentChapter[0] = String.valueOf(content.charAt(0));
            sectionTree.add(TenderSectionNode.builder()
                    .title(content)
                    .level(1)
                    .children(new ArrayList<>())
                    .build());
        }

        int pIdx = paragraphIndex[0];
        Block block = Block.builder()
                .blockId(UUID.randomUUID().toString())
                .documentId(docId)
                .blockType("PARAGRAPH")
                .chapterPath(currentChapter[0])
                .content(content)
                .rawContent(raw)
                .anchor(Anchor.builder()
                        .chapterPath(currentChapter[0])
                        .paragraphIndex(pIdx)
                        .paragraphNo(pIdx + 1)
                        .tableIndex(-1)
                        .build())
                .featureTags(detectFieldTags(content))
                .build();

        paragraphIndex[0]++;
        return block;
    }

    /**
     * 从一个内容块中提取结构化字段。
     */
    List<Field> extractFieldsFromBlock(Block block) {
        List<Field> result = new ArrayList<>();
        String content = block.getContent();
        if (content == null || content.isBlank()) return result;

        // 电话字段
        Matcher phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            String value = phoneMatcher.group();
            result.add(buildField(block, "contact_phone", "联系电话", value,
                    value, "phone:" + value, 0.99));
        }

        // 邮箱字段
        Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
        while (emailMatcher.find()) {
            String value = emailMatcher.group();
            result.add(buildField(block, "contact_email", "联系邮箱", value,
                    value.toLowerCase(), "email:" + value.toLowerCase(), 0.99));
        }

        // 报价/金额字段（匹配数字+元，或含关键词段落）
        Matcher priceMatcher = PRICE_NUMBER_PATTERN.matcher(content);
        while (priceMatcher.find()) {
            String value = priceMatcher.group();
            String normalized = value.replaceAll("[¥￥,，元万]", "").trim();
            result.add(buildField(block, "bid_price", "投标报价", value,
                    normalized, "quote_total:" + normalized, 0.85));
        }
        // 如果没有匹配到具体数值，但段落含报价/合计关键词，仍标记字段
        if (result.stream().noneMatch(f -> "bid_price".equals(f.getFieldType()))
                && PRICE_MULTI.matcher(content).find()) {
            result.add(buildField(block, "bid_price", "投标报价", content,
                    "", "quote_total:", 0.60));
        }

        // 人员字段（提取"经理/负责人/成员/工程师"后面的名字）
        Matcher teamMatcher = TEAM_NAME_PATTERN.matcher(content);
        while (teamMatcher.find()) {
            String role = teamMatcher.group(1);
            String name = teamMatcher.group(2);
            result.add(buildField(block, "team_member", role, name,
                    name, "person:" + name, 0.90));
        }

        return result;
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

    String normalizeText(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    boolean isSectionHeader(String content) {
        if (content == null || content.length() < 2) return false;
        char first = content.charAt(0);
        char second = content.charAt(1);
        return CHINESE_NUMERALS.contains(first) && SECTION_SEPARATORS.contains(second);
    }

    List<String> detectFieldTags(String content) {
        List<String> tags = new ArrayList<>();
        if (content == null || content.isBlank()) return tags;
        if (PHONE_PATTERN.matcher(content).find()) tags.add("PHONE_FIELD");
        if (EMAIL_PATTERN.matcher(content).find()) tags.add("EMAIL_FIELD");
        if (PRICE_MULTI.matcher(content).find() || content.contains("元")) tags.add("PRICE_FIELD");
        if (TEAM_KEYWORDS.matcher(content).find()) tags.add("TEAM_FIELD");
        return tags;
    }

    private String extractTableContent(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(cells.get(i).getText());
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
