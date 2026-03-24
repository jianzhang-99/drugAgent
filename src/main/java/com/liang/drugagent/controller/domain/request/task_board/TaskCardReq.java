package com.liang.drugagent.controller.domain.request.task_board;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务卡片请求对象
 * 用于创建和更新任务卡片
 *
 * @author drug-agent-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务卡片请求对象")
public class TaskCardReq {

    @Schema(description = "任务名称")
    @Size(max = 255, message = "任务名称长度不能超过255")
    private String taskName;

    @Schema(description = "任务类型: TENDER_REVIEW/CONTRACT_CHECK/COMPLIANCE_ALERT")
    private String taskType;

    @Schema(description = "场景标识")
    private String scene;

    @Schema(description = "提交人")
    @Size(max = 100, message = "提交人长度不能超过100")
    private String submittedBy;

    @Schema(description = "指派人")
    @Size(max = 100, message = "指派人长度不能超过100")
    private String assignedTo;

    @Schema(description = "优先级 1-10")
    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 10, message = "优先级最大为10")
    private Integer priority;

    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    @Schema(description = "关联文档ID列表")
    private List<String> documentIds;
}
