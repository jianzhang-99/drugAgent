package com.liang.drugagent.service.rag;

import com.liang.drugagent.domain.req.KnowledgeAskReq;
import com.liang.drugagent.domain.resp.KnowledgeAskResp;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一 RAG 服务：混合检索 + 融合排序 + 拒答门控。
 */
@Service
public class KnowledgeRagService {

    private final KnowledgeChunkIndex chunkIndex;
    private final RagPolicyResolver policyResolver;
    private final EmbeddingModel embeddingModel;

    public KnowledgeRagService(KnowledgeChunkIndex chunkIndex,
                               RagPolicyResolver policyResolver,
                               EmbeddingModel embeddingModel) {
        this.chunkIndex = chunkIndex;
        this.policyResolver = policyResolver;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 统一问答入口。
     *
     * 执行顺序：
     * 1) 参数校验
     * 2) 场景策略解析
     * 3) 候选预过滤（orgId + scene/subScene/docType/topic）
     * 4) 混合检索（BM25 + 向量）
     * 5) 融合排序（RRF/线性加权）
     * 6) 门控决策（证据不足/分数不足 -> 拒答或人工复核）
     */
    public KnowledgeAskResp ask(KnowledgeAskReq req, SceneEnum sceneEnum) {
        if (req == null || isBlank(req.getQuestion())) {
            throw new IllegalArgumentException("question 不能为空");
        }
        if (isBlank(req.getOrgId())) {
            throw new IllegalArgumentException("orgId 不能为空");
        }

        RagPolicy policy = policyResolver.resolve(sceneEnum);
        List<KnowledgeChunkIndex.Entry> candidates = preFilter(req, sceneEnum);
        if (candidates.isEmpty()) {
            return noHit("知识库无可用候选，请补充资料或切换检索范围", policy.getRiskLevel());
        }

        Map<String, Double> keywordScores = keywordRetrieve(req.getQuestion(), candidates, policy.getKeywordTopN());
        Map<String, Double> vectorScores = vectorRetrieve(req.getQuestion(), candidates, policy.getVectorTopN());
        List<ScoredChunk> fused = fuse(candidates, keywordScores, vectorScores, policy);

        int topK = req.getTopK() != null && req.getTopK() > 0 ? req.getTopK() : policy.getTopK();
        List<ScoredChunk> selected = fused.stream().limit(topK).toList();

        if (selected.size() < policy.getMinEvidence()) {
            return needHumanReview("命中证据不足，建议人工补充资料后复核", selected, policy.getRiskLevel());
        }
        if (selected.getFirst().score() < policy.getMinScore()) {
            return needHumanReview("命中置信度不足，建议人工复核", selected, policy.getRiskLevel());
        }

        KnowledgeAskResp resp = new KnowledgeAskResp();
        resp.setDecision("ANSWERED");
        resp.setReason("HIT");
        resp.setRiskLevel(policy.getRiskLevel());
        resp.setAnswer(buildAnswer(req.getQuestion(), selected));
        resp.setCitations(toCitations(selected));
        return resp;
    }

    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return new float[0];
        }
        return response.getResults().getFirst().getOutput();
    }

    /**
     * 预过滤策略：
     * - orgId 为硬过滤（避免跨租户串检索）
     * - scene/subScene/docType/topic 为软匹配（降低误杀）
     */
    private List<KnowledgeChunkIndex.Entry> preFilter(KnowledgeAskReq req, SceneEnum sceneEnum) {
        String scene = sceneEnum == null ? blankTo(req.getScene(), "general") : sceneEnum.name().toLowerCase();
        Set<String> queryTags = new HashSet<>(Objects.requireNonNullElseGet(req.getTopicTags(), List::of));

        return chunkIndex.all().stream()
                .filter(e -> req.getOrgId().equals(stringMeta(e, "orgId")))
                .filter(e -> sceneSoftMatch(scene, stringMeta(e, "scene")))
                .filter(e -> subSceneSoftMatch(req.getSubScene(), stringMeta(e, "subScene")))
                .filter(e -> docTypeSoftMatch(req.getDocType(), stringMeta(e, "docType")))
                .filter(e -> topicSoftMatch(queryTags, metaTags(e)))
                .collect(Collectors.toList());
    }

    private boolean sceneSoftMatch(String requestedScene, String candidateScene) {
        if (isBlank(requestedScene) || "general".equalsIgnoreCase(requestedScene)) {
            return true;
        }
        if (isBlank(candidateScene)) {
            return true;
        }
        return requestedScene.equalsIgnoreCase(candidateScene) || "general".equalsIgnoreCase(candidateScene);
    }

    private boolean subSceneSoftMatch(String requested, String candidate) {
        if (isBlank(requested) || "general".equalsIgnoreCase(requested)) {
            return true;
        }
        if (isBlank(candidate)) {
            return true;
        }
        return requested.equalsIgnoreCase(candidate) || "general".equalsIgnoreCase(candidate);
    }

    private boolean docTypeSoftMatch(String requested, String candidate) {
        if (isBlank(requested) || "general".equalsIgnoreCase(requested)) {
            return true;
        }
        if (isBlank(candidate)) {
            return true;
        }
        return requested.equalsIgnoreCase(candidate) || "general".equalsIgnoreCase(candidate);
    }

    private boolean topicSoftMatch(Set<String> requested, List<String> candidateTags) {
        if (requested.isEmpty()) {
            return true;
        }
        if (candidateTags == null || candidateTags.isEmpty()) {
            return true;
        }
        for (String tag : requested) {
            if (candidateTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关键词通道：使用简化 BM25。
     *
     * 说明：返回值会做 0~1 归一化，便于与向量分数融合。
     */
    private Map<String, Double> keywordRetrieve(String question,
                                                List<KnowledgeChunkIndex.Entry> candidates,
                                                int topN) {
        List<String> terms = tokenize(question);
        Map<String, Double> bm25 = chunkIndex.bm25Search(candidates, terms, topN);
        return normalizeScores(bm25);
    }

    /**
     * 向量通道：query 向量与 chunk 向量做余弦相似度。
     *
     * 说明：同样归一化后参与融合。
     */
    private Map<String, Double> vectorRetrieve(String question,
                                               List<KnowledgeChunkIndex.Entry> candidates,
                                               int topN) {
        float[] queryVector = embed(question);
        if (queryVector.length == 0) {
            return Map.of();
        }
        Map<String, Double> scores = new HashMap<>();
        for (KnowledgeChunkIndex.Entry e : candidates) {
            if (e.getVector() == null || e.getVector().length == 0) {
                continue;
            }
            scores.put(e.getChunkId(), cosine(queryVector, e.getVector()));
        }
        Map<String, Double> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return normalizeScores(ranked);
    }

    /**
     * 融合策略：
     * - 默认使用 RRF（更抗分数尺度不一致问题）
     * - 可降级为线性加权融合（便于实验对照）
     */
    private List<ScoredChunk> fuse(List<KnowledgeChunkIndex.Entry> candidates,
                                   Map<String, Double> keyword,
                                   Map<String, Double> vector,
                                   RagPolicy policy) {
        Map<String, KnowledgeChunkIndex.Entry> index = candidates.stream()
                .collect(Collectors.toMap(KnowledgeChunkIndex.Entry::getChunkId, it -> it));

        Set<String> ids = new HashSet<>();
        ids.addAll(keyword.keySet());
        ids.addAll(vector.keySet());

        Map<String, Integer> keywordRank = rankMap(keyword);
        Map<String, Integer> vectorRank = rankMap(vector);

        List<ScoredChunk> fused = new ArrayList<>();
        for (String id : ids) {
            double score;
            if (policy.isUseRrf()) {
                int rrfK = Math.max(policy.getRrfK(), 1);
                double kRrf = keywordRank.containsKey(id) ? 1D / (rrfK + keywordRank.get(id)) : 0D;
                double vRrf = vectorRank.containsKey(id) ? 1D / (rrfK + vectorRank.get(id)) : 0D;
                score = kRrf * policy.getKeywordWeight() + vRrf * policy.getVectorWeight();
            } else {
                double keyScore = keyword.getOrDefault(id, 0.0);
                double vecScore = vector.getOrDefault(id, 0.0);
                score = keyScore * policy.getKeywordWeight() + vecScore * policy.getVectorWeight();
            }

            KnowledgeChunkIndex.Entry entry = index.get(id);
            if (entry != null) {
                fused.add(new ScoredChunk(entry, score));
            }
        }
        fused.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return fused;
    }

    private String buildAnswer(String question, List<ScoredChunk> selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("基于检索证据，针对你的问题“").append(question).append("”，建议如下：\n");
        for (int i = 0; i < selected.size(); i++) {
            ScoredChunk chunk = selected.get(i);
            sb.append(i + 1).append(". ").append(extractSnippet(chunk.entry().getContent())).append("\n");
        }
        sb.append("\n说明：上述结论仅基于命中的知识片段，请结合业务上下文复核。");
        return sb.toString();
    }

    private List<KnowledgeAskResp.Citation> toCitations(List<ScoredChunk> selected) {
        List<KnowledgeAskResp.Citation> citations = new ArrayList<>();
        for (ScoredChunk item : selected) {
            KnowledgeAskResp.Citation citation = new KnowledgeAskResp.Citation();
            citation.setChunkId(stringMeta(item.entry(), "chunkId"));
            citation.setSourceId(stringMeta(item.entry(), "sourceId"));
            citation.setSourceTitle(stringMeta(item.entry(), "sourceTitle"));
            citation.setSnippet(extractSnippet(item.entry().getContent()));
            citation.setScore(item.score());
            citations.add(citation);
        }
        return citations;
    }

    /**
     * 强拒答：完全无命中时直接返回 NO_HIT，避免模型臆测。
     */
    private KnowledgeAskResp noHit(String reason, String riskLevel) {
        KnowledgeAskResp resp = new KnowledgeAskResp();
        resp.setDecision("NO_HIT");
        resp.setReason(reason);
        resp.setRiskLevel(riskLevel);
        resp.setAnswer("未命中足够证据，建议补充资料后由人工复核。");
        return resp;
    }

    /**
     * 弱拒答：有候选但证据不足/置信度不足，降级为人工复核。
     */
    private KnowledgeAskResp needHumanReview(String reason, List<ScoredChunk> selected, String riskLevel) {
        KnowledgeAskResp resp = new KnowledgeAskResp();
        resp.setDecision("NEED_HUMAN_REVIEW");
        resp.setReason(reason);
        resp.setRiskLevel(riskLevel);
        resp.setAnswer("当前命中证据不足或置信度不够，建议人工复核。");
        resp.setCitations(toCitations(selected));
        return resp;
    }

    private String stringMeta(KnowledgeChunkIndex.Entry entry, String key) {
        Object value = entry.getMetadata() == null ? null : entry.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> metaTags(KnowledgeChunkIndex.Entry entry) {
        Object tags = entry.getMetadata() == null ? null : entry.getMetadata().get("topicTags");
        if (tags instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private Map<String, Double> normalizeScores(Map<String, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        double max = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        if (max <= 0D) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>();
        for (Map.Entry<String, Double> entry : raw.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / max);
        }
        return normalized;
    }

    private List<String> tokenize(String question) {
        if (question == null) {
            return List.of();
        }
        String normalized = question.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", " ");
        String[] parts = normalized.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        if (len == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Map<String, Integer> rankMap(Map<String, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<String, Double>> sorted = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            rank.put(sorted.get(i).getKey(), i + 1);
        }
        return rank;
    }

    private String extractSnippet(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankTo(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private record ScoredChunk(KnowledgeChunkIndex.Entry entry, double score) {
    }
}
