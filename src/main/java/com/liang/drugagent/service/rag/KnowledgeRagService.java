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
 * 统一 RAG 服务：
 * 1) 做候选过滤
 * 2) 做混合检索（关键词/BM25 + 向量）
 * 3) 做融合排序（RRF 或线性加权）
 * 4) 做门控决策（回答 / 拒答 / 人工复核）
 */
@Service
public class KnowledgeRagService {

    // 内存知识索引（存 chunk 文本、metadata、向量）
    private final KnowledgeChunkIndex chunkIndex;

    // 场景策略解析器（不同场景不同 topK、阈值、权重）
    private final RagPolicyResolver policyResolver;

    // 向量模型（用于 query embedding）
    private final EmbeddingModel embeddingModel;

    public KnowledgeRagService(KnowledgeChunkIndex chunkIndex,
                               RagPolicyResolver policyResolver,
                               EmbeddingModel embeddingModel) {
        // 注入关键词+向量混合检索需要的索引
        this.chunkIndex = chunkIndex;
        // 注入场景化策略
        this.policyResolver = policyResolver;
        // 注入 embedding 能力
        this.embeddingModel = embeddingModel;
    }

    /**
     * RAG 主入口。
     */
    public KnowledgeAskResp ask(KnowledgeAskReq req, SceneEnum sceneEnum) {
        // 1) 参数校验：问题不能为空
        if (req == null || isBlank(req.getQuestion())) {
            throw new IllegalArgumentException("question 不能为空");
        }

        // 2) 参数校验：组织 ID 不能为空（多租户隔离）
        if (isBlank(req.getOrgId())) {
            throw new IllegalArgumentException("orgId 不能为空");
        }

        // 3) 读取场景策略（比如 topK、阈值、权重）
        RagPolicy policy = policyResolver.resolve(sceneEnum);

        // 4) 候选预过滤（先把明显不相关数据排掉）
        List<KnowledgeChunkIndex.Entry> candidates = preFilter(req, sceneEnum);

        // 5) 若一个候选都没有，直接强拒答
        if (candidates.isEmpty()) {
            return noHit("知识库无可用候选，请补充资料或切换检索范围", policy.getRiskLevel());
        }

        // 6) 关键词通道打分（简化 BM25）
        Map<String, Double> keywordScores = keywordRetrieve(req.getQuestion(), candidates, policy.getKeywordTopN());

        // 7) 向量通道打分（cosine）
        Map<String, Double> vectorScores = vectorRetrieve(req.getQuestion(), candidates, policy.getVectorTopN());

        // 8) 融合两个通道（RRF 或线性加权）
        List<ScoredChunk> fused = fuse(candidates, keywordScores, vectorScores, policy);

        // 9) RRF 分数做归一化，确保后续 minScore 处于统一量纲（0~1）
        fused = normalizeFusedScores(fused);

        // 10) 最终截断 topK（优先用户传入，其次策略默认）
        int topK = req.getTopK() != null && req.getTopK() > 0 ? req.getTopK() : policy.getTopK();
        List<ScoredChunk> selected = fused.stream().limit(topK).toList();

        // 10) 门控规则1：证据条数不足 -> 人工复核
        if (selected.size() < policy.getMinEvidence()) {
            return needHumanReview("命中证据不足，建议人工补充资料后复核", selected, policy.getRiskLevel());
        }

        // 11) 门控规则2：最高分不足 -> 人工复核
        if (selected.getFirst().score() < policy.getMinScore()) {
            return needHumanReview("命中置信度不足，建议人工复核", selected, policy.getRiskLevel());
        }

        // 12) 通过门控，构造可回答结果
        KnowledgeAskResp resp = new KnowledgeAskResp();
        // 决策状态：已回答
        resp.setDecision("ANSWERED");
        // 原因标记：命中
        resp.setReason("HIT");
        // 风险级别：由场景策略给出
        resp.setRiskLevel(policy.getRiskLevel());
        // 回答文本：基于证据片段拼接
        resp.setAnswer(buildAnswer(req.getQuestion(), selected));
        // 引用列表：返回每条证据来源
        resp.setCitations(toCitations(selected));
        // 返回结果
        return resp;
    }

