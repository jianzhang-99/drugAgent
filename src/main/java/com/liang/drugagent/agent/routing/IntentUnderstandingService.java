package com.liang.drugagent.agent.routing;

import com.liang.drugagent.shared.domain.model.WorkflowRouteDecision;

/**
 * 意图理解服务接口。
 *
 * <p>基于 LLM 做意图理解与场景识别，
 * 输出结构化 WorkflowRouteDecision。</p>
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface IntentUnderstandingService {

    /**
     * 基于 LLM 进行意图理解和场景识别。
     *
     * <p>核心流程：
     * <ol>
     *   <li>构建意图理解 prompt</li>
     *   <li>调用 LLM</li>
     *   <li>解析结构化 JSON 输出</li>
     *   <li>验证结果有效性</li>
     * </ol>
     *
     * @param context 意图理解上下文
     * @return 路由决策结果
     * @throws IntentUnderstandingException 理解失败时抛出
     */
    WorkflowRouteDecision understand(IntentUnderstandingContext context) throws IntentUnderstandingException;

    /**
     * 意图理解异常。
     */
    class IntentUnderstandingException extends RuntimeException {
        public IntentUnderstandingException(String message) {
            super(message);
        }

        public IntentUnderstandingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
