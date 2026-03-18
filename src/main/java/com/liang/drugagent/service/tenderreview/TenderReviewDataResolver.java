package com.liang.drugagent.service.tenderreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.tenderreview.Anchor;
import com.liang.drugagent.domain.tenderreview.Block;
import com.liang.drugagent.domain.tenderreview.CompareScope;
import com.liang.drugagent.domain.tenderreview.ExtractionMeta;
import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从请求 metadata 解析 TenderReviewData。
 */
@Component
public class TenderReviewDataResolver {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s*(.+)$");
    private static final Pattern CONTACT_PERSON_PATTERN = Pattern.compile("(?:项目)?联系人[：:]?\\s*([\\u4e00-\\u9fa5A-Za-z]{2,20})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("1\\d{2}[- ]?\\d{4}[- ]?\\d{4}");
    private static final Pattern SERVICE_COMMITMENT_PATTERN =
            Pattern.compile("([^，。；;]*?(?:问题|故障|请求|事件))[^，。；;]*?(\\d+\\s*(?:分钟|小时|天))(?:[^，。；;]*?(?:响应|到场|处理|恢复))?");
    private static final Pattern CASE_DATA_PATTERN =
            Pattern.compile("\\d+\\s*(?:家|个|项|套|名|座|台|年|%|亿元|万元|人次|余家|余个|余项)");
    private static final Pattern SECTION_NO_PATTERN = Pattern.compile("^([0-9一二三四五六七八九十]+(?:\\.[0-9]+)*)");

    private final ObjectMapper objectMapper;

    public TenderReviewDataResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TenderReviewData resolve(AgentContext context) {
        if (context == null) {
            return null;
        }
        return resolve(context.getMetadata(), context.getTraceId());
    }

    public TenderReviewData resolve(Map<String, Object> metadata, String traceId) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }

        Object rawData = metadata.get("tenderReviewData");
        if (rawData != null) {
            TenderReviewData direct = objectMapper.convertValue(rawData, TenderReviewData.class);
            if (direct != null && direct.getDocuments() != null && !direct.getDocuments().isEmpty()) {
                return direct;
            }
        }

