package com.liang.drugagent.scene.tender_review.service;

import com.liang.drugagent.scene.tender_review.model.Block;
import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 语义候选召回服务。
 * 负责为 LLM 语义判断召回高质量候选片段对。
 *
 * <p>核心职责：
 * <ul>
 *   <li>章节约束召回：只比较同主题章节（技术方案对技术方案、风险识别对风险识别等）</li>
 *   <li>轻量相似度预筛：使用关键词重叠度过滤低相似度候选对</li>
 *   <li>数量控制：每规则每对文档最多 5 组候选，每组最多 2~4 段核心片段</li>
 * </ul>
 *
 * @author architect
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenderSemanticCandidateService {

    private static final int MAX_CANDIDATE_GROUPS_PER_RULE_PAIR = 5;
    private static final int MIN_SNIPPETS_PER_GROUP = 2;
    private static final int MAX_SNIPPETS_PER_GROUP = 4;
    private static final double MIN_SIMILARITY_THRESHOLD = 0.08;

    /**
     * 召回候选片段对。
     *
     * @param data           标书审查数据
     * @param ruleCode       规则编码
     * @param compareTopic   比对主题（如"技术方案"、"风险识别"）
     * @param leftDocId      左侧文档 ID
     * @param rightDocId     右侧文档 ID
     * @return 候选请求列表
     */
    public List<TenderSemanticJudgeReq> recallCandidates(
            TenderReviewData data,
            String ruleCode,
            String compareTopic,
            String leftDocId,
            String rightDocId) {

        List<CandidateSnippetPair> rawCandidates = new ArrayList<>();

        // 1. 章节约束召回：获取同主题 blocks
        List<Block> leftBlocks = filterBlocksByTopic(data.getBlocks(), leftDocId, compareTopic);
        List<Block> rightBlocks = filterBlocksByTopic(data.getBlocks(), rightDocId, compareTopic);

        // 2. 轻量相似度预筛 + 候选对生成
        for (Block left : leftBlocks) {
            for (Block right : rightBlocks) {
                double similarity = calculateSimilarity(left.getContent(), right.getContent());
                if (similarity >= MIN_SIMILARITY_THRESHOLD) {
                    rawCandidates.add(new CandidateSnippetPair(left, right, similarity));
                }
            }
        }

        // 3. 按相似度排序，取 top N
        rawCandidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        List<CandidateSnippetPair> topCandidates = rawCandidates.stream()
                .limit(MAX_CANDIDATE_GROUPS_PER_RULE_PAIR)
                .collect(Collectors.toList());

        // 4. 构建 TenderSemanticJudgeReq
        String caseId = data.getACase() != null ? data.getACase().getCaseId() : null;
        return buildJudgeRequests(topCandidates, ruleCode, compareTopic, leftDocId, rightDocId, data.getBlocks(), caseId);
    }

    /**
     * 按主题过滤 blocks。
     */
    private List<Block> filterBlocksByTopic(List<Block> blocks, String docId, String compareTopic) {
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedTopic = normalizeTopic(compareTopic);
        return blocks.stream()
                .filter(b -> docId.equals(b.getDocumentId()))
                .filter(b -> isTopicMatch(b.getChapterPath(), normalizedTopic) || isTopicMatch(b.getAnchorChapterPath(), normalizedTopic))
                .collect(Collectors.toList());
    }

    /**
     * 判断章节标题/路径是否匹配主题。
     */
    private boolean isTopicMatch(String text, String normalizedTopic) {
        if (text == null || normalizedTopic == null) {
            return false;
        }
        String lowerText = text.toLowerCase();
        return switch (normalizedTopic) {
            case "技术方案" -> lowerText.contains("技术方案") || lowerText.contains("技术设计") || lowerText.contains("技术应答");
            case "风险识别" -> lowerText.contains("风险识别") || lowerText.contains("风险分析") || lowerText.contains("风险控制");
            case "商务条款" -> lowerText.contains("商务条款") || lowerText.contains("商务响应") || lowerText.contains("合同条款");
            case "服务承诺" -> lowerText.contains("服务承诺") || lowerText.contains("服务水平") || lowerText.contains("售后服务");
            case "实施方法" -> lowerText.contains("实施方法") || lowerText.contains("实施计划") || lowerText.contains("施工方案");
            case "核心团队" -> lowerText.contains("核心团队") || lowerText.contains("人员配置") || lowerText.contains("项目团队");
            default -> true;
        };
    }

    /**
     * 标准化主题名称。
     */
    private String normalizeTopic(String compareTopic) {
        if (compareTopic == null) {
            return "";
        }
        return compareTopic.trim();
    }

    /**
     * 计算相似度（关键词重叠度）。
     */
    private double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isBlank() || text2.isBlank()) {
            return 0.0;
        }
        Set<String> keywords1 = extractKeywords(text1);
        Set<String> keywords2 = extractKeywords(text2);
        double sim = jaccardSimilarity(keywords1, keywords2);
        log.debug("[CandidateService] 相似度计算: keywords1={}个, keywords2={}个, sim={}",
                keywords1.size(), keywords2.size(), String.format("%.3f", sim));
        return sim;
    }

    /**
     * 提取关键词（字符二元组 + 停用词过滤）。
     *
     * <p>中文文本无空格，直接按标点和空格分词会得到单字，无法提取有效语义。
     * 改用字符二元组（bigram）捕获连续字符模式，配合停用词过滤，
     * 可以在无需中文分词库的情况下实现中文文本相似度计算。</p>
     */
    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        // 停用词过滤：常见单字功能词不应计入相似度
        Set<String> stopWords = Set.of(
                "的", "了", "和", "是", "在", "有", "与", "及", "等", "为", "对", "以", "或", "于",
                "着", "过", "被", "把", "将", "向", "往", "经", "由", "而", "则", "乃",
                "之", "其", "此", "那", "哪", "谁", "什么", "怎么", "如何", "是否"
        );
        Set<String> keywords = new HashSet<>();
        // 移除空格和英文/数字后提取字符二元组
        String clean = text.toLowerCase().replaceAll("[\\s0-9a-zA-Z]+", "");
        for (int i = 0; i < clean.length() - 1; i++) {
            String bigram = clean.substring(i, i + 2);
            // 跳过停用词 bigram 和含标点的 bigram
            if (stopWords.contains(bigram)) {
                continue;
            }
            if (isChinesePunctuation(bigram.charAt(0)) || isChinesePunctuation(bigram.charAt(1))) {
                continue;
            }
            keywords.add(bigram);
        }
        return keywords;
    }

    /**
     * 判断单个字符是否为中文标点符号。
     */
    private boolean isChinesePunctuation(char c) {
        return "，。、；：！？\u201c\u201d\u2018\u2019（）【】《》——…".indexOf(c) >= 0;
    }

    /**
     * Jaccard 相似度。
     */
    private double jaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    /**
     * 构建 LLM 裁决请求。
     */
    private List<TenderSemanticJudgeReq> buildJudgeRequests(
            List<CandidateSnippetPair> candidates,
            String ruleCode,
            String compareTopic,
            String leftDocId,
            String rightDocId,
            List<Block> allBlocks,
            String caseId) {

        List<TenderSemanticJudgeReq> requests = new ArrayList<>();
        for (CandidateSnippetPair pair : candidates) {
            // 收集章节内的实际内容段落（非标题块）
            List<String> leftContents = collectChapterContents(pair.leftBlock, allBlocks);
            List<String> rightContents = collectChapterContents(pair.rightBlock, allBlocks);

            List<String> leftSnippets = selectCoreSnippets(
                    leftContents,
                    MIN_SNIPPETS_PER_GROUP,
                    MAX_SNIPPETS_PER_GROUP
            );
            List<String> rightSnippets = selectCoreSnippets(
                    rightContents,
                    MIN_SNIPPETS_PER_GROUP,
                    MAX_SNIPPETS_PER_GROUP
            );

            TenderSemanticJudgeReq req = TenderSemanticJudgeReq.builder()
                    .ruleCode(ruleCode)
                    .scene("TENDER_REVIEW")
                    .caseId(caseId)
                    .leftDocumentId(leftDocId)
                    .rightDocumentId(rightDocId)
                    .compareTopic(compareTopic)
                    .leftSnippets(leftSnippets)
                    .rightSnippets(rightSnippets)
                    .extraContext(Map.of(
                            "leftChapterPath", pair.leftBlock.getChapterPath() != null ? pair.leftBlock.getChapterPath() : "",
                            "rightChapterPath", pair.rightBlock.getChapterPath() != null ? pair.rightBlock.getChapterPath() : "",
                            "similarity", pair.similarity
                    ))
                    .build();
            requests.add(req);
        }
        return requests;
    }

    /**
     * 收集同一章节下的所有内容块（非 HEADING 类型的段落和表格）。
     * 同时包含主标题章节及其子章节下的内容块。
     */
    private List<String> collectChapterContents(Block matchedBlock, List<Block> allBlocks) {
        if (matchedBlock == null || allBlocks == null) {
            return Collections.emptyList();
        }
        String chapterPath = matchedBlock.getChapterPath();
        if (chapterPath == null) {
            return Collections.emptyList();
        }
        String docId = matchedBlock.getDocumentId();
        List<String> contents = new ArrayList<>();
        // 收集同文档同章节或子章节的非 HEADING 块
        for (Block b : allBlocks) {
            if (!docId.equals(b.getDocumentId())) {
                continue;
            }
            String bChapter = b.getChapterPath();
            if (bChapter == null) {
                continue;
            }
            // 相同章节 或 子章节（通过编号层级判断，如同为"三"下属或"3.1"下属）
            boolean sameChapter = chapterPath.equals(bChapter);
            boolean isSubSection = isSubSectionOf(bChapter, chapterPath);
            if ((sameChapter || isSubSection)
                    && !"HEADING".equals(b.getBlockType())
                    && b.getContent() != null
                    && !b.getContent().isBlank()) {
                contents.add(b.getContent());
            }
        }
        // 如果没有找到内容块，则使用匹配块本身的内容（降级）
        if (contents.isEmpty() && matchedBlock.getContent() != null && !matchedBlock.getContent().isBlank()) {
            contents.add(matchedBlock.getContent());
        }
        log.debug("[CandidateService] collectChapterContents: docId={}, chapterPath={}, 找到{}个内容块",
                docId, chapterPath, contents.size());
        return contents;
    }

    /**
     * 判断 subChapter 是否为 parentChapter 的子章节。
     * 通过提取编号前缀来判断（如"3.1"是"三"的下属，或"3.1.1"是"3.1"的下属）。
     * 支持中文数字（"一、二、三..."）与阿拉伯数字（"1、1.1..."）的对应关系。
     */
    private boolean isSubSectionOf(String subChapter, String parentChapter) {
        if (subChapter == null || parentChapter == null) {
            return false;
        }
        // 如果一个章节标题是另一个的前缀，认为是父子章节
        if (subChapter.startsWith(parentChapter) || parentChapter.startsWith(subChapter)) {
            return true;
        }
        // 提取章节编号进行比对（支持中文数字和阿拉伯数字）
        String subPrefix = extractSectionPrefix(subChapter);
        String parentPrefix = extractSectionPrefix(parentChapter);
        if (subPrefix.isEmpty() || parentPrefix.isEmpty()) {
            return false;
        }
        // 检查编号前缀是否匹配
        boolean prefixMatch = subPrefix.startsWith(parentPrefix) || parentPrefix.startsWith(subPrefix);
        if (prefixMatch) {
            return true;
        }
        // 额外检查：中文数字编号与阿拉伯数字编号的对应关系
        // 例如："三" 对应 "3"，"3.1" 是 "三" 的子章节
        String subNormalized = normalizeChineseNumber(subPrefix);
        String parentNormalized = normalizeChineseNumber(parentPrefix);
        if (!subNormalized.equals(subPrefix) || !parentNormalized.equals(parentPrefix)) {
            // 至少有一方被转换过，再次检查匹配
            return subNormalized.startsWith(parentNormalized) || parentNormalized.startsWith(subNormalized);
        }
        return false;
    }

    /**
     * 提取章节编号前缀（如"3.1"来自"3.1 总体技术架构"，"三"来自"三、技术方案"）。
     */
    private String extractSectionPrefix(String chapter) {
        if (chapter == null) {
            return "";
        }
        // 匹配阿拉伯数字编号：3.1, 3.1.1 等
        if (chapter.matches("^[0-9]+(\\.[0-9]+)*\\s.*")) {
            int dotIdx = chapter.indexOf('.');
            if (dotIdx > 0) {
                // 返回主编号+子编号的前缀
                int end = chapter.indexOf(' ', dotIdx);
                return end > 0 ? chapter.substring(0, end) : chapter.substring(0, Math.min(dotIdx + 2, chapter.length()));
            }
            int spaceIdx = chapter.indexOf(' ');
            return spaceIdx > 0 ? chapter.substring(0, spaceIdx) : chapter;
        }
        // 匹配中文编号：三、3、第一节 等
        if (chapter.matches("^[一二三四五六七八九十百]+[、.\\s].*") || chapter.matches("^[0-9]+[、.\\s].*")) {
            int idx = chapter.length();
            for (int i = 0; i < chapter.length(); i++) {
                char c = chapter.charAt(i);
                if (Character.isDigit(c) || "、.".indexOf(c) >= 0) {
                    idx = i;
                    break;
                }
            }
            return chapter.substring(0, idx);
        }
        return "";
    }

    /**
     * 将中文数字编号转换为阿拉伯数字编号。
     * 例如："三" -> "3"，"三.1" -> "3.1"，"第三章" -> "3"
     */
    private String normalizeChineseNumber(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return prefix;
        }
        // 中文数字映射
        Map<Character, String> chineseToDigit = Map.of(
                '一', "1", '二', "2", '三', "3", '四', "4", '五', "5",
                '六', "6", '七', "7", '八', "8", '九', "9", '十', "10"
        );

        StringBuilder result = new StringBuilder();
        for (char c : prefix.toCharArray()) {
            if (chineseToDigit.containsKey(c)) {
                result.append(chineseToDigit.get(c));
            } else if (c == '十') {
                // 十需要特殊处理：十 -> 10，十X -> 1X
                result.append("10");
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 选择核心片段（简单截取）。
     */
    private List<String> selectCoreSnippets(List<String> contents, int minCount, int maxCount) {
        if (contents == null || contents.isEmpty()) {
            return Collections.emptyList();
        }
        int count = Math.min(contents.size(), maxCount);
        return contents.subList(0, count);
    }

    /**
     * 内部类：候选片段对。
     */
    private static class CandidateSnippetPair {
        final Block leftBlock;
        final Block rightBlock;
        final double similarity;

        CandidateSnippetPair(Block leftBlock, Block rightBlock, double similarity) {
            this.leftBlock = leftBlock;
            this.rightBlock = rightBlock;
            this.similarity = similarity;
        }
    }
}
