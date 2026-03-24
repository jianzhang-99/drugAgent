package com.liang.drugagent.scene.tender_review.support.executor;

import com.liang.drugagent.scene.tender_review.model.RuleResult;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;

/**
 * 标书审查规则执行器接口。
 * 每个实现类负责一条或一组同类型规则的命中判断。
 *
 * @author liangjiajian
 */
public interface TenderRuleExecutor {

    /**
     * 执行规则计算。
     *
     * @param data 标书审查结构化输入
     * @return 当前执行器产出的规则命中结果
     */
    RuleResult execute(TenderReviewData data);
}