    /**
     * 将文本向量化。
     */
    public float[] embed(String text) {
        // 调 embedding 模型
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        // 防空保护
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return new float[0];
        }
        // 取第一条向量输出
        return response.getResults().getFirst().getOutput();
    }

    /**
     * 候选过滤：
     * - orgId：硬过滤
     * - scene/subScene/docType/topic：软过滤
     */
    private List<KnowledgeChunkIndex.Entry> preFilter(KnowledgeAskReq req, SceneEnum sceneEnum) {
        // 如果调用方有 SceneEnum 就用它，否则回退请求中的 scene
        String scene = sceneEnum == null ? blankTo(req.getScene(), "general") : sceneEnum.name().toLowerCase();

        // 请求里的 topic 标签
        Set<String> queryTags = new HashSet<>(Objects.requireNonNullElseGet(req.getTopicTags(), List::of));

        // 过滤链开始
        return chunkIndex.all().stream()
                // 组织强隔离
                .filter(e -> req.getOrgId().equals(stringMeta(e, "orgId")))
                // 场景软匹配
                .filter(e -> sceneSoftMatch(scene, stringMeta(e, "scene")))
                // 子场景软匹配
                .filter(e -> subSceneSoftMatch(req.getSubScene(), stringMeta(e, "subScene")))
                // 文档类型软匹配
                .filter(e -> docTypeSoftMatch(req.getDocType(), stringMeta(e, "docType")))
                // topic 标签软匹配
                .filter(e -> topicSoftMatch(queryTags, metaTags(e)))
                // 收集结果
                .collect(Collectors.toList());
    }

    // scene 软匹配：请求为空或 general 时放行；候选为 general 也放行
    private boolean sceneSoftMatch(String requestedScene, String candidateScene) {
        if (isBlank(requestedScene) || "general".equalsIgnoreCase(requestedScene)) {
            return true;
        }
        if (isBlank(candidateScene)) {
            return true;
        }
        return requestedScene.equalsIgnoreCase(candidateScene) || "general".equalsIgnoreCase(candidateScene);
    }

    // subScene 软匹配
    private boolean subSceneSoftMatch(String requested, String candidate) {
        if (isBlank(requested) || "general".equalsIgnoreCase(requested)) {
            return true;
        }
        if (isBlank(candidate)) {
            return true;
        }
        return requested.equalsIgnoreCase(candidate) || "general".equalsIgnoreCase(candidate);
    }

    // docType 软匹配
    private boolean docTypeSoftMatch(String requested, String candidate) {
        if (isBlank(requested) || "general".equalsIgnoreCase(requested)) {
            return true;
        }
        if (isBlank(candidate)) {
            return true;
        }
        return requested.equalsIgnoreCase(candidate) || "general".equalsIgnoreCase(candidate);
    }

    // topic 标签软匹配：任一命中即可
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
     * 关键词通道检索（BM25）并归一化。
     */
    private Map<String, Double> keywordRetrieve(String question,
                                                List<KnowledgeChunkIndex.Entry> candidates,
                                                int topN) {
        // query 分词
        List<String> terms = tokenize(question);
        // BM25 召回
        Map<String, Double> bm25 = chunkIndex.bm25Search(candidates, terms, topN);
        // 分数归一化
        return normalizeScores(bm25);
    }

    /**
     * 向量通道检索并归一化。
     */
    private Map<String, Double> vectorRetrieve(String question,
                                               List<KnowledgeChunkIndex.Entry> candidates,
                                               int topN) {
        // query 向量化
        float[] queryVector = embed(question);
        // 防空
        if (queryVector.length == 0) {
            return Map.of();
        }

        // 计算每个候选的余弦分
        Map<String, Double> scores = new HashMap<>();
        for (KnowledgeChunkIndex.Entry e : candidates) {
            // 没有向量的候选跳过
            if (e.getVector() == null || e.getVector().length == 0) {
                continue;
            }
            // 计算 cosine
            scores.put(e.getChunkId(), cosine(queryVector, e.getVector()));
        }

        // 排序并截断 topN
        Map<String, Double> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // 分数归一化
        return normalizeScores(ranked);
    }

    /**
     * 融合两路检索结果。
     */
    private List<ScoredChunk> fuse(List<KnowledgeChunkIndex.Entry> candidates,
                                   Map<String, Double> keyword,
                                   Map<String, Double> vector,
                                   RagPolicy policy) {
        // chunkId -> entry 索引
        Map<String, KnowledgeChunkIndex.Entry> index = candidates.stream()
                .collect(Collectors.toMap(KnowledgeChunkIndex.Entry::getChunkId, it -> it));

        // 两路出现过的所有 id（求并集）
        Set<String> ids = new HashSet<>();
        ids.addAll(keyword.keySet());
        ids.addAll(vector.keySet());

        // 转 rank 用于 RRF
        Map<String, Integer> keywordRank = rankMap(keyword);
        Map<String, Integer> vectorRank = rankMap(vector);

        // 融合输出容器
        List<ScoredChunk> fused = new ArrayList<>();

        for (String id : ids) {
            double score;

            // 默认走 RRF
            if (policy.isUseRrf()) {
                // RRF 的平滑参数
                int rrfK = Math.max(policy.getRrfK(), 1);
                // keyword RRF 分
                double kRrf = keywordRank.containsKey(id) ? 1D / (rrfK + keywordRank.get(id)) : 0D;
                // vector RRF 分
                double vRrf = vectorRank.containsKey(id) ? 1D / (rrfK + vectorRank.get(id)) : 0D;
                // 按场景权重融合
                score = kRrf * policy.getKeywordWeight() + vRrf * policy.getVectorWeight();
            } else {
                // 对照模式：线性加权
                double keyScore = keyword.getOrDefault(id, 0.0);
                double vecScore = vector.getOrDefault(id, 0.0);
                score = keyScore * policy.getKeywordWeight() + vecScore * policy.getVectorWeight();
            }

            // 回取 chunk 实体
            KnowledgeChunkIndex.Entry entry = index.get(id);
            if (entry != null) {
                fused.add(new ScoredChunk(entry, score));
            }
        }

        // 最终按融合分降序
        fused.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return fused;
    }

    /**
     * 回答构造（MVP：证据摘录式）。
     */
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

    /**
     * 引用构造：把 chunk 信息标准化给前端。
     */
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
     * 强拒答：完全没有候选。
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
     * 弱拒答：有候选但不够可信。
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

    // 读取 metadata 字段
    private String stringMeta(KnowledgeChunkIndex.Entry entry, String key) {
        Object value = entry.getMetadata() == null ? null : entry.getMetadata().get(key);
        return value == null ? null : String.valueOf(value);
    }

    // 读取 topicTags 字段
    @SuppressWarnings("unchecked")
    private List<String> metaTags(KnowledgeChunkIndex.Entry entry) {
        Object tags = entry.getMetadata() == null ? null : entry.getMetadata().get("topicTags");
        if (tags instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    // 分数归一化到 0~1
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

    // query 分词（轻量）
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

    // 融合结果归一化，修复 RRF 分数量纲与阈值不一致问题。
    private List<ScoredChunk> normalizeFusedScores(List<ScoredChunk> fused) {
        if (fused == null || fused.isEmpty()) {
            return List.of();
        }
        double max = fused.stream().mapToDouble(ScoredChunk::score).max().orElse(0D);
        if (max <= 0D) {
            return fused;
        }
        List<ScoredChunk> normalized = new ArrayList<>(fused.size());
        for (ScoredChunk item : fused) {
            normalized.add(new ScoredChunk(item.entry(), item.score() / max));
        }
        normalized.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return normalized;
    }

    // 余弦相似度
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

    // 打分 map 转 rank map（用于 RRF）
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

    // 提取短摘要片段
    private String extractSnippet(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }

    // 判空工具
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 空值回退工具
    private String blankTo(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    // 融合后结果结构：entry + score
    private record ScoredChunk(KnowledgeChunkIndex.Entry entry, double score) {
    }
}
