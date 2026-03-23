package com.liang.drugagent.dto.tender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 标书案例创建响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TenderCaseCreateResp", description = "创建标书案例响应")
public class CaseCreateResp {

    @Schema(description = "案例ID")
    private String caseId;

    @Schema(description = "文档ID列表")
    private List<String> documentIds;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "提交人")
    private String submittedBy;
}
