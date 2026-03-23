package com.liang.drugagent.agent.routing;

import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 路由决策结果校验器。
 *
 * <p>负责校验模型输出结果的合法性和有效性，
 * 包括场景是否支持、置信度是否合理等。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Component
public class RouteDecisionValidator {

    private static final double MIN_VALID_CONFIDENCE = 0.0;
    private static final double MAX_VALID_CONFIDENCE = 1.0;

    /**
     * 支持的业务场景集合（排除 UNKNOWN）。
     */
    private static final Set<SceneEnum> VALID_BUSINESS_SCENES = Arrays.stream(SceneEnum.values())
            .filter(s -> s != SceneEnum.UNKNOWN)
            .collect(Collectors.toSet());

    /**
     * 校验决策结果是否有效。
     *
     * @param decision 路由决策对象
     * @return true 如果有效，false 如果无效
     */
    public boolean isValid(WorkflowRouteDecision decision) {
        if (decision == null) {
            log.warn("[RouteDecisionValidator] 决策对象为 null");
            return false;
        }

        // 校验场景是否合法
        if (!isValidScene(decision.getScene())) {
            log.warn("[RouteDecisionValidator] 非法的场景值: {}", decision.getScene());
            return false;
        }

        // 校验置信度范围
        if (!isValidConfidence(decision.getConfidence())) {
            log.warn("[RouteDecisionValidator] 非法的置信度值: {}", decision.getConfidence());
            return false;
        }

        return true;
    }

    /**
     * 校验场景是否为支持的业务场景。
     *
     * @param scene 场景枚举
     * @return true 如果是支持的业务场景
     */
    public boolean isValidScene(SceneEnum scene) {
        return scene != null && VALID_BUSINESS_SCENES.contains(scene);
    }

    /**
     * 校验置信度是否在有效范围内。
     *
     * @param confidence 置信度值
     * @return true 如果在 [0.0, 1.0] 范围内
     */
    public boolean isValidConfidence(Double confidence) {
        if (confidence == null) {
            return false;
        }
        return confidence >= MIN_VALID_CONFIDENCE && confidence <= MAX_VALID_CONFIDENCE;
    }

    /**
     * 检查置信度是否达到采纳阈值。
     *
     * @param confidence 置信度
     * @param threshold  阈值（默认 0.75）
     * @return true 如果置信度 >= 阈值
     */
    public boolean isConfidenceAboveThreshold(Double confidence, double threshold) {
        return confidence != null && confidence >= threshold;
    }

    /**
     * 检查置信度是否低于回退阈值。
     *
     * @param confidence 置信度
     * @param threshold  阈值（默认 0.5）
     * @return true 如果置信度 < 阈值
     */
    public boolean isConfidenceBelowFallback(Double confidence, double threshold) {
        return confidence == null || confidence < threshold;
    }
}