        List<MetadataDocument> metadataDocuments = readMetadataDocuments(metadata);
        if (metadataDocuments.isEmpty()) {
            return null;
        }
        return buildFromDocuments(metadataDocuments, metadata, traceId);
    }

    private List<MetadataDocument> readMetadataDocuments(Map<String, Object> metadata) {
        Object rawDocuments = metadata.get("documents");
        if (rawDocuments == null) {
            rawDocuments = metadata.get("tenderDocuments");
        }
        if (rawDocuments == null) {
            return List.of();
        }

        List<?> items = objectMapper.convertValue(rawDocuments, List.class);
        List<MetadataDocument> documents = new ArrayList<>();
        for (Object item : items) {
            MetadataDocument document = objectMapper.convertValue(item, MetadataDocument.class);
            if (document == null || isBlank(document.content())) {
                continue;
            }
            documents.add(new MetadataDocument(
                    defaultString(document.documentId(), "DOC-" + (documents.size() + 1)),
                    defaultString(document.documentName(), "文档-" + (documents.size() + 1)),
                    defaultString(document.fileType(), "markdown"),
                    document.content()
            ));
        }
        return documents;
    }

    private TenderReviewData buildFromDocuments(List<MetadataDocument> metadataDocuments,
                                                Map<String, Object> metadata,
                                                String traceId) {
        TenderReviewData data = new TenderReviewData();
        data.setACase(buildCase(metadata, traceId));

        List<TenderDocument> documents = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        List<Field> fields = new ArrayList<>();

        for (MetadataDocument metadataDocument : metadataDocuments) {
            documents.add(toTenderDocument(metadataDocument));
            DocumentParseResult parseResult = parseDocument(metadataDocument);
            blocks.addAll(parseResult.blocks());
            fields.addAll(parseResult.fields());
        }

        data.setDocuments(documents);
        data.setBlocks(blocks);
        data.setFields(fields);
        data.setCompareScopes(buildCompareScopes(documents));
        data.setExtractionMeta(buildExtractionMeta());
        return data;
    }

    private TenderCase buildCase(Map<String, Object> metadata, String traceId) {
        TenderCase tenderCase = new TenderCase();
        tenderCase.setCaseId(defaultString(asString(metadata.get("caseId")), traceId));
        tenderCase.setScene("tender_review");
        return tenderCase;
    }

    private TenderDocument toTenderDocument(MetadataDocument metadataDocument) {
        TenderDocument document = new TenderDocument();
        document.setDocumentId(metadataDocument.documentId());
        document.setDocumentName(metadataDocument.documentName());
        document.setFileType(metadataDocument.fileType());
        return document;
    }

    private List<CompareScope> buildCompareScopes(List<TenderDocument> documents) {
        if (documents == null || documents.size() < 2) {
            return List.of();
        }
        CompareScope scope = new CompareScope();
        scope.setScopeId("CMP-" + UUID.randomUUID());
        scope.setScopeType("full_bid_compare");
        scope.setDocumentIds(documents.stream().map(TenderDocument::getDocumentId).toList());
        return List.of(scope);
    }

    private ExtractionMeta buildExtractionMeta() {
        ExtractionMeta meta = new ExtractionMeta();
        meta.setSchemaVersion("tender-review-v1");
        meta.setParserVersion("metadata-text-parser-v1");
        meta.setParseSuccess(Boolean.TRUE);
        return meta;
    }

    private DocumentParseResult parseDocument(MetadataDocument document) {
        List<Block> blocks = new ArrayList<>();
        List<Field> fields = new ArrayList<>();

        String[] lines = normalizeText(document.content()).split("\n");
        String chapterPath = "全文";
        int paragraphNo = 0;
        int tableNo = 0;

        StringBuilder paragraphBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (paragraphBuffer.length() > 0) {
                    paragraphNo++;
                    addParagraphBlock(document, chapterPath, paragraphNo, paragraphBuffer.toString(), blocks, fields);
                    paragraphBuffer.setLength(0);
                }
                if (!tableBuffer.isEmpty()) {
                    tableNo++;
                    addTableBlock(document, chapterPath, tableNo, tableBuffer, blocks, fields);
                    tableBuffer.clear();
                }
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                if (paragraphBuffer.length() > 0) {
                    paragraphNo++;
                    addParagraphBlock(document, chapterPath, paragraphNo, paragraphBuffer.toString(), blocks, fields);
                    paragraphBuffer.setLength(0);
                }
                if (!tableBuffer.isEmpty()) {
                    tableNo++;
                    addTableBlock(document, chapterPath, tableNo, tableBuffer, blocks, fields);
                    tableBuffer.clear();
                }
                chapterPath = headingMatcher.group(1).trim();
                continue;
            }

            if (line.startsWith("|")) {
                if (paragraphBuffer.length() > 0) {
                    paragraphNo++;
                    addParagraphBlock(document, chapterPath, paragraphNo, paragraphBuffer.toString(), blocks, fields);
                    paragraphBuffer.setLength(0);
                }
                tableBuffer.add(line);
                continue;
            }

            if (!tableBuffer.isEmpty()) {
                tableNo++;
                addTableBlock(document, chapterPath, tableNo, tableBuffer, blocks, fields);
                tableBuffer.clear();
            }

            if (paragraphBuffer.length() > 0) {
                paragraphBuffer.append('\n');
            }
            paragraphBuffer.append(line);
        }

        if (paragraphBuffer.length() > 0) {
            paragraphNo++;
            addParagraphBlock(document, chapterPath, paragraphNo, paragraphBuffer.toString(), blocks, fields);
        }
        if (!tableBuffer.isEmpty()) {
            tableNo++;
            addTableBlock(document, chapterPath, tableNo, tableBuffer, blocks, fields);
        }

        return new DocumentParseResult(blocks, fields);
    }

    private void addParagraphBlock(MetadataDocument document,
                                   String chapterPath,
                                   int paragraphNo,
                                   String rawContent,
                                   List<Block> blocks,
                                   List<Field> fields) {
        String normalizedContent = normalizeText(rawContent);
        Block block = buildBlock(document.documentId(), "paragraph", chapterPath, normalizedContent, rawContent, paragraphNo, null);
        blocks.add(block);
        extractParagraphFields(document, block, fields);
    }

    private void addTableBlock(MetadataDocument document,
                               String chapterPath,
                               int tableNo,
                               List<String> rawLines,
                               List<Block> blocks,
                               List<Field> fields) {
        String rawContent = String.join("\n", rawLines);
        String normalizedContent = normalizeText(rawContent);
        Block block = buildBlock(document.documentId(), "table", chapterPath, normalizedContent, rawContent, null, tableNo);
        blocks.add(block);
        extractTableFields(document, block, rawLines, fields);
    }

    private Block buildBlock(String documentId,
                             String blockType,
                             String chapterPath,
                             String content,
                             String rawContent,
                             Integer paragraphNo,
                             Integer tableNo) {
        Block block = new Block();
        block.setBlockId("BLK-" + UUID.randomUUID());
        block.setDocumentId(documentId);
        block.setBlockType(blockType);
        block.setChapterPath(chapterPath);
        block.setContent(content);
        block.setRawContent(rawContent);
        block.setAnchor(buildAnchor(chapterPath, paragraphNo, tableNo));
        return block;
    }

    private Anchor buildAnchor(String chapterPath, Integer paragraphNo, Integer tableNo) {
        Anchor anchor = new Anchor();
        anchor.setSectionNo(extractSectionNo(chapterPath));
        anchor.setParagraphNo(paragraphNo);
        anchor.setTableNo(tableNo);
        anchor.setPageNo(1);
        return anchor;
    }

    private void extractParagraphFields(MetadataDocument document, Block block, List<Field> fields) {
        String chapter = safeLower(block.getChapterPath());
        String content = block.getContent();
        if (isBlank(content)) {
            return;
        }

        Matcher contactMatcher = CONTACT_PERSON_PATTERN.matcher(content);
        if (contactMatcher.find()) {
            fields.add(buildField(document.documentId(), block, "contact_person", "联系人",
                    contactMatcher.group(1), normalizePersonName(contactMatcher.group(1)), "contact_person"));
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(content);
        while (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            fields.add(buildField(document.documentId(), block, "contact_phone", "联系电话",
                    phone, normalizePhone(phone), "contact_phone"));
        }

        if (chapter.contains("技术方案") || chapter.contains("方案") || chapter.contains("proposal")) {
            fields.add(buildField(document.documentId(), block, "proposal_segment",
                    trimFieldName(block.getChapterPath(), "技术方案"),
                    content, normalizeSentence(content), normalizeKeyFromChapter(block.getChapterPath())));
        }

        if (chapter.contains("实施") || chapter.contains("implementation")) {
            for (String stage : extractOrderedStages(content)) {
                fields.add(buildField(document.documentId(), block, "implementation_method",
                        "实施阶段", stage, normalizeSentence(stage), "implementation_method"));
            }
        }

        Matcher commitmentMatcher = SERVICE_COMMITMENT_PATTERN.matcher(content);
        while (commitmentMatcher.find()) {
            String item = normalizeSentence(commitmentMatcher.group(1));
            String value = normalizeSentence(commitmentMatcher.group(2));
            fields.add(buildField(document.documentId(), block, "service_commitment",
                    item, value, value, item));
        }

        if (chapter.contains("风险") || chapter.contains("risk")) {
            fields.add(buildField(document.documentId(), block, "risk_identification",
                    trimFieldName(block.getChapterPath(), "风险识别"),
                    content, normalizeSentence(content), normalizeKeyFromChapter(block.getChapterPath())));
        }

        if (chapter.contains("案例") || chapter.contains("case")) {
            Matcher caseMatcher = CASE_DATA_PATTERN.matcher(content);
            while (caseMatcher.find()) {
                String metric = normalizeSentence(caseMatcher.group());
                fields.add(buildField(document.documentId(), block, "case_data",
                        "案例数据", metric, metric, "case_data"));
            }
        }

        if (content.length() >= 8) {
            fields.add(buildField(document.documentId(), block, "text_segment",
                    trimFieldName(block.getChapterPath(), "文本片段"),
                    content, normalizeSentence(content), normalizeKeyFromChapter(block.getChapterPath())));
        }
    }

    private void extractTableFields(MetadataDocument document, Block block, List<String> rawLines, List<Field> fields) {
        List<List<String>> rows = parseMarkdownTable(rawLines);
        if (rows.size() < 2) {
            return;
        }

        for (List<String> row : rows) {
            if (row.size() < 2) {
                continue;
            }
            String key = normalizeSentence(row.get(0));
            String value = normalizeSentence(row.get(1));
            if (isBlank(key) || isBlank(value)) {
                continue;
            }
            if (key.contains("项目联系人") || key.contains("联系人") || key.contains("contact person") || key.equals("contact")) {
                fields.add(buildField(document.documentId(), block, "contact_person",
                        "联系人", value, normalizePersonName(value), "contact_person"));
            }
            if (key.contains("联系电话") || key.contains("电话") || key.contains("联系方式") || key.contains("phone")) {
                String normalizedPhone = normalizePhone(value);
                if (!isBlank(normalizedPhone)) {
                    fields.add(buildField(document.documentId(), block, "contact_phone",
                            "联系电话", value, normalizedPhone, "contact_phone"));
                }
            }
        }

        List<String> header = rows.get(0);
        String chapter = safeLower(block.getChapterPath());

        if (containsHeader(header, "联系人", "联系电话", "项目联系人", "contact person", "phone", "contact")) {
            for (int i = 1; i < rows.size(); i++) {
                Map<String, String> row = mapRow(header, rows.get(i));
                addSimpleFieldIfPresent(document.documentId(), block, fields, "contact_person",
                        "联系人", firstValue(row, "项目联系人", "联系人", "contact person", "contact"),
                        normalizePersonName(firstValue(row, "项目联系人", "联系人", "contact person", "contact")), "contact_person");
                addSimpleFieldIfPresent(document.documentId(), block, fields, "contact_phone",
                        "联系电话", firstValue(row, "联系电话", "电话", "联系方式", "phone"),
                        normalizePhone(firstValue(row, "联系电话", "电话", "联系方式", "phone")), "contact_phone");
            }
        }

        if (containsHeader(header, "姓名", "岗位", "name", "role") || chapter.contains("团队") || chapter.contains("team")) {
            for (int i = 1; i < rows.size(); i++) {
                Map<String, String> row = mapRow(header, rows.get(i));
                String name = firstValue(row, "姓名", "成员姓名", "name");
                if (isBlank(name)) {
                    continue;
                }
                String role = firstValue(row, "岗位", "角色", "职务", "role");
                String years = firstValue(row, "从业年限", "年限", "years");
                String resume = firstValue(row, "核心履历", "个人履历摘要", "履历", "简介", "resume");
                String value = joinNonBlank(" | ", role, years, resume);
                fields.add(buildField(document.documentId(), block, "team_member",
                        name, value, normalizeSentence(value), normalizePersonName(name)));
            }
        }

        if (containsHeader(header, "分项名称", "小计", "item", "total", "unit price") || chapter.contains("报价") || chapter.contains("quote")) {
            for (int i = 1; i < rows.size(); i++) {
                Map<String, String> row = mapRow(header, rows.get(i));
                String itemName = firstValue(row, "分项名称", "报价项", "服务项", "项目名称", "item");
                String amount = firstValue(row, "小计", "报价", "合计", "总价", "total");
                if (isBlank(itemName) || isBlank(amount) || itemName.contains("合计") || itemName.contains("总价")) {
                    continue;
                }
                String normalizedAmount = normalizeAmount(amount);
                if (normalizedAmount == null) {
                    continue;
                }
                fields.add(buildField(document.documentId(), block, "quote_item",
                        itemName, amount, normalizedAmount, "quote_item:" + normalizeSentence(itemName)));
            }
        }

        if (chapter.contains("风险") || chapter.contains("risk")) {
            for (int i = 1; i < rows.size(); i++) {
                Map<String, String> row = mapRow(header, rows.get(i));
                String riskItem = firstValue(row, "风险项", "风险点", "risk");
                if (isBlank(riskItem)) {
                    continue;
                }
                String value = joinNonBlank(" | ", riskItem, firstValue(row, "影响分析", "影响", "impact"), firstValue(row, "应对措施", "措施", "measure"));
                fields.add(buildField(document.documentId(), block, "risk_identification",
                        riskItem, value, normalizeSentence(value), normalizeSentence(riskItem)));
            }
        }
    }

    private void addSimpleFieldIfPresent(String documentId,
                                         Block block,
                                         List<Field> fields,
                                         String fieldType,
                                         String fieldName,
                                         String fieldValue,
                                         String normalizedValue,
                                         String normalizedKey) {
        if (isBlank(fieldValue) || isBlank(normalizedValue)) {
            return;
        }
        fields.add(buildField(documentId, block, fieldType, fieldName, fieldValue, normalizedValue, normalizedKey));
    }

    private Field buildField(String documentId,
                             Block block,
                             String fieldType,
                             String fieldName,
                             String fieldValue,
                             String normalizedValue,
                             String normalizedKey) {
        Field field = new Field();
        field.setFieldId("FLD-" + UUID.randomUUID());
        field.setDocumentId(documentId);
        field.setBlockId(block.getBlockId());
        field.setFieldType(fieldType);
        field.setFieldName(defaultString(fieldName, fieldType));
        field.setFieldValue(fieldValue);
        field.setNormalizedValue(normalizedValue);
        field.setNormalizedKey(normalizedKey);
        field.setChapterPath(block.getChapterPath());
        field.setAnchor(block.getAnchor());
        field.setConfidence(0.90);
        return field;
    }

    private List<List<String>> parseMarkdownTable(List<String> rawLines) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) {
                continue;
            }
            List<String> cells = splitTableRow(trimmed);
            if (cells.stream().allMatch(cell -> cell.replace("-", "").replace(":", "").isBlank())) {
                continue;
            }
            rows.add(cells);
        }
        return rows;
    }

    private List<String> splitTableRow(String line) {
        String working = line;
        if (working.startsWith("|")) {
            working = working.substring(1);
        }
        if (working.endsWith("|")) {
            working = working.substring(0, working.length() - 1);
        }
        String[] cells = working.split("\\|", -1);
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(normalizeSentence(cell));
        }
        return result;
    }

    private Map<String, String> mapRow(List<String> header, List<String> row) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            mapped.put(header.get(i), i < row.size() ? row.get(i) : "");
        }
        return mapped;
    }

    private boolean containsHeader(List<String> header, String... keywords) {
        for (String head : header) {
            for (String keyword : keywords) {
                if (head.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String firstValue(Map<String, String> row, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().contains(key) && !isBlank(entry.getValue())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private List<String> extractOrderedStages(String content) {
        List<String> stages = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?:\\d+[.、]|[一二三四五六七八九十]+[、.])\\s*([^：:\n]+)").matcher(content);
        while (matcher.find()) {
            stages.add(normalizeSentence(matcher.group(1)));
        }
        return stages;
    }

    private String extractSectionNo(String chapterPath) {
        if (isBlank(chapterPath)) {
            return null;
        }
        Matcher matcher = SECTION_NO_PATTERN.matcher(chapterPath);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String trimFieldName(String source, String fallback) {
        if (isBlank(source)) {
            return fallback;
        }
        return source.length() > 40 ? source.substring(0, 40) : source;
    }

    private String normalizeSentence(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value
                .replace("**", "")
                .replace("`", "")
                .replace("\r", "")
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String normalizePersonName(String value) {
        String normalized = normalizeSentence(value);
        return normalized == null ? null : normalized.replaceAll("[^\\u4e00-\\u9fa5A-Za-z]", "");
    }

    private String normalizePhone(String value) {
        String normalized = normalizeSentence(value);
        return normalized == null ? null : normalized.replaceAll("[^0-9]", "");
    }

    private String normalizeAmount(String value) {
        String normalized = normalizeSentence(value);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(digits).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeKeyFromChapter(String chapterPath) {
        String normalized = normalizeSentence(chapterPath);
        return normalized == null ? "text" : normalized.toLowerCase(Locale.ROOT);
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> nonBlank = new ArrayList<>();
        for (String value : values) {
            String normalized = normalizeSentence(value);
            if (!isBlank(normalized)) {
                nonBlank.add(normalized);
            }
        }
        return String.join(delimiter, nonBlank);
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MetadataDocument(String documentId, String documentName, String fileType, String content) {
    }

    private record DocumentParseResult(List<Block> blocks, List<Field> fields) {
    }
}
