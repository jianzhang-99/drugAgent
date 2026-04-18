package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.CompareScope;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 元数据聚集性检测执行器（W-M7）。
 *
 * <p>检测不同投标人的文档是否具有高度相似的文件指纹元数据，
 * 如相同的作者、相近的创建时间、相同的应用程序等。
 * 正常情况下，不同投标人独立制作的文档不应具有相同的元数据特征。</p>
 *
 * <p>判断逻辑：
 * <ul>
 *   <li>作者相同（Author 字段完全相同）</li>
 *   <li>创建时间相近（CreateDate 相差不超过5分钟，且秒级差异）</li>
 *   <li>应用程序相同（Application 字段完全相同）</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Component
public class MetadataHomologyExecutor extends AbstractTenderExecutor {

    /** 规则编码。 */
    private static final String RULE_CODE = "W-M7";
    /** 规则名称。 */
    private static final String RULE_NAME = "元数据聚集性";
    /** 风险类型。 */
    private static final String RISK_TYPE = "collusion";
    /** 规则优先级。 */
    private static final String PRIORITY = "HIGH";
    /** 规则版本。 */
    private static final String VERSION = "v1";
    /** 创建时间最大允许差异（分钟）。 */
    private static final int MAX_CREATE_TIME_DIFF_MINUTES = 5;

    @Override
    public List<RuleHit> execute(TenderReviewData data) {
        if (data == null || data.getCompareScopes() == null || data.getDocuments() == null) {
            return List.of();
        }

        List<RuleHit> hits = new ArrayList<>();
        for (CompareScope scope : data.getCompareScopes()) {
            hits.addAll(detectInScope(scope, data.getDocuments()));
        }
        return hits;
    }

    private List<RuleHit> detectInScope(CompareScope scope, List<TenderDocument> documents) {
        if (scope == null || scope.getDocumentIds() == null || scope.getDocumentIds().size() < 2) {
            return List.of();
        }

        // 按文档 ID 索引文档
        List<TenderDocument> scopeDocs = documents.stream()
                .filter(Objects::nonNull)
                .filter(doc -> scope.getDocumentIds().contains(doc.getDocumentId()))
                .toList();

        List<RuleHit> hits = new ArrayList<>();

        // 两两比对文档元数据
        for (int i = 0; i < scopeDocs.size(); i++) {
            for (int j = i + 1; j < scopeDocs.size(); j++) {
                TenderDocument left = scopeDocs.get(i);
                TenderDocument right = scopeDocs.get(j);

                MetadataMatchResult matchResult = checkMetadataSimilarity(left, right);
                if (matchResult.isMatched()) {
                    hits.add(buildHit(scope, left, right, matchResult));
                }
            }
        }

        return hits;
    }

    /**
     * 检查两份文档的元数据是否相似。
     */
    private MetadataMatchResult checkMetadataSimilarity(TenderDocument left, TenderDocument right) {
        boolean authorMatch = isNonBlankAndEqual(left.getAuthor(), right.getAuthor());
        boolean applicationMatch = isNonBlankAndEqual(left.getApplication(), right.getApplication());
        boolean createTimeMatch = isCreateTimeSimilar(left.getCreateDate(), right.getCreateDate());

        String matchedFields = buildMatchedFieldsDescription(authorMatch, applicationMatch, createTimeMatch,
                left.getAuthor(), right.getAuthor(),
                left.getApplication(), right.getApplication(),
                left.getCreateDate(), right.getCreateDate());

        // 计算权重
        int matchCount = (authorMatch ? 1 : 0) + (applicationMatch ? 1 : 0) + (createTimeMatch ? 1 : 0);
        int weight = 0;
        String riskLevel = "MEDIUM";

        if (matchCount >= 3) {
            weight = 95;
            riskLevel = "HIGH";
        } else if (matchCount == 2) {
            // 作者+时间 或 作者+应用
            if (authorMatch && (createTimeMatch || applicationMatch)) {
                weight = 85;
                riskLevel = "HIGH";
            } else {
                weight = 70;
            }
        } else if (matchCount == 1) {
            if (authorMatch || createTimeMatch) {
                weight = 50;
            } else {
                weight = 30;
            }
        }

        boolean isMatched = matchCount >= 2; // 至少两个字段匹配才判定为异常
        return new MetadataMatchResult(isMatched, matchedFields, weight, riskLevel, authorMatch, applicationMatch, createTimeMatch);
    }

    /**
     * 判断两个非空字符串是否相等。
     */
    private boolean isNonBlankAndEqual(String s1, String s2) {
        if (s1 == null || s1.isBlank() || s2 == null || s2.isBlank()) {
            return false;
        }
        return s1.equals(s2);
    }

