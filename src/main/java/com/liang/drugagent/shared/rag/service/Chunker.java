package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文档 Chunk 切分器。
 *
 * <p>采用"结构优先，长度兜底"的切分方式。</p>
 */
@Slf4j
@Component
public class Chunker {

    /**
     * 默认 chunk 大小（字符数）
     */
    private static final int DEFAULT_CHUNK_SIZE = 800;

    /**
     * 默认 overlap 大小
     */
    private static final int DEFAULT_OVERLAP = 100;

    /**
     * 标题正则（支持 # 标题和数字标题）
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?m)^(#+\\s+.+)$|^(\\d+[.、]\\s+.+)$|^([一二三四五六七八九十]+[、\\s].+)$"
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

        List<RagChunk> chunks = new ArrayList<>();
        List<Section> sections = splitIntoSections(text);

        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();

        for (Section section : sections) {
            if (currentChunk.length() + section.text.length() > chunkSize) {
                // 当前 section 加入后会超出大小，保存当前 chunk 并开始新的
                if (currentChunk.length() > 0) {
                    chunks.add(createChunk(document, currentChunk.toString(), chunkIndex++));
                    // overlap：保留部分内容到下一个 chunk
                    String overlapText = getOverlapText(currentChunk.toString(), overlap);
                    currentChunk = new StringBuilder(overlapText);
                }

                // 如果单个 section 就超过 chunk 大小，按段落继续切
                while (section.text.length() > chunkSize) {
                    String part = section.text.substring(0, chunkSize);
                    chunks.add(createChunk(document, part, chunkIndex++));
                    section.text = section.text.substring(chunkSize);
                    if (overlap > 0 && section.text.length() > overlap) {
                        section.text = section.text.substring(0, overlap);
                    }
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(section.text);
        }

        // 保存最后一个 chunk
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(document, currentChunk.toString(), chunkIndex));
        }

        log.info("文档切分完成 - sourceId={}, chunk数量={}", document.getSourceId(), chunks.size());
        return chunks;
    }

    /**
     * 按结构（标题、段落）分割文本
     */
    private List<Section> splitIntoSections(String text) {
        List<Section> sections = new ArrayList<>();
        String[] paragraphs = text.split("\n\n");

        StringBuilder currentSection = new StringBuilder();
        String currentHeading = null;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) {
                continue;
            }

            boolean isHeading = HEADING_PATTERN.matcher(para).find();

            if (isHeading && currentSection.length() > 0) {
                // 保存上一个 section
                sections.add(new Section(currentHeading, currentSection.toString()));
                currentSection = new StringBuilder();
            }

            if (isHeading) {
                currentHeading = para;
            } else {
                if (currentSection.length() > 0) {
                    currentSection.append("\n\n");
                }
                currentSection.append(para);
            }
        }

        // 保存最后一个 section
        if (currentSection.length() > 0) {
            sections.add(new Section(currentHeading, currentSection.toString()));
        }

        return sections;
    }

    /**
     * 创建 Chunk 对象
     */
    private RagChunk createChunk(RagDocument document, String content, int chunkIndex) {
        ChunkMetadata metadata = ChunkMetadata.builder()
                .orgId(document.getOrgId())
                .scene(document.getScene())
                .subScene(document.getSubScene())
                .docType(document.getDocType())
                .sourceId(document.getSourceId())
                .sourceTitle(document.getTitle())
                .chunkId(document.getSourceId() + "-" + chunkIndex)
                .chunkIndex(chunkIndex)
                .version(document.getVersion())
                .build();

        return RagChunk.builder()
                .chunkId(metadata.getChunkId())
                .content(content)
                .metadata(metadata)
                .build();
    }

    /**
     * 获取 overlap 文本
     */
    private String getOverlapText(String text, int overlap) {
        if (text.length() <= overlap) {
            return text;
        }
        return text.substring(text.length() - overlap);
    }

    /**
     * 内部类：表示一个文本段落或章节
     */
    private static class Section {
        String heading;
        String text;

        Section(String heading, String text) {
            this.heading = heading;
            this.text = text;
        }
    }
}
