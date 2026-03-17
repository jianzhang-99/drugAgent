package com.liang.drugagent.tenderreview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseCreateRequest {

    /** 上传文件的文件名列表 */
    private List<String> filenames;

    /** 提交人 */
    private String submittedBy;
}
