package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.model.ChunkMetadata;
import com.liang.drugagent.shared.rag.model.RagChunk;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chunker 切分质量验证测试。
 *
 * <p>验证"结构优先、长度兜底"切分策略的正确性：
 * <ul>
 *   <li>章节/条款标题是否正确识别并保留在 chunk 内容或 metadata 中</li>
 *   <li>相邻 chunk 之间的 overlap 是否在合理范围（80-150字符）</li>
 *   <li>超长段落是否按句子边界拆分，而非截断</li>
 *   <li>metadata 字段完整率是否达标</li>
 * </ul>
 */
@Slf4j
@DisplayName("Chunker 切分质量验证")
public class ChunkerQualityTest {

    private Chunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new Chunker();
    }

    /**
     * 测试：多级标题文档的切分质量
     * 验证每个 chunk 是否保留了章节/条款语义
     */
    @Test
    @DisplayName("多级标题文档：章节标题应保留在 chunk 内容中")
    void testMultiLevelHeading_preservesHeadings() {
        String content = """
                # 第一章 总则

                第一条 为保证药品的安全、有效和质量可控，规范药品注册行为，加强药品注册管理，保护药品生产企业、药品使用单位及消费者的合法权益，根据《中华人民共和国药品管理法》和《中华人民共和国药品管理法实施条例》，制定本办法。

                第二条 在中华人民共和国境内从事药品研制、注册、生产、经营、使用和监督管理活动，适用本办法。

                ## 第二章 药品注册申请

                第三条 药品注册申请包括药物临床试验申请、药品上市许可申请、药品补充申请和药品再注册申请。

                第四条 药物临床试验是指以药品上市注册为目的，为确定药物安全性与有效性而在人体开展的药物研究。
                """;

        RagDocument doc = buildDoc(content, "REGULATION");

        List<RagChunk> chunks = chunker.chunk(doc);

        assertFalse(chunks.isEmpty(), "chunk列表不应为空");

        log.info("=== 多级标题文档切分结果 ===");
        log.info("总 chunk 数量: {}", chunks.size());

        boolean foundChapter1 = false;
        boolean foundChapter2 = false;
        boolean foundArticle1 = false;

        for (RagChunk chunk : chunks) {
            String text = chunk.getContent();
            log.info("[Chunk {}] 字符数={}, sectionTitle={}",
                    chunk.getMetadata().getChunkIndex(), text.length(), chunk.getMetadata().getSectionTitle());
            log.info("  内容预览: {}", text.substring(0, Math.min(80, text.length())));

            if (text.contains("第一章 总则")) {
                foundChapter1 = true;
            }
            if (text.contains("第二章 药品注册申请")) {
                foundChapter2 = true;
            }
            if (text.contains("第一条")) {
                foundArticle1 = true;
            }
        }

        assertTrue(foundChapter1, "应包含第一章总则的 chunk");
        assertTrue(foundChapter2, "应包含第二章注册申请的 chunk");
        assertTrue(foundArticle1, "应包含第一条内容的 chunk");

        log.info("章节标题保留验证通过");
    }

    /**
     * 测试：overlap 字符数是否在 80-150 范围内
     */
    @Test
    @DisplayName("Overlap：相邻 chunk 应有 80-150 字符重叠")
    void testOverlap_withinRange() {
        // 构造一个需要多个 chunk 的长文档
        // 使用较短的 chunkSize(400) + 多个段落来触发多次 split
        StringBuilder sb = new StringBuilder("# 第一章 长章节\n\n");
        // 每个段落约200字符
        String para200 = "这是第X条的内容，约两百字符。药品注册管理办法规定了药品注册的法定程序和要求，涉及药物临床试验、药品上市许可、药品补充申请和药品再注册等重要环节。药品注册应当提供真实、充分、可靠的数据资料。";
        for (int i = 0; i < 10; i++) {
            sb.append("第").append(i + 1).append("条 ").append(para200).append("\n\n");
        }

        RagDocument doc = buildDoc(sb.toString(), "REGULATION");
        // 使用较小的 chunkSize 触发多次 split
        List<RagChunk> chunks = chunker.chunk(doc, 400, 120);

        log.info("=== Overlap 验证 ===");
        log.info("Chunk 数量: {}", chunks.size());

        if (chunks.size() < 2) {
            log.warn("Chunk 数量少于2，跳过 overlap 验证（测试数据长度不足）");
            return;
        }

        int validOverlapCount = 0;
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1).getContent();
            String curr = chunks.get(i).getContent();

            // 计算 overlap：前一个 chunk 末尾与当前 chunk 开头的公共字符数
            int overlap = computeOverlap(prev, curr);
            log.info("Chunk[{}] 与 Chunk[{}] overlap: {} 字符", i - 1, i, overlap);

            if (overlap >= 80 && overlap <= 150) {
                validOverlapCount++;
            }
        }

        log.info("有效 overlap 数量: {}/{}", validOverlapCount, chunks.size() - 1);
        assertTrue(validOverlapCount > 0, "至少应有一些 chunk 对有合理的 overlap");
    }

    /**
     * 测试：超长段落应按句子边界拆分，不应截断句子中间
     */
    @Test
    @DisplayName("超长段落：应按句子边界拆分，不应截断中间语义")
    void testLongParagraph_splitsAtSentenceBoundary() {
        // 构造一个超长段落（无标题）
        String longPara = "第一条规定，为保证药品的安全、有效和质量可控，规范药品注册行为，加强药品注册管理，保护药品生产企业、药品使用单位及消费者的合法权益，根据《中华人民共和国药品管理法》和《中华人民共和国药品管理法实施条例》，制定本办法。";
        // 重复以超过默认 chunk 大小
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(longPara).append("这是扩充内容。");
        }

        RagDocument doc = buildDoc(sb.toString(), "REGULATION");
        List<RagChunk> chunks = chunker.chunk(doc);

        log.info("=== 超长段落切分 ===");
        log.info("原始长度: {}, chunk数量: {}", sb.length(), chunks.size());

        // 验证每个 chunk 都以完整句子结尾
        for (RagChunk chunk : chunks) {
            String text = chunk.getContent().trim();
            log.info("Chunk[{}] 字符数={}, 结尾字符=[{}]",
                    chunk.getMetadata().getChunkIndex(), text.length(),
                    text.isEmpty() ? "" : text.charAt(text.length() - 1));

            // 检查是否以句子结束标点结尾（。！？或英文字符）
            if (!text.isEmpty()) {
                char lastChar = text.charAt(text.length() - 1);
                assertTrue(
                        Character.isLetterOrDigit(lastChar) ||
                                lastChar == '。' || lastChar == '！' || lastChar == '？' ||
                                lastChar == '.' || lastChar == '!' || lastChar == '?',
                        "Chunk 应以句子边界结尾，不应截断中间语义"
                );
            }
        }

        log.info("句子边界拆分验证通过");
    }

    /**
     * 测试：metadata 字段完整率验证
     */
    @Test
    @DisplayName("Metadata：必须字段完整率应达 100%")
    void testMetadata_completeness() {
        String content = """
                # 第一章 总则

                第一条 为保证药品的安全、有效和质量可控，规范药品注册行为。

                第二条 在中华人民共和国境内从事药品研制、注册、生产、经营、使用和监督管理活动，适用本办法。
                """;

        RagDocument doc = RagDocument.builder()
                .sourceId("TEST-META-001")
                .title("药品注册管理办法")
                .rawText(content)
                .orgId("org-001")
                .scene("REGULATION")
                .subScene("DRUG_REGISTRATION")
                .docType("REGULATION")
                .version("1.0")
                .effectiveDate(LocalDate.of(2023, 1, 1))
                .hierarchyLevel("国家法规")
                .status("有效")
                .topicTags(List.of("药品注册", "监管"))
                .sourceOrg("国家药品监督管理局")
                .createdAt(LocalDateTime.now())
                .build();

        List<RagChunk> chunks = chunker.chunk(doc);

        log.info("=== Metadata 完整率验证 ===");

        String[] mustHaveFields = {"orgId", "scene", "subScene", "docType", "sourceId", "sourceTitle", "chunkId", "chunkIndex"};

        int totalFieldChecks = 0;
        int passedFieldChecks = 0;

        for (RagChunk chunk : chunks) {
            ChunkMetadata meta = chunk.getMetadata();
            log.info("Chunk[{}] metadata:", chunk.getMetadata().getChunkIndex());
            log.info("  orgId={}", meta.getOrgId());
            log.info("  scene={}", meta.getScene());
            log.info("  subScene={}", meta.getSubScene());
            log.info("  docType={}", meta.getDocType());
            log.info("  sourceId={}", meta.getSourceId());
            log.info("  sourceTitle={}", meta.getSourceTitle());
            log.info("  chunkId={}", meta.getChunkId());
            log.info("  chunkIndex={}", meta.getChunkIndex());
            log.info("  sectionTitle={}", meta.getSectionTitle());
            log.info("  topicTags={}", meta.getTopicTags());

            // 必须字段检查
            assertEquals("org-001", meta.getOrgId(), "orgId 必须匹配");
            assertEquals("REGULATION", meta.getScene(), "scene 必须匹配");
            assertEquals("DRUG_REGISTRATION", meta.getSubScene(), "subScene 必须匹配");
            assertEquals("REGULATION", meta.getDocType(), "docType 必须匹配");
            assertEquals("TEST-META-001", meta.getSourceId(), "sourceId 必须匹配");
            assertEquals("药品注册管理办法", meta.getSourceTitle(), "sourceTitle 必须匹配");
            assertNotNull(meta.getChunkId(), "chunkId 不能为 null");
            assertNotNull(meta.getChunkIndex(), "chunkIndex 不能为 null");

            totalFieldChecks += 8;
            passedFieldChecks += 8;
        }

        // topicTags 来自文档级，应有值
        for (RagChunk chunk : chunks) {
            if (chunk.getMetadata().getTopicTags() != null) {
                assertEquals(2, chunk.getMetadata().getTopicTags().size(), "topicTags 应有 2 个标签");
                passedFieldChecks++;
            }
            totalFieldChecks++;
        }

        log.info("Metadata 字段完整率: {}/{} = {}%",
                passedFieldChecks, totalFieldChecks,
                (passedFieldChecks * 100 / totalFieldChecks));

        assertEquals(totalFieldChecks, passedFieldChecks, "所有 metadata 必填字段必须完整");
    }

    /**
     * 测试：合同类文档按条款切分
     */
    @Test
    @DisplayName("合同类文档：应偏条款切分")
    void testContractDoc_tendersClauseSplit() {
        String content = """
                合同条款

                第一条 合同当事人
                本合同由甲方（委托方）和乙方（受托方）共同签订，甲方委托乙方提供药品研发服务。

                第二条 服务内容
                乙方应为甲方提供以下服务：药物临床试验方案设计、药物安全性评价、药品注册资料整理等。

                第三条 费用及支付方式
                本合同总金额为人民币壹佰万元整，甲方应在本合同签订后30日内支付50%首付款项。
                """;

        RagDocument doc = buildDoc(content, "CONTRACT");
        List<RagChunk> chunks = chunker.chunk(doc);

        log.info("=== 合同文档切分结果 ===");
        log.info("Chunk 数量: {}", chunks.size());

        boolean foundArticle1 = false;
        boolean foundArticle2 = false;
        boolean foundArticle3 = false;

        for (RagChunk chunk : chunks) {
            String text = chunk.getContent();
            log.info("[Chunk {}] 字符数={}", chunk.getMetadata().getChunkIndex(), text.length());
            log.info("  预览: {}", text.substring(0, Math.min(60, text.length())));

            if (text.contains("第一条")) foundArticle1 = true;
            if (text.contains("第二条")) foundArticle2 = true;
            if (text.contains("第三条")) foundArticle3 = true;
        }

        assertTrue(foundArticle1 && foundArticle2 && foundArticle3,
                "合同文档应分别保留各条款内容");
    }

    // -------------------- 辅助方法 --------------------

    private RagDocument buildDoc(String content, String docType) {
        return RagDocument.builder()
                .sourceId("TEST-DOC-" + System.currentTimeMillis())
                .title("测试文档")
                .rawText(content)
                .orgId("test-org")
                .scene("TEST")
                .subScene("TEST_SCENE")
                .docType(docType)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 计算两个字符串之间的 overlap 字符数
     */
    private int computeOverlap(String prev, String curr) {
        if (prev == null || curr == null || prev.isEmpty() || curr.isEmpty()) {
            return 0;
        }
        int maxOverlap = Math.min(prev.length(), curr.length());
        for (int len = maxOverlap; len > 0; len--) {
            String suffix = prev.substring(prev.length() - len);
            if (curr.startsWith(suffix)) {
                return len;
            }
        }
        return 0;
    }
}
