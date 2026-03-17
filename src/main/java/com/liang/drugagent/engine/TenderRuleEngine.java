package com.liang.drugagent.engine;

import com.liang.drugagent.domain.tenderreview.RuleHit;
import com.liang.drugagent.domain.tenderreview.RuleResult;
import com.liang.drugagent.domain.tenderreview.TenderReviewData;
import com.liang.drugagent.executor.tenderreview.TenderRuleExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
     * @return 汇总后的规则命中结果
     */
    public RuleResult execute(TenderReviewData data) {
        RuleResult result = new RuleResult();
        if (data == null || executors == null || executors.isEmpty()) {
            return result;
        }

        List<RuleHit> allHits = new ArrayList<>();
        for (TenderRuleExecutor executor : executors) {
            RuleResult partialResult = executor.execute(data);
            if (partialResult == null || partialResult.getHits() == null || partialResult.getHits().isEmpty()) {
                continue;
            }
            allHits.addAll(partialResult.getHits());
        }

        allHits.sort(Comparator.comparing(RuleHit::getWeight, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RuleHit::getRuleCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(RuleHit::getRuleName, Comparator.nullsLast(String::compareTo)));
        result.setHits(allHits.stream().filter(Objects::nonNull).toList());
        return result;
    }
}
