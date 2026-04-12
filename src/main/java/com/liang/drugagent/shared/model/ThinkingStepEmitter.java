package com.liang.drugagent.shared.model;

import java.util.function.Consumer;

/**
 * 思考步骤进度发射器接口。
 * 工作流通过此接口在执行过程中推送进度更新。
 *
 * @author liangjiajian
 */
public interface ThinkingStepEmitter {

    /**
     * 发射思考步骤进度更新。
     *
     * @param progress 进度更新事件
     */
    void emit(ThinkingStepProgress progress);

    /**
     * 检查是否已取消。
     *
     * @return true if cancelled
     */
    boolean isCancelled();

    /**
     * 创建一个空发射器（用于同步调用，不推送任何事件）。
     */
    static ThinkingStepEmitter noop() {
        return new ThinkingStepEmitter() {
            @Override
            public void emit(ThinkingStepProgress progress) {
                // 空实现
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }
}
