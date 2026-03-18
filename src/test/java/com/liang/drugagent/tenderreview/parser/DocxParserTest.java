package com.liang.drugagent.tenderreview.parser;

import com.liang.drugagent.tenderreview.domain.Block;
import com.liang.drugagent.tenderreview.domain.ExtractedField;
import com.liang.drugagent.tenderreview.domain.SectionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocxParserTest {

    private static final String SAMPLE_A =
            "D:/Program/tools/drugAgent/doc/场景一 标书审查/02_原始样本素材/测试样本A_疑似围标投标文件.docx";
    private static final String SAMPLE_B =
            "D:/Program/tools/drugAgent/doc/场景一 标书审查/02_原始样本素材/测试样本B_疑似围标投标文件.docx";

    private DocxParser parser;

    @BeforeEach
    void setUp() {
        parser = new DocxParser();
    }

    // ---- normalizeText ----

    @Test
    void normalizeTextTrimsAndCollapseWhitespace() {
        assertThat(parser.normalizeText("  hello   world  ")).isEqualTo("hello world");
    }

    @Test
    void normalizeTextHandlesNull() {
        assertThat(parser.normalizeText(null)).isEqualTo("");
    }

    @Test
    void normalizeTextHandlesEmpty() {
        assertThat(parser.normalizeText("")).isEqualTo("");
    }

    // ---- isSectionHeader ----

    @Test
    void isSectionHeaderDetectsDunHao() {
        assertThat(parser.isSectionHeader("一、投标人资格")).isTrue();
    }

    @Test
    void isSectionHeaderDetectsSpace() {
        assertThat(parser.isSectionHeader("二 投标文件")).isTrue();
    }

    @Test
    void isSectionHeaderDetectsIdeographicSpace() {
        assertThat(parser.isSectionHeader("三\u3000技术要求")).isTrue();
    }

    @Test
    void isSectionHeaderReturnsFalseForShortString() {
        assertThat(parser.isSectionHeader("一")).isFalse();
    }

    @Test
    void isSectionHeaderReturnsFalseForNonChinese() {
        assertThat(parser.isSectionHeader("1、普通段落")).isFalse();
    }

    @Test
    void isSectionHeaderReturnsFalseForNull() {
        assertThat(parser.isSectionHeader(null)).isFalse();
    }

    // ---- detectFieldTags ----

    @Test
    void detectsPhoneField() {
        List<String> tags = parser.detectFieldTags("联系电话：13812345678");
        assertThat(tags).contains("PHONE_FIELD");
    }

    @Test
    void detectsEmailField() {
        List<String> tags = parser.detectFieldTags("邮箱：test@example.com");
        assertThat(tags).contains("EMAIL_FIELD");
    }

    @Test
    void detectsPriceField() {
        List<String> tags = parser.detectFieldTags("投标报价合计：100万元");
        assertThat(tags).contains("PRICE_FIELD");
    }

    @Test
    void detectsTeamField() {
        List<String> tags = parser.detectFieldTags("项目经理：张三");
        assertThat(tags).contains("TEAM_FIELD");
    }

    @Test
    void detectsNoTagsForPlainText() {
        List<String> tags = parser.detectFieldTags("这是普通的段落内容");
        assertThat(tags).isEmpty();
    }

    @Test
    void detectsEmptyTagsForBlank() {
        assertThat(parser.detectFieldTags("")).isEmpty();
        assertThat(parser.detectFieldTags(null)).isEmpty();
    }

    // ---- parse with real sample files ----

    @Test
    void parseSampleAParagraphCount() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            assertThat(result.getParagraphCount()).isGreaterThanOrEqualTo(90);
        }
    }

    @Test
    void parseSampleATableCount() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            assertThat(result.getTableCount()).isEqualTo(5);
        }
    }

    @Test
    void parseSampleASectionTree() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            List<SectionNode> tree = result.getSectionTree();
            assertThat(tree).hasSizeGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void parseSampleAParagraphIndexMonotonicallyIncreasing() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            List<Block> blocks = result.getParagraphBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                assertThat(blocks.get(i).getAnchor().getParagraphIndex()).isEqualTo(i);
            }
        }
    }

    @Test
    void parseSampleATableIndexRange() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            List<Block> tableBlocks = result.getTableBlocks();
            assertThat(tableBlocks).hasSize(5);
            for (int i = 0; i < tableBlocks.size(); i++) {
                assertThat(tableBlocks.get(i).getAnchor().getTableIndex()).isEqualTo(i);
            }
        }
    }

    @Test
    void parseSampleAParagraphBlocksHaveNegativeTableIndex() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-a");
            result.getParagraphBlocks().forEach(b ->
                    assertThat(b.getAnchor().getTableIndex()).isEqualTo(-1));
        }
    }

    @Test
    void parseSampleBParagraphCount() throws IOException {
        Path path = Paths.get(SAMPLE_B);
        assumeTrue(Files.exists(path), "样本文件B不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-b");
            assertThat(result.getParagraphCount()).isGreaterThanOrEqualTo(90);
        }
    }

    @Test
    void parseSampleBTableCount() throws IOException {
        Path path = Paths.get(SAMPLE_B);
        assumeTrue(Files.exists(path), "样本文件B不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "test-doc-b");
            assertThat(result.getTableCount()).isEqualTo(5);
        }
    }

    @Test
    void parseResultDocIdMatchesInput() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "custom-doc-id");
            assertThat(result.getDocId()).isEqualTo("custom-doc-id");
        }
    }

    // ---- extractionMeta ----

    @Test
    void extractionMetaIsPopulated() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "doc-meta");
            assertThat(result.getExtractionMeta()).isNotNull();
            assertThat(result.getExtractionMeta().isParseSuccess()).isTrue();
            assertThat(result.getExtractionMeta().getSchemaVersion())
                    .isEqualTo(DocxParser.SCHEMA_VERSION);
            assertThat(result.getExtractionMeta().getParserVersion())
                    .isEqualTo(DocxParser.PARSER_VERSION);
        }
    }

    // ---- extractFieldsFromBlock ----

    @Test
    void extractFieldsPhoneFromBlock() {
        Block block = buildBlock("联系电话：13812345678");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).anyMatch(f ->
                f.getFieldType().equals("contact_phone") &&
                f.getFieldValue().equals("13812345678") &&
                f.getNormalizedKey().equals("phone:13812345678"));
    }

    @Test
    void extractFieldsEmailFromBlock() {
        Block block = buildBlock("邮箱：test@example.com");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).anyMatch(f ->
                f.getFieldType().equals("contact_email") &&
                f.getNormalizedKey().equals("email:test@example.com"));
    }

    @Test
    void extractFieldsPriceFromBlock() {
        Block block = buildBlock("投标总价：1250000元");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).anyMatch(f ->
                f.getFieldType().equals("bid_price") &&
                f.getNormalizedKey().startsWith("quote_total:"));
    }

    @Test
    void extractFieldsTeamMemberFromBlock() {
        Block block = buildBlock("项目经理：张三，负责项目整体管理");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).anyMatch(f ->
                f.getFieldType().equals("team_member") &&
                f.getFieldValue().equals("张三") &&
                f.getNormalizedKey().equals("person:张三"));
    }

    @Test
    void extractFieldsReturnsEmptyForPlainText() {
        Block block = buildBlock("本项目为普通采购项目，无特殊要求。");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).isEmpty();
    }

    @Test
    void extractedFieldHasBlockIdAndDocumentId() {
        Block block = buildBlock("联系方式：13900001234");
        List<ExtractedField> fields = parser.extractFieldsFromBlock(block);
        assertThat(fields).isNotEmpty();
        assertThat(fields.get(0).getBlockId()).isEqualTo(block.getBlockId());
        assertThat(fields.get(0).getDocumentId()).isEqualTo(block.getDocumentId());
    }

    // ---- Anchor new fields ----

    @Test
    void paragraphBlockAnchorHasParagraphNo() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "doc-anchor");
            List<Block> blocks = result.getParagraphBlocks();
            // paragraphNo = paragraphIndex + 1
            assertThat(blocks.get(0).getAnchor().getParagraphNo()).isEqualTo(1);
            assertThat(blocks.get(1).getAnchor().getParagraphNo()).isEqualTo(2);
        }
    }

    @Test
    void tableBlockAnchorHasTableNo() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "doc-anchor-tbl");
            List<Block> tables = result.getTableBlocks();
            for (int i = 0; i < tables.size(); i++) {
                assertThat(tables.get(i).getAnchor().getTableNo()).isEqualTo(i + 1);
            }
        }
    }

    // ---- fields list from real sample ----

    @Test
    void parseSampleAFieldsNotEmpty() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "doc-fields");
            assertThat(result.getFields()).isNotNull();
            assertThat(result.getFieldCount()).isGreaterThan(0);
        }
    }

    @Test
    void parseSampleAFieldsHaveRequiredAttributes() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = parser.parse(is, "doc-fields-attr");
            result.getFields().forEach(f -> {
                assertThat(f.getFieldId()).isNotBlank();
                assertThat(f.getDocumentId()).isEqualTo("doc-fields-attr");
                assertThat(f.getBlockId()).isNotBlank();
                assertThat(f.getFieldType()).isNotBlank();
                assertThat(f.getNormalizedKey()).isNotBlank();
                assertThat(f.getConfidence()).isGreaterThan(0.0);
            });
        }
    }

    // ---- helper ----

    private Block buildBlock(String content) {
        return Block.builder()
                .blockId(java.util.UUID.randomUUID().toString())
                .documentId("test-doc")
                .blockType(com.liang.drugagent.tenderreview.domain.enums.BlockType.PARAGRAPH)
                .chapterPath("一")
                .content(content)
                .rawContent(content)
                .anchor(com.liang.drugagent.tenderreview.domain.Anchor.builder()
                        .chapterPath("一").paragraphIndex(0).paragraphNo(1).tableIndex(-1).build())
                .featureTags(new java.util.ArrayList<>())
                .build();
    }
}
