package com.liang.drugagent.dto.tender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务执行响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TaskExecuteResp", description = "任务执行响应")
public class TaskExecuteResp {

    @Schema(description = "案例ID")
    private String caseId;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "风险等级")
    private String riskLevel;

    @Schema(description = "综合评分")
    private Integer score;

    @Schema(description = "审查时间")
    private String reviewTime;
}
