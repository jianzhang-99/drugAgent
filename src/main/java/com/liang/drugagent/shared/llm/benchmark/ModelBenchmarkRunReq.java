package com.liang.drugagent.shared.llm.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 模型名称（如 "MiniMax-M2.7-highspeed", "qwen-turbo"）
     */
    private String modelName;

    /**
     * 评测使用的 Prompt
     */
    private String prompt;
}
