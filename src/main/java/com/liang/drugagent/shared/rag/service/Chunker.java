package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档 Chunk 切分器。
 *
 * <p>采用"结构优先、长度兜底"的切分策略：
 * <ul>
 *   <li>优先按标题、章节、段落边界切分，保留语义完整性</li>
 *   <li>单个段落超过 chunk 大小时，按句子边界切分，避免截断语义</li>
 *   <li>相邻 chunk 之间保留少量 overlap（80-150字符），维持上下文连续性</li>
 * </ul>
 *
 * <p>不同文档类型的切分策略：
 * <ul>
 *   <li>制度/规范类（REGULATION）：偏章节与条款切分</li>
 *   <li>合同类（CONTRACT）：偏条款切分</li>
 *   <li>标书类（TENDER）：偏章节与小节切分</li>
 * </ul>
 */
@Slf4j
@Component
public class Chunker {

    /**
     * 默认 chunk 大小（字符数）
     */
    private static final int DEFAULT_CHUNK_SIZE = 800;

    /**
     * 默认 overlap 大小（字符数）
     */
    private static final int DEFAULT_OVERLAP = 120;

    /**
     * overlap 下限
     */
    private static final int MIN_OVERLAP = 80;

    /**
     * overlap 上限
     */
    private static final int MAX_OVERLAP = 150;

    /**
     * Markdown 标题正则（# 一级、## 二级 等）
     */
    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");

    /**
     * 数字编号标题正则（1. 2.1 3、 等）
     */
    private static final Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("(?m)^(\\d+(?:[.、]\\d*)*)\\s+(.+)$");

    /**
     * 中文数字标题正则（一、二、（一）等）
     */
    private static final Pattern CN_NUMBER_HEADING_PATTERN = Pattern.compile(
            "(?m)^([一二三四五六七八九十百]+(?:[、)]\\d+)?)\\s+(.+)$"
    );

