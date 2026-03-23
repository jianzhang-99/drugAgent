package com.liang.drugagent.core.agent;

import com.liang.drugagent.app.config.RoutingProperties;
import com.liang.drugagent.core.domain.model.WorkflowRouteDecision;
import com.liang.drugagent.core.agent.SceneEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认澄清策略实现。
 *
 * <p>基于置信度阈值和场景类型决定是否需要澄清。
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Slf4j
@Component
public class DefaultClarificationPolicy implements ClarificationPolicy {

    private final RoutingProperties routingProperties;

    public DefaultClarificationPolicy(RoutingProperties routingProperties) {
        this.routingProperties = routingProperties;
    }

    @Override
    public boolean shouldClarify(WorkflowRouteDecision decision) {
        // 已有明确的澄清标记时
        if (decision != null && decision.isRequiresClarification()) {
            log.debug("[ClarificationPolicy] Already marked as requiring clarification");
            return true;
        }

        // 未知场景
        if (decision == null || decision.getScene() == SceneEnum.UNKNOWN) {
            log.debug("[ClarificationPolicy] Unknown scene, requiring clarification");
            return true;
        }

        // 置信度低于阈值
        Double confidence = decision.getConfidence();
        if (confidence == null || confidence < routingProperties.getConfidenceLowThreshold()) {
            log.debug("[ClarificationPolicy] Low confidence ({}) below threshold, requiring clarification",
                    confidence);
            return true;
        }

        // 置信度在中间区间，可以澄清但不是必须
        if (confidence < routingProperties.getConfidenceThreshold()) {
            log.debug("[ClarificationPolicy] Medium confidence ({}), clarification optional",
                    confidence);
            // Phase 2 默认不强制澄清，后续可配置
            return false;
        }

        return false;
    }

    @Override
    public String generateClarificationQuestion(WorkflowRouteDecision decision) {
        if (decision != null && decision.getClarificationQuestion() != null
                && !decision.getClarificationQuestion().isBlank()) {
            return decision.getClarificationQuestion();
        }

        // 基于场景生成默认澄清问题
        Map<String, String> defaultQuestions = new HashMap<>();
        defaultQuestions.put(SceneEnum.TENDER_REVIEW.name(),
                "我理解您想要进行标书相关分析。请问您是想要：\n1）比对两份标书的相似度？\n2）检查标书是否存在围标嫌疑？\n3）其他标书审查需求？");
        defaultQuestions.put(SceneEnum.CONTRACT_PRECHECK.name(),
                "我理解您想要进行合同相关审核。请问您是想要：\n1）审核合同条款的风险？\n2）检查合同条款的合规性？\n3）其他合同相关需求？");
        defaultQuestions.put(SceneEnum.RISK_ALERT.name(),
                "我理解您想要进行风险分析。请问您是想要：\n1）分析药品/耗材的用量趋势？\n2）检测异常数据预警？\n3）生成统计分析报告？");
        defaultQuestions.put(SceneEnum.UNKNOWN.name(),
                "抱歉，我目前无法确定您的具体需求。请告诉我您想要：\n1）比对标书文件\n2）审核合同条款\n3）分析药品/耗材风险数据\n或者直接描述您的具体需求");

        String scene = decision != null && decision.getScene() != null
                ? decision.getScene().name()
                : SceneEnum.UNKNOWN.name();

        String question = defaultQuestions.getOrDefault(scene,
                "抱歉，我无法确定您的具体需求。请描述您想要进行的操作，我会为您匹配合适的处理流程。");

        log.info("[ClarificationPolicy] Generated clarification question for scene={}", scene);
        return question;
    }
}
