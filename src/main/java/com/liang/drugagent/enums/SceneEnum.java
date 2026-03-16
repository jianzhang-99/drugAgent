package com.liang.drugagent.enums;

/**
 * Drug Agent 场景枚举。
 *
 * <p>用于描述当前请求最终命中的业务处理场景，
 * 供路由器、工作流注册表和响应结果统一使用。</p>
 *
 * @author liangjiajian
 */
public enum SceneEnum {

    /**
     * 标书雷同与语义查重场景。
     */
    TENDER_REVIEW,

    /**
     * 合同文件 AI 预审核场景。
     */
    CONTRACT_PRECHECK,

    /**
     * 医疗耗材与药品合规风险预警场景。
     */
    RISK_ALERT,

    /**
     * 未知或未识别场景。
     */
    UNKNOWN;

    public static SceneEnum fromHint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SceneEnum sceneEnum : values()) {
            if (sceneEnum.name().equalsIgnoreCase(value)) {
                return sceneEnum;
            }
        }
        return null;
    }
}
