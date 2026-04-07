package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt;
import com.liang.drugagent.shared.llm.LlmRequest;
import com.liang.drugagent.shared.llm.LlmResponse;
import com.liang.drugagent.shared.llm.LlmService;
import com.liang.drugagent.shared.rag.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索服务。
 *
 * <p>负责接收查询请求、执行向量检索、拼装证据上下文、调用 LLM 生成回答。</p>
 */
@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;

    // 检索参数配置
    private static final int DEFAULT_TOPK = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.0;
    private static final double DEFAULT_BM25_WEIGHT = 0.3;

    // 混合检索时从向量库召回的候选数量（应大于 topK 以弥补重排损失）
    private static final int HYBRID_SEARCH_CANDIDATE_MULTIPLIER = 3;

    public RagService(VectorStore vectorStore, EmbeddingService embeddingService,
                      LlmService llmService, HybridSearchService hybridSearchService,
                      RerankService rerankService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
    }

    /**
     * 执行 RAG 查询
     */
    public RagQueryResponse query(RagQueryRequest request) {
        // 1. 参数校验
        if (request.getOrgId() == null || request.getOrgId().isBlank()) {
            log.error("RAG查询失败：orgId 不能为空");
            return RagQueryResponse.builder()
                    .decision(RagDecision.NEED_HUMAN_REVIEW)
                    .reason(RagReason.CONTEXT_MISSING)
                    .answer("查询失败：缺少组织标识（orgId）")
                    .build();
        }

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            log.error("RAG查询失败：question 不能为空");
            return RagQueryResponse.builder()
                    .decision(RagDecision.NO_HIT)
                    .reason(RagReason.NO_CANDIDATE)
                    .answer("查询失败：问题不能为空")
                    .build();
        }

        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] RAG查询开始 - orgId={}, scene={}, question={}, enableHybrid={}, enableRerank={}",
                traceId, request.getOrgId(), request.getScene(), request.getQuestion(),
                request.getEnableHybridSearch(), request.getEnableRerank());

        // 2. 应用场景默认过滤策略
        applySceneDefaultFilters(request);

        // 3. 确定检索参数
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOPK;
        double similarityThreshold = request.getSimilarityThreshold() != null
                ? request.getSimilarityThreshold() : DEFAULT_SIMILARITY_THRESHOLD;

        // 4. 执行检索（向量检索 -> 混合检索 -> 重排）
        List<Document> documents = executeRetrieval(request, topK, traceId);

        // 5. 相似度阈值过滤
        if (similarityThreshold > 0) {
            documents = documents.stream()
                    .filter(doc -> doc.getScore() != null && doc.getScore() >= similarityThreshold)
                    .collect(Collectors.toList());
            log.info("[{}] 相似度阈值过滤后 - 剩余chunk数量={}", traceId, documents.size());
        }

        if (documents.isEmpty()) {
            log.info("[{}] RAG检索无结果", traceId);
            return RagQueryResponse.noHit("未找到相关知识片段");
        }

        // 6. 转换为内部 chunks 和 citations
        List<RagChunk> chunks = toRagChunks(documents);
        List<RagCitation> citations = toRagCitations(documents);

        log.info("[{}] RAG检索命中 - chunk数量={}", traceId, chunks.size());

        // 6. 如果不需要生成回答，直接返回检索结果
        if (!Boolean.TRUE.equals(request.getNeedGenerateAnswer())) {
            return RagQueryResponse.builder()
                    .decision(RagDecision.ANSWERED)
                    .reason(RagReason.HIT)
                    .citations(citations)
                    .evidenceChunks(chunks)
                    .build();
        }

        // 7. 拼装 Prompt 并调用 LLM
        String prompt = buildPrompt(request.getQuestion(), chunks, citations);
        try {
            String answer = callLlm(prompt, request.getSessionId());
            log.info("[{}] RAG回答生成成功 - 答案长度={}", traceId, answer.length());

            // 评估回答质量，LOW_CONFIDENCE 触发人工复核
            boolean needHumanReview = evaluateNeedHumanReview(answer, chunks);

            return RagQueryResponse.builder()
                    .decision(needHumanReview ? RagDecision.NEED_HUMAN_REVIEW : RagDecision.ANSWERED)
                    .reason(needHumanReview ? RagReason.LOW_CONFIDENCE : RagReason.HIT)
                    .answer(answer)
                    .citations(citations)
                    .evidenceChunks(chunks)
                    .needHumanReview(needHumanReview)
                    .build();
        } catch (Exception e) {
            log.error("[{}] RAG回答生成失败", traceId, e);
            return RagQueryResponse.needHumanReview(RagReason.LOW_CONFIDENCE,
                    "检索到相关信息但生成回答失败，请人工确认。检索到的相关片段：" +
                            chunks.stream().map(c -> c.getContent().substring(0, Math.min(100, c.getContent().length())))
                                    .collect(Collectors.joining("；")));
        }
    }

    /**
     * 评估是否需要人工复核
     *
     * <p>当回答质量低或证据不足时，应触发人工复核而非仅做异常兜底。</p>
     */
    private boolean evaluateNeedHumanReview(String answer, List<RagChunk> chunks) {
        // 回答过短
        if (answer == null || answer.length() < 10) {
            return true;
        }
        // 证据片段过少（少于2个）
        if (chunks == null || chunks.size() < 2) {
            return true;
        }
        // 回答中包含不确定表述
        String uncertainPhrases = "不确定|无法确定|不清楚|可能|也许|大概";
        return answer.matches(".*" + uncertainPhrases + ".*");
    }

    /**
     * 应用场景默认过滤策略。
     *
     * <p>根据不同业务场景设置默认过滤条件：
     * - 知识问答：orgId 硬过滤（已在校验中强制），scene 可选
     * - 标书审查知识增强（scene=tender_review）：默认过滤 scene
     * - 风险预警知识增强（scene=risk_warning）：默认过滤 scene</p>
     */
    private void applySceneDefaultFilters(RagQueryRequest request) {
        String scene = request.getScene();

        // 标书审查场景：默认按 scene 过滤
        if ("tender_review".equals(scene)) {
            log.debug("标书审查场景，应用 scene 过滤: {}", scene);
        }
        // 风险预警场景：默认按 scene 过滤
        else if ("risk_warning".equals(scene)) {
            log.debug("风险预警场景，应用 scene 过滤: {}", scene);
        }
        // 知识问答场景：scene 可选，不强制过滤
        else if (scene == null || scene.isBlank()) {
            log.debug("知识问答场景，scene 可选");
        }
    }

    /**
     * 执行检索主链路。
     *
     * <p>支持三种检索模式：
     * 1. 纯向量检索（默认）
     * 2. 混合检索（BM25 + 向量）：启用 enableHybridSearch 时
     * 3. 重排（Relevance + Diversity）：启用 enableRerank 时</p>
     */
    private List<Document> executeRetrieval(RagQueryRequest request, int topK, String traceId) {
        boolean enableHybrid = Boolean.TRUE.equals(request.getEnableHybridSearch());
        boolean enableRerank = Boolean.TRUE.equals(request.getEnableRerank());

        List<Document> documents;

        if (enableHybrid) {
            // 混合检索模式：从向量库召回更多候选，再用 HybridSearchService 合并 BM25 和向量分数
            int candidateK = topK * HYBRID_SEARCH_CANDIDATE_MULTIPLIER;
            log.info("[{}] 混合检索模式 - 召回候选数={}, topK={}", traceId, candidateK, topK);

            SearchRequest searchRequest = buildSearchRequest(request, candidateK);
            List<Document> candidates = vectorStore.similaritySearch(searchRequest);

            if (candidates.isEmpty()) {
                return new ArrayList<>();
            }

            // 使用 HybridSearchService 合并 BM25 和向量分数
            List<RagChunk> hybridChunks = hybridSearchService.hybridSearch(
                    request.getQuestion(), candidates, topK, DEFAULT_BM25_WEIGHT);

            // 将 RagChunk 转回 Document（保留分数）
            documents = hybridChunksToDocuments(hybridChunks);

            log.info("[{}] 混合检索完成 - 候选数={}, 返回数={}",
                    traceId, candidates.size(), documents.size());
        } else {
            // 纯向量检索模式
            SearchRequest searchRequest = buildSearchRequest(request, topK);
            documents = vectorStore.similaritySearch(searchRequest);
            log.info("[{}] 向量检索完成 - 返回数={}", traceId, documents.size());
        }

        if (enableRerank && !documents.isEmpty()) {
            // 重排模式：对检索结果按 Relevance + Diversity 重排
            List<RagChunk> chunks = toRagChunks(documents);
            List<RagChunk> rerankedChunks = rerankService.rerank(request.getQuestion(), chunks, topK);
            documents = hybridChunksToDocuments(rerankedChunks);

            log.info("[{}] 重排完成 - 返回数={}", traceId, documents.size());
        }

        return documents;
    }

    /**
     * 构建向量检索请求，支持自定义 topK
     */
    private SearchRequest buildSearchRequest(RagQueryRequest request, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(request.getQuestion())
                .topK(topK);

        // orgId 为必填硬过滤，其余字段按需追加为 AND 过滤
        List<String> filters = new ArrayList<>();
        if (request.getOrgId() != null && !request.getOrgId().isBlank()) {
            filters.add("orgId == '" + request.getOrgId() + "'");
        }
        if (request.getScene() != null && !request.getScene().isBlank()) {
            filters.add("scene == '" + request.getScene() + "'");
        }
        if (request.getSubScene() != null && !request.getSubScene().isBlank()) {
            filters.add("subScene == '" + request.getSubScene() + "'");
        }
        if (request.getDocType() != null && !request.getDocType().isBlank()) {
            filters.add("docType == '" + request.getDocType() + "'");
        }
        if (request.getSourceId() != null && !request.getSourceId().isBlank()) {
            filters.add("sourceId == '" + request.getSourceId() + "'");
        }

        // topicTags 过滤：支持多标签精确匹配
        if (request.getTopicTags() != null && !request.getTopicTags().isEmpty()) {
            String topicTagsFilter = request.getTopicTags().stream()
                    .map(tag -> "topicTags == '" + tag + "'")
                    .collect(Collectors.joining(" or "));
            filters.add("(" + topicTagsFilter + ")");
        }

        if (!filters.isEmpty()) {
            String filterExpression = String.join(" and ", filters);
            log.debug("向量检索过滤条件: {}", filterExpression);
            builder.filterExpression(filterExpression);
        }

        return builder.build();
    }

    /**
     * 将 RagChunk 列表转回 Document 列表（用于检索结果回传）
     */
    private List<Document> hybridChunksToDocuments(List<RagChunk> chunks) {
        return chunks.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>();
            ChunkMetadata cm = chunk.getMetadata();
            if (cm != null) {
                metadata.put("orgId", cm.getOrgId() != null ? cm.getOrgId() : "");
                metadata.put("scene", cm.getScene() != null ? cm.getScene() : "");
                metadata.put("subScene", cm.getSubScene() != null ? cm.getSubScene() : "");
                metadata.put("docType", cm.getDocType() != null ? cm.getDocType() : "");
                metadata.put("sourceId", cm.getSourceId() != null ? cm.getSourceId() : "");
                metadata.put("sourceTitle", cm.getSourceTitle() != null ? cm.getSourceTitle() : "");
                metadata.put("chunkIndex", cm.getChunkIndex() != null ? cm.getChunkIndex() : 0);
                metadata.put("sectionTitle", cm.getSectionTitle() != null ? cm.getSectionTitle() : "");
                metadata.put("pageNo", cm.getPageNo() != null ? cm.getPageNo() : 0);
                metadata.put("version", cm.getVersion() != null ? cm.getVersion() : "");
            }

            return Document.builder()
                    .id(chunk.getChunkId())
                    .text(chunk.getContent())
                    .metadata(metadata)
                    .score(chunk.getScore() != null ? chunk.getScore().doubleValue() : 0.0)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 将 AI Document 转换为内部 RagChunk
     */
    private List<RagChunk> toRagChunks(List<Document> documents) {
        return documents.stream().map(doc -> {
            Map<String, Object> metadata = doc.getMetadata();
            ChunkMetadata chunkMetadata = ChunkMetadata.builder()
                    .orgId(getStringValue(metadata, "orgId"))
                    .scene(getStringValue(metadata, "scene"))
                    .subScene(getStringValue(metadata, "subScene"))
                    .docType(getStringValue(metadata, "docType"))
                    .sourceId(getStringValue(metadata, "sourceId"))
                    .sourceTitle(getStringValue(metadata, "sourceTitle"))
                    .chunkId(doc.getId())
                    .chunkIndex(getIntValue(metadata, "chunkIndex"))
                    .sectionTitle(getStringValue(metadata, "sectionTitle"))
                    .pageNo(getIntValue(metadata, "pageNo"))
                    .version(getStringValue(metadata, "version"))
                    .effectiveDate(getDateValue(metadata, "effectiveDate"))
                    .hierarchyLevel(getStringValue(metadata, "hierarchyLevel"))
                    .status(getStringValue(metadata, "status"))
                    .sourceOrg(getStringValue(metadata, "sourceOrg"))
                    .build();

            return RagChunk.builder()
                    .chunkId(doc.getId())
                    .content(doc.getText())
                    .metadata(chunkMetadata)
                    .score(doc.getScore() != null ? doc.getScore().floatValue() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 将 AI Document 转换为内部 RagCitation
     */
    private List<RagCitation> toRagCitations(List<Document> documents) {
        return documents.stream().map(doc -> {
            Map<String, Object> metadata = doc.getMetadata();
            return RagCitation.builder()
                    .chunkId(doc.getId())
                    .sourceId(getStringValue(metadata, "sourceId"))
                    .sourceTitle(getStringValue(metadata, "sourceTitle"))
                    .snippet(doc.getText().length() > 200 ?
                            doc.getText().substring(0, 200) + "..." : doc.getText())
                    .score(doc.getScore() != null ? doc.getScore().floatValue() : null)
                    .sectionTitle(getStringValue(metadata, "sectionTitle"))
                    .pageNo(getIntValue(metadata, "pageNo"))
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 拼装 Prompt
     *
     * <p>证据格式：【来源:xxx | chunkId:xxx】
     * 确保每个证据块可独立追溯，与 citations 一一对应。</p>
     */
    private String buildPrompt(String question, List<RagChunk> chunks, List<RagCitation> citations) {
        StringBuilder evidence = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagCitation citation = citations.get(i);
            String sourceTitle = citation.getSourceTitle() != null ? citation.getSourceTitle() : "未知";
            String chunkId = citation.getChunkId() != null ? citation.getChunkId() : chunks.get(i).getChunkId();
            String content = chunks.get(i).getContent();

            evidence.append("【来源:").append(sourceTitle)
                    .append(" | chunkId:").append(chunkId).append("】\n")
                    .append("内容：").append(content)
                    .append("\n\n");
        }

        return SharedRagPrompt.SYSTEM_PROMPT + "\n\n# 问题区\n" + question +
                "\n\n# 证据区\n" + evidence +
                "\n\n# 输出区\n请基于以上证据，按照回答任务类型规范输出回答。回答中每个关键结论必须引用【chunkId:xxx】：";
    }

    /**
     * 调用 LLM 生成回答
     */
    private String callLlm(String prompt, String sessionId) {
        LlmRequest request = LlmRequest.builder()
                .sessionId(sessionId != null ? sessionId : "rag-query")
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .temperature(0.3f)
                .maxTokens(2000)
                .build();

        LlmResponse response = llmService.chatForChat(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return response.getContent();
        }
        throw new RuntimeException("LLM 调用失败: " + response.getErrorMessage());
    }

    private String getStringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private java.time.LocalDate getDateValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDate) {
            return (java.time.LocalDate) value;
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).toLocalDate();
        }
        try {
            return java.time.LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
