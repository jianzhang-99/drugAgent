package com.liang.drugagent.shared.llm.benchmark;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型评测响应。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelBenchmarkResp {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * Provider 名称
     */
    private String provider;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTimeMs;

    /**
     * Token 消耗
     */
    private Integer tokensUsed;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 评测时间
     */
    private LocalDateTime benchmarkTime;
}
