package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;

import java.util.UUID;

/**
 * 标书审查执行器抽象基类。
 * 提供公共的工具方法以减少冗余代码。
 *
 * @author liangjiajian
 */
public abstract class AbstractTenderExecutor implements TenderRuleExecutor {

    /**
     * 构建通用的 RuleHit 对象基础数据。
     */
    protected RuleHit createBaseHit(String ruleCode, String ruleName, String scopeId, String riskType, String priority,
            String version) {
        RuleHit hit = new RuleHit();
        hit.setHitId(UUID.randomUUID().toString());
        hit.setRuleCode(ruleCode);
        hit.setRuleName(ruleName);
        hit.setScopeId(scopeId);
        hit.setRiskType(riskType);
        hit.setPriority(priority);
        hit.setVersion(version);
        return hit;
    }

    /**
     * 将字段转换为通用的证据对象。
     */
    protected RuleEvidence toEvidence(Field field) {
        if (field == null) {
            return null;
        }
        RuleEvidence evidence = new RuleEvidence();
        evidence.setDocumentId(field.getDocumentId());
        evidence.setFieldId(field.getFieldId());
        evidence.setBlockId(field.getBlockId());
        evidence.setMatchedValue(field.getNormalizedValue());
        evidence.setChapterPath(field.getChapterPath());
        evidence.setAnchor(field.getAnchor());
        return evidence;
    }

    /**
     * 计算编辑距离算法。
     */
    protected int editDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    /**
     * 标准化电话号码，去除非数字字符。
     */
    protected String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }
}
