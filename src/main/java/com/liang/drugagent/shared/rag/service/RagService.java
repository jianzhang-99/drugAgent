package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
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

    public RagService(VectorStore vectorStore, EmbeddingService embeddingService, LlmService llmService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.llmService = llmService;
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
        log.info("[{}] RAG查询开始 - orgId={}, scene={}, question={}",
                traceId, request.getOrgId(), request.getScene(), request.getQuestion());

        // 2. 构建向量检索请求
        SearchRequest searchRequest = buildSearchRequest(request);

        // 3. 执行向量检索
        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents.isEmpty()) {
            log.info("[{}] RAG检索无结果", traceId);
            return RagQueryResponse.noHit("未找到相关知识片段");
        }

        // 4. 转换为内部 chunks 和 citations
        List<RagChunk> chunks = toRagChunks(documents);
        List<RagCitation> citations = toRagCitations(documents);

        log.info("[{}] RAG检索命中 - chunk数量={}", traceId, chunks.size());

        // 5. 如果不需要生成回答，直接返回检索结果
        if (!Boolean.TRUE.equals(request.getNeedGenerateAnswer())) {
            return RagQueryResponse.builder()
                    .decision(RagDecision.ANSWERED)
                    .reason(RagReason.HIT)
                    .citations(citations)
                    .evidenceChunks(chunks)
                    .build();
        }

        // 6. 拼装 Prompt 并调用 LLM
        String prompt = buildPrompt(request.getQuestion(), chunks, citations);
        try {
            String answer = callLlm(prompt, request.getSessionId());
            log.info("[{}] RAG回答生成成功 - 答案长度={}", traceId, answer.length());

            return RagQueryResponse.builder()
                    .decision(RagDecision.ANSWERED)
                    .reason(RagReason.HIT)
                    .answer(answer)
                    .citations(citations)
                    .evidenceChunks(chunks)
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
     * 构建向量检索请求，支持 orgId / scene / subScene / docType / sourceId 多字段过滤
     */
    private SearchRequest buildSearchRequest(RagQueryRequest request) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(request.getQuestion())
                .topK(request.getTopK() != null ? request.getTopK() : 5);

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

        if (!filters.isEmpty()) {
            String filterExpression = String.join(" and ", filters);
            log.debug("向量检索过滤条件: {}", filterExpression);
            builder.filterExpression(filterExpression);
        }

        return builder.build();
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
     */
    private String buildPrompt(String question, List<RagChunk> chunks, List<RagCitation> citations) {
        StringBuilder evidence = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagCitation citation = citations.get(i);
            evidence.append("[").append(i + 1).append("] 来源：")
                    .append(citation.getSourceTitle() != null ? citation.getSourceTitle() : "未知")
                    .append("\n内容：")
                    .append(chunks.get(i).getContent())
                    .append("\n\n");
        }

        return SharedRagPrompt.SYSTEM_PROMPT + "\n\n问题：" + question + "\n\n检索证据：\n" + evidence +
                "\n请基于以上证据回答问题：";
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
}