    /**
     * 判断两个创建时间是否相近（相差不超过指定分钟数）。
     */
    private boolean isCreateTimeSimilar(String time1, String time2) {
        if (time1 == null || time1.isBlank() || time2 == null || time2.isBlank()) {
            return false;
        }

        try {
            // 时间格式: "2024-03-18 09:14:22" 或 "2024-03-18T09:14:22"
            String normalizedTime1 = time1.replace(" ", "T");
            String normalizedTime2 = time2.replace(" ", "T");

            // 提取日期和时间部分
            String[] parts1 = normalizedTime1.split("[T\\s]");
            String[] parts2 = normalizedTime2.split("[T\\s]");

            if (parts1.length < 2 || parts2.length < 2) {
                return false;
            }

            // 比较日期部分（去掉秒）
            String dateTime1 = parts1[0] + " " + parts1[1].substring(0, 5);
            String dateTime2 = parts2[0] + " " + parts2[1].substring(0, 5);

            if (!dateTime1.equals(dateTime2)) {
                return false;
            }

            // 检查秒级是否相近（差异在 MAX_CREATE_TIME_DIFF_MINUTES 分钟内）
            // 时间格式: HH:mm:ss
            String[] timeParts1 = parts1[1].split(":");
            String[] timeParts2 = parts2[1].split(":");

            if (timeParts1.length < 3 || timeParts2.length < 3) {
                return false;
            }

            int seconds1 = Integer.parseInt(timeParts1[0]) * 3600 + Integer.parseInt(timeParts1[1]) * 60 + Integer.parseInt(timeParts1[2]);
            int seconds2 = Integer.parseInt(timeParts2[0]) * 3600 + Integer.parseInt(timeParts2[1]) * 60 + Integer.parseInt(timeParts2[2]);

            int diffSeconds = Math.abs(seconds1 - seconds2);
            int maxDiffSeconds = MAX_CREATE_TIME_DIFF_MINUTES * 60;

            return diffSeconds <= maxDiffSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建匹配字段的描述。
     */
    private String buildMatchedFieldsDescription(boolean authorMatch, boolean applicationMatch, boolean createTimeMatch,
                                                 String author1, String author2,
                                                 String app1, String app2,
                                                 String time1, String time2) {
        List<String> matches = new ArrayList<>();

        if (authorMatch) {
            matches.add("作者相同: " + author1);
        }
        if (applicationMatch) {
            matches.add("应用程序相同: " + app1);
        }
        if (createTimeMatch) {
            matches.add("创建时间相近: " + time1 + " vs " + time2);
        }

        return String.join("; ", matches);
    }

    private RuleHit buildHit(CompareScope scope, TenderDocument left, TenderDocument right, MetadataMatchResult result) {
        RuleHit hit = createBaseHit(RULE_CODE, RULE_NAME, scope.getScopeId(), RISK_TYPE, result.riskLevel(), VERSION);
        hit.setWeight(result.weight());
        hit.setMatchedValue(result.matchedFields());

        hit.setTriggerSummary(String.format("文档 %s 与 %s 存在元数据聚集性异常（匹配字段: %s）。" +
                        "不同投标人独立制作的文档，其文件元数据（作者、创建时间、应用程序）不应高度相似。",
                left.getDocumentId(), right.getDocumentId(), result.matchedFields()));

        hit.setDocumentIds(List.of(left.getDocumentId(), right.getDocumentId()));

        // 构建证据
        List<RuleEvidence> evidences = new ArrayList<>();
        evidences.add(buildMetadataEvidence(left, "left"));
        evidences.add(buildMetadataEvidence(right, "right"));
        hit.setEvidences(evidences);

        return hit;
    }

    private RuleEvidence buildMetadataEvidence(TenderDocument doc, String side) {
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(doc.getDocumentId());
        evidence.setMatchedValue(String.format("[%s] Author: %s, CreateDate: %s, Application: %s",
                side,
                doc.getAuthor() != null ? doc.getAuthor() : "N/A",
                doc.getCreateDate() != null ? doc.getCreateDate() : "N/A",
                doc.getApplication() != null ? doc.getApplication() : "N/A"));
        evidence.setOriginalValue(doc.getMetadataRaw());
        evidence.setChapterPath("文件指纹元数据");
        return evidence;
    }

    /** 元数据匹配结果。 */
    private record MetadataMatchResult(
            boolean isMatched,
            String matchedFields,
            int weight,
            String riskLevel,
            boolean authorMatch,
            boolean applicationMatch,
            boolean createTimeMatch
    ) {}
}
