package com.liang.drugagent.controller.request.task_board;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务卡片查询请求对象
 * 用于任务看板列表筛选和排序
 *
 * @author drug-agent-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务卡片查询请求对象")
public class TaskCardQueryReq {

    @Schema(description = "页码，从1开始")
    @Builder.Default
    private Integer page = 1;

    @Schema(description = "每页数量")
    @Builder.Default
    private Integer pageSize = 20;

    @Schema(description = "任务状态筛选")
    private String status;

    @Schema(description = "任务类型筛选")
    private String taskType;

    @Schema(description = "场景筛选")
    private String scene;

    @Schema(description = "风险等级筛选")
    private String riskLevel;

    @Schema(description = "提交人筛选")
    private String submittedBy;

    @Schema(description = "指派人筛选")
    private String assignedTo;

    @Schema(description = "优先级筛选（1-10）")
    private Integer priority;

    @Schema(description = "是否只看未处理的")
    private Boolean unhandledOnly;

    @Schema(description = "是否只看高风险的")
    private Boolean highRiskOnly;

    @Schema(description = "是否只看即将到期的（3天内）")
    private Boolean deadlineSoon;

    @Schema(description = "关键词搜索（任务名称/摘要）")
    private String keyword;

    @Schema(description = "排序字段: createdAt/updatedAt/priority/riskLevel/score")
    @Builder.Default
    private String sortField = "createdAt";

    @Schema(description = "排序方向: asc/desc")
    @Builder.Default
    private String sortOrder = "desc";
}
