package com.liang.drugagent.scene.tender_review.support;

import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.support.executor.TenderRuleExecutor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 标书审查规则引擎。
 * 负责统一编排各个规则执行器，并汇总输出完整的命中结果。
 *
 * @author liangjiajian
 */
@Component
public class TenderRuleEngine {

    private final List<TenderRuleExecutor> executors;

    public TenderRuleEngine(List<TenderRuleExecutor> executors) {
        this.executors = executors;
    }

    /**
     * 执行所有已注册的规则执行器。
     *
     * @param data 标书审查结构化输入
     * @return 汇总后的规则命中结果列表
     */
    public List<RuleHit> execute(TenderReviewData data) {
        if (data == null || executors == null || executors.isEmpty()) {
            return List.of();
        }

        List<RuleHit> allHits = new java.util.ArrayList<>();
        for (TenderRuleExecutor executor : executors) {
            List<RuleHit> partialResult = executor.execute(data);
            if (partialResult == null || partialResult.isEmpty()) {
                continue;
            }
            allHits.addAll(partialResult);
        }

        allHits.sort(Comparator.comparing(RuleHit::getWeight, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RuleHit::getRuleCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(RuleHit::getRuleName, Comparator.nullsLast(String::compareTo)));
        return allHits.stream().filter(Objects::nonNull).toList();
    }
}
