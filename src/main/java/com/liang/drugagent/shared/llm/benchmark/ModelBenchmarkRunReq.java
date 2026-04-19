package com.liang.drugagent.shared.llm.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单模型评测请求。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelBenchmarkRunReq {

    /**
     * 模型名称（单模型评测时使用）
     */
    private String modelName;

    /**
     * 评测使用的 Prompt
     */
    private String prompt;

    /**
     * 指定评测模型列表（批量评测时使用，优先级高于modelName）
     */
    private List<String> modelNames;
}
