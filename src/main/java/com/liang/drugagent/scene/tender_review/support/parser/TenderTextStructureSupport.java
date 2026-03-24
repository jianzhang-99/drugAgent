package com.liang.drugagent.scene.tender_review.support.parser;

import com.liang.drugagent.tool.document.support.DocumentSectionRecognizer;
import com.liang.drugagent.tool.document.support.DocumentTextNormalizer;
import com.liang.drugagent.scene.tender_review.model.Block;
import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.TenderDocumentParseResult;
import com.liang.drugagent.scene.tender_review.model.TenderSectionNode;
import com.liang.drugagent.scene.tender_review.support.extractor.TenderFieldExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文本结构化支持（字段提取、规范化、章节识别）。
 *
 * <p>已重构：通用文本处理委托给 core/document 层，
 * 招采领域字段抽取委托给 TenderFieldExtractor。</p>
 *
 * @author liangjiajian
 */
@Component
public class TenderTextStructureSupport {

    static final String SCHEMA_VERSION = "tender-review-struct-v1";
    static final String PARSER_VERSION = "v1.2.0";

    private final DocumentTextNormalizer textNormalizer;
    private final DocumentSectionRecognizer sectionRecognizer;
    private final TenderFieldExtractor fieldExtractor;

    public TenderTextStructureSupport(DocumentTextNormalizer textNormalizer,
                                      DocumentSectionRecognizer sectionRecognizer,
                                      TenderFieldExtractor fieldExtractor) {
        this.textNormalizer = textNormalizer;
        this.sectionRecognizer = sectionRecognizer;
        this.fieldExtractor = fieldExtractor;
    }

    /**
     * 从段落列表构建解析结果。
     */
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
                    .anchorChapterPath(currentChapter[0])
                    .anchorParagraphIndex(i)
                    .anchorParagraphNo(i + 1)
                    .anchorTableIndex(-1)
                    .featureTags(fieldExtractor.detectFieldTags(content))
                    .build();
            paragraphBlocks.add(block);
            fields.addAll(convertToFields(fieldExtractor.extractFields(content, docId, block.getBlockId(), currentChapter[0]), block));
        }

        return TenderDocumentParseResult.builder()
                .docId(docId)
                .sectionTree(sectionTree)
                .paragraphBlocks(paragraphBlocks)
                .tableBlocks(tableBlocks)
                .fields(fields)
                .schemaVersion(SCHEMA_VERSION)
                .parserVersion(parserVersion)
                .parseSuccess(true)
                .build();
    }

    /**
     * 从 Block 中提取结构化字段（委托给 TenderFieldExtractor）。
     */
    public List<Field> extractFieldsFromBlock(Block block) {
        return convertToFields(
                fieldExtractor.extractFields(block.getContent(), block.getDocumentId(), block.getBlockId(), block.getChapterPath()),
                block
        );
    }

    private List<Field> convertToFields(List<TenderFieldExtractor.TenderField> tenderFields, Block block) {
        return tenderFields.stream().map(tf -> Field.builder()
                .fieldId(tf.getFieldId())
                .documentId(tf.getDocumentId())
                .blockId(tf.getBlockId())
                .fieldType(tf.getFieldType())
                .fieldName(tf.getFieldName())
                .fieldValue(tf.getFieldValue())
                .normalizedValue(tf.getNormalizedValue())
                .normalizedKey(tf.getNormalizedKey())
                .chapterPath(tf.getChapterPath())
                .anchorChapterPath(block.getAnchorChapterPath())
                .anchorParagraphIndex(block.getAnchorParagraphIndex())
                .anchorTableIndex(block.getAnchorTableIndex())
                .anchorPageNo(block.getAnchorPageNo())
                .anchorSectionNo(block.getAnchorSectionNo())
                .anchorParagraphNo(block.getAnchorParagraphNo())
                .anchorTableNo(block.getAnchorTableNo())
                .confidence(tf.getConfidence())
                .build())
                .collect(Collectors.toList());
    }

    /** 规范化文本。委托给 DocumentTextNormalizer。 */
    public String normalizeText(String raw) {
        return textNormalizer.normalize(raw);
    }

    /** 判断是否为章节标题。委托给 DocumentSectionRecognizer。 */
    public boolean isSectionHeader(String content) {
        return sectionRecognizer.isSectionHeader(content);
    }

    /** 检测字段类型标签。委托给 TenderFieldExtractor。 */
    public List<String> detectFieldTags(String content) {
        return fieldExtractor.detectFieldTags(content);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
