package com.liang.drugagent.dto.tender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 标书案例创建请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TenderCaseCreateReq", description = "创建标书案例请求")
public class CaseCreateReq {

    @Schema(description = "文件名列表")
    private List<String> filenames;

    @Schema(description = "提交人", defaultValue = "anonymous")
    private String submittedBy;
}
