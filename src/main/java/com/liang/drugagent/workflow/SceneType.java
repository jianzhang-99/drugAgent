package com.liang.drugagent.workflow;

/**
 * Drug Agent 场景枚举。
 *
 * <p>用于描述当前请求最终命中的业务处理场景，
 * 供路由器、工作流注册表和响应结果统一使用。</p>
 * @author liangjiajian
 */
public enum SceneType {

    /**
     * 通用法规问答场景。
     * 适用于法规查询、条款解释、合规依据咨询等轻问答请求。
     */
    GENERAL_QA,

    /**
     * 合规审查场景。
     * 适用于上传材料后的审查、制度核验、文件合规判断等请求。
     */
    COMPLIANCE_REVIEW,

    /**
     * 药品分析场景。
     * 适用于药品用量趋势、异常识别、统计分析和风险提示等请求。
     */
    DRUG_ANALYSIS,

    /**
     * 未知或未识别场景。
     * 当路由器暂时无法判断明确业务意图时，进入该兜底场景。
     */
    UNKNOWN;

    public static SceneType fromHint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SceneType sceneType : values()) {
            if (sceneType.name().equalsIgnoreCase(value)) {
                return sceneType;
            }
        }
        return null;
    }
}
