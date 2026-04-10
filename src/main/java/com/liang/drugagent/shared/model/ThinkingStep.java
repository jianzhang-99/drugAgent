package com.liang.drugagent.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端可展示的思考步骤。
 *
 * <p>用于表达业务 Agent 的可解释执行过程，面向展示层提供“做了什么、为什么做、
 * 当前结果如何”的结构化信息，而不是暴露原始链式推理内容。</p>
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingStep {

    /**
     * 步骤编码，供前端做稳定映射。
     */
    private String code;

    /**
     * 步骤标题。
     */
    private String title;

    /**
     * 步骤说明。
     */
    private String detail;

    /**
     * 步骤类型：
     * ROUTE / EXECUTION / CLARIFICATION / ERROR / FINALIZE
     */
    private String type;

    /**
     * 步骤状态：
     * COMPLETED / FAILED / INFO
     */
    private String status;

    /**
     * 展示顺序，从 1 开始。
     */
    private Integer order;
}
