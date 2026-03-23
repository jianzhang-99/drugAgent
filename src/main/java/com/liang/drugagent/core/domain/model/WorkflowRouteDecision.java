package com.liang.drugagent.core.domain.model;

import com.liang.drugagent.core.agent.SceneEnum;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 工作流路由决策结果。
 *
 * <p>封装路由器产生的完整决策信息，包括目标场景、决策来源、
 * 置信度以及需要澄清时的追问内容。</p>
 *
 * @author liangjiajian
 */
@Getter
@Builder
public class WorkflowRouteDecision {

    /**
     * 路由到的目标场景。
     */
    private SceneEnum scene;

    /**
     * 决策来源/依据。
     * <p>取值范围：</p>
     * <ul>
     *   <li>{@code sceneHint} - 前端或上游显式指定的场景</li>
     *   <li>{@code rule} - 规则引擎判断</li>
     *   <li>{@code llm} - LLM 推理判断</li>
     *   <li>{@code fallback} - 兜底默认</li>
     * </ul>
     */
    private String source;

    /**
     * 路由原因的简要描述。
     */
    private String reason;

    /**
     * 置信度，范围 0.0 ~ 1.0。
     */
    private Double confidence;

    /**
     * 是否需要向用户澄清意图。
     */
    private boolean requiresClarification;

    /**
     * 澄清问题（当 requiresClarification 为 true 时）。
     */
    private String clarificationQuestion;

    /**
     * 原始模型输出或其他未结构化的辅助信息。
     */
    private Map<String, Object> raw;

}
