package com.liang.drugagent.shared.llm.benchmark;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型评测结果实体。
 *
 * <p>记录每次模型评测的详细信息，包括响应时间、Token消耗、成功率等指标，
 * 用于评估和比较不同模型的性能表现。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_benchmark_result")
public class ModelBenchmarkResult {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称（如 "MiniMax-M2.7-highspeed", "qwen-turbo"）
     */
    private String modelName;

    /**
     * Provider 名称（MINIMAX, DASHSCOPE）
     */
    private String provider;

    /**
     * 评测使用的 Prompt
     */
    private String prompt;

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
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 评测时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime benchmarkTime;
}
