package com.liang.drugagent.shared.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型信息。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    /**
     * 模型标识（如 minimax, dashscope）。
     */
    private String model;

    /**
     * 显示名称。
     */
    private String name;

    /**
     * 默认模型名称。
     */
    private String defaultModelName;

    /**
     * 是否可用。
     */
    private boolean available;

    /**
     * 是否为默认模型。
     */
    private boolean isDefault;
}
