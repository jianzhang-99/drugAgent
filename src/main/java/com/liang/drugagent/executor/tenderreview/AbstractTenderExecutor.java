package com.liang.drugagent.executor.tenderreview;

import com.liang.drugagent.domain.tenderreview.Field;
import com.liang.drugagent.domain.tenderreview.RuleEvidence;
import com.liang.drugagent.domain.tenderreview.RuleHit;

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
}
