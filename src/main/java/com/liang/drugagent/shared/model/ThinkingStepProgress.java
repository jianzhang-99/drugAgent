package com.liang.drugagent.shared.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 思考过程进度更新事件。
 * 用于 SSE 流式推送思考步骤的实时更新。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThinkingStepProgress {

    /**
     * 当前步骤编码
     */
    private String currentCode;

    /**
     * 当前步骤标题
     */
    private String currentTitle;

    /**
     * 当前步骤状态：PROCESSING / COMPLETED / FAILED
     */
    private String currentStatus;

    /**
     * 当前步骤的详细说明
     */
    private String currentDetail;

    /**
     * 已完成的思考步骤列表（累计）
     */
    private List<ThinkingStep> completedSteps;

    /**
     * 当前正在执行的思考步骤
     */
    private ThinkingStep currentStep;

    /**
     * 是否为最终结果
     */
    private boolean finalResult;

    /**
     * 最终结果数据（仅 finalResult=true 时有值）
     */
    private WorkflowResult result;

    /**
     * 会话标题（仅 finalResult=true 时有值），注入到前端用于更新会话标题
     */
    private String sessionTitle;

    /**
     * 文档ID列表（仅 finalResult=true 时有值），追加到前端 sessionFileIds
     */
    private List<String> documentIds;
}