    /**
     * 条款编号正则（第X条、第X款等），仅捕获条款编号本身，不捕获正文
     */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?m)^(第[一二三四五六七八九十百\\d]+[条款节章卷篇])"
    );

    /**
     * 句子结束标点（用于长段落拆分）
     */
    private static final Pattern SENTENCE_END_PATTERN = Pattern.compile(
            "[。！？；\\.!?;](?=\\s|$)"
    );

    /**
     * 将文档切分为 chunks
     */
    public List<RagChunk> chunk(RagDocument document) {
        return chunk(document, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * 将文档切分为 chunks（自定义大小）
     */
    public List<RagChunk> chunk(RagDocument document, int chunkSize, int overlap) {
        String text = document.getRawText();
        if (text == null || text.isBlank()) {
            log.warn("文档内容为空，跳过切分: sourceId={}", document.getSourceId());
            return new ArrayList<>();
        }

        // 将 overlap 限制在合理范围内
        int effectiveOverlap = Math.max(MIN_OVERLAP, Math.min(MAX_OVERLAP, overlap));

        List<RagChunk> chunks = new ArrayList<>();
        List<Section> sections = splitIntoSections(text, document.getDocType());

        int chunkIndex = 0;
        String pendingContent = "";

        for (Section section : sections) {
            String sectionContent = section.buildContent();

            // 如果单个 section 就超过 chunk 大小，先按段落/句子拆分
            if (sectionContent.length() > chunkSize) {
                List<String> subChunks = splitLongSection(section, chunkSize, effectiveOverlap, pendingContent);
                for (int i = 0; i < subChunks.size(); i++) {
                    String subContent = subChunks.get(i);
                    boolean isLastSubChunk = (i == subChunks.size() - 1);
                    // 非最后一个子块，使用 sectionTitle；最后一个子块归入下一 section
                    String chunkSectionTitle = isLastSubChunk ? null : section.heading;
                    chunks.add(createChunk(document, subContent, chunkIndex++, chunkSectionTitle));
                }
                pendingContent = subChunks.isEmpty() ? "" : subChunks.get(subChunks.size() - 1);
            } else if (pendingContent.length() + sectionContent.length() > chunkSize) {
                // pending 内容加上当前 section 会超出大小，先保存 pending
                if (!pendingContent.isEmpty()) {
                    chunks.add(createChunk(document, pendingContent, chunkIndex++, section.heading));
                }
                pendingContent = sectionContent;
            } else {
                // 可以合并到 pending
                if (!pendingContent.isEmpty()) {
                    pendingContent += "\n\n";
                }
                pendingContent += sectionContent;
            }
        }

        // 保存最后一个 pending chunk
        if (!pendingContent.isEmpty()) {
            chunks.add(createChunk(document, pendingContent, chunkIndex, null));
        }

        log.info("文档切分完成 - sourceId={}, docType={}, chunk数量={}",
                document.getSourceId(), document.getDocType(), chunks.size());
        return chunks;
    }

    /**
     * 按文档结构分割文本为 sections
     *
     * @param text     原始文本
     * @param docType  文档类型，用于决定切分策略
     */
    List<Section> splitIntoSections(String text, String docType) {
        List<Section> sections = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");

        StringBuilder currentBody = new StringBuilder();
        String currentHeading = null;
        int paraIndex = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) {
                continue;
            }

            HeadingInfo headingInfo = detectHeading(para, docType);

            if (headingInfo != null && currentBody.length() > 0) {
                // 遇到新标题，保存上一个 section
                sections.add(new Section(currentHeading, currentBody.toString(), paraIndex));
                currentBody = new StringBuilder();
            }

            if (headingInfo != null) {
                currentHeading = headingInfo.fullHeading;
            } else {
                if (currentBody.length() > 0) {
                    currentBody.append("\n\n");
                }
                currentBody.append(para);
                paraIndex++;
            }
        }

        // 保存最后一个 section
        if (currentBody.length() > 0) {
            sections.add(new Section(currentHeading, currentBody.toString(), paraIndex));
        }

        return sections;
    }

    /**
     * 检测段落是否为结构标题，并返回标题信息。
     *
     * <p>标题检测规则：
     * <ul>
     *   <li>Markdown 标题（# 一级、## 二级 等）：所有文档类型</li>
     *   <li>章节编号（1. 2.1 3、 等）：所有文档类型</li>
     *   <li>中文章节编号（一、二、（一）等）：所有文档类型</li>
     *   <li>条款编号（第X条）：仅当行内无正文内容时才视为标题，否则视为条款内容</li>
     * </ul>
     *
     * @param para    段落内容
     * @param docType 文档类型
     */
    HeadingInfo detectHeading(String para, String docType) {
        Matcher mdMatcher = MARKDOWN_HEADING_PATTERN.matcher(para);
        if (mdMatcher.find()) {
            return new HeadingInfo(mdMatcher.group(2).trim(), 1);
        }

        Matcher numMatcher = NUMBERED_HEADING_PATTERN.matcher(para);
        if (numMatcher.find()) {
            String number = numMatcher.group(1);
            String title = numMatcher.group(2).trim();
            return new HeadingInfo(number + " " + title, 2);
        }

        Matcher cnMatcher = CN_NUMBER_HEADING_PATTERN.matcher(para);
        if (cnMatcher.find()) {
            String number = cnMatcher.group(1);
            String title = cnMatcher.group(2).trim();
            return new HeadingInfo(number + "、" + title, 2);
        }

        // 条款编号（第X条）：仅当行内正文为空时视为标题，用于制度/合同类文档的章节识别
        if ("REGULATION".equals(docType) || "CONTRACT".equals(docType)) {
            Matcher articleMatcher = ARTICLE_PATTERN.matcher(para);
            if (articleMatcher.find()) {
                String articlePart = articleMatcher.group(1);
                // 检查条款编号后面是否有正文（跳过空白后还有内容则不是纯标题）
                String afterArticle = para.substring(articleMatcher.end()).trim();
                // 只有条款编号本身（无正文内容）才视为标题；带正文的条款属于章节内容
                if (afterArticle.isEmpty()) {
                    return new HeadingInfo(articlePart, 3);
                }
            }
        }

        return null;
    }

    /**
     * 拆分超长 section，优先按段落拆分，段落内按句子拆分
     */
    private List<String> splitLongSection(Section section, int chunkSize, int overlap, String pendingContent) {
        List<String> subChunks = new ArrayList<>();

        // 先尝试按段落拆分
        String[] paragraphs = section.text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();
        int sectionStartIndex = 0;

        for (int p = sectionStartIndex; p < paragraphs.length; p++) {
            String para = paragraphs[p].trim();
            if (para.isEmpty()) continue;

            if (currentChunk.length() + para.length() + 2 <= chunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(para);
            } else {
                // 当前段落加入后会超长
                if (currentChunk.length() > 0) {
                    subChunks.add(currentChunk.toString());
                    // overlap：取当前 chunk 末尾 overlap 字符
                    String overlapText = getOverlapText(currentChunk.toString(), overlap);
                    currentChunk = new StringBuilder(overlapText);
                }

                // 如果单个段落本身就超过 chunk 大小，按句子拆分
                if (para.length() > chunkSize) {
                    List<String> sentenceChunks = splitBySentence(para, chunkSize, overlap);
                    for (int i = 0; i < sentenceChunks.size(); i++) {
                        String sentChunk = sentenceChunks.get(i);
                        if (currentChunk.length() + sentChunk.length() <= chunkSize) {
                            if (currentChunk.length() > 0) {
                                currentChunk.append("\n\n");
                            }
                            currentChunk.append(sentChunk);
                        } else {
                            if (currentChunk.length() > 0) {
                                subChunks.add(currentChunk.toString());
                            }
                            currentChunk = new StringBuilder(sentChunk);
                        }
                    }
                } else {
                    if (currentChunk.length() > 0) {
                        currentChunk.append("\n\n");
                    }
                    currentChunk.append(para);
                }
            }
        }

        if (currentChunk.length() > 0) {
            subChunks.add(currentChunk.toString());
        }

        return subChunks;
    }

    /**
     * 按句子边界拆分超长文本
     */
    private List<String> splitBySentence(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        Matcher matcher = SENTENCE_END_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            int endPos = matcher.end();
            String sentence = text.substring(lastEnd, endPos);

            if (current.length() + sentence.length() <= chunkSize) {
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    // overlap 取末尾
                    String overlapText = getOverlapText(current.toString(), overlap);
                    current = new StringBuilder(overlapText);
                }
                current.append(sentence);
            }
            lastEnd = endPos;
        }

        // 处理剩余内容
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (current.length() + remaining.length() <= chunkSize) {
                current.append(remaining);
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                }
                current = new StringBuilder(remaining);
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    /**
     * 获取 overlap 文本（取原文末尾 overlap 字符）
     */
    private String getOverlapText(String text, int overlap) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }

    /**
     * 创建 Chunk 对象
     *
     * @param document     源文档
     * @param content      chunk 内容文本
     * @param chunkIndex   chunk 顺序索引
     * @param sectionTitle 所属章节标题（可为 null，表示继承前一个 chunk 的标题）
     */
    private RagChunk createChunk(RagDocument document, String content, int chunkIndex, String sectionTitle) {
        ChunkMetadata metadata = ChunkMetadata.builder()
                .orgId(document.getOrgId())
                .scene(document.getScene())
                .subScene(document.getSubScene())
                .docType(document.getDocType())
                .sourceId(document.getSourceId())
                .sourceTitle(document.getTitle())
                .chunkId(document.getSourceId() + "-" + chunkIndex)
                .chunkIndex(chunkIndex)
                // sectionTitle 直接取传入值；空则留 null，由检索侧通过 chunkId 推算
                .sectionTitle(sectionTitle)
                .version(document.getVersion())
                .effectiveDate(document.getEffectiveDate())
                .hierarchyLevel(document.getHierarchyLevel())
                .status(document.getStatus())
                .topicTags(document.getTopicTags())
                .sourceOrg(document.getSourceOrg())
                .build();

        return RagChunk.builder()
                .chunkId(metadata.getChunkId())
                .content(content)
                .metadata(metadata)
                .build();
    }

    /**
     * 标题信息
     */
    private static class HeadingInfo {
        String fullHeading;  // 完整标题文本
        int level;           // 标题级别（1=Markdown一级，2=数字编号，3=条款）

        HeadingInfo(String fullHeading, int level) {
            this.fullHeading = fullHeading;
            this.level = level;
        }
    }

    /**
     * 内部类：表示一个文本章节
     */
    private static class Section {
        String heading;   // 章节标题（可为 null，表示无标题段落）
        String text;      // 章节正文内容
        int paraIndex;    // 起始段落索引

        Section(String heading, String text, int paraIndex) {
            this.heading = heading;
            this.text = text;
            this.paraIndex = paraIndex;
        }

        /**
         * 构建章节完整内容（标题 + 正文）
         */
        String buildContent() {
            if (heading != null && !heading.isBlank()) {
                return heading + "\n\n" + text;
            }
            return text;
        }
    }
}
