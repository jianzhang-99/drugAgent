// 文件路径: src/main/java/com/liang/drugagent/common/log/LogConstants.java
package com.liang.drugagent.common.log;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日志常量定义。
 */
public class LogConstants {

    /** MDC Key 常量 */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SCENE = "scene";
    public static final String MDC_SCENE_NAME = "sceneName";
    public static final String MDC_STEP = "step";
    public static final String MDC_STEP_NAME = "stepName";
    public static final String MDC_COST_MS = "costMs";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_SESSION_ID = "sessionId";

    /** 场景枚举 */
    @Getter
    @AllArgsConstructor
    public enum Scene {
        TENDER_REVIEW("招标审查"),
        CONTRACT("合同审查"),
        RISK("风险预警"),
        UNKNOWN("未知场景");

        private final String chineseName;
    }

    /** 步骤枚举 */
    @Getter
    @AllArgsConstructor
    public enum Step {
        ROUTE("意图识别"),
        INTENT("意图理解"),
        PARSE("文档解析"),
        EXTRACT("数据提取"),
        COMPARE("比对分析"),
        REPORT("报告生成"),
        KNOWLEDGE("知识检索"),
        EXECUTE("执行");

        private final String chineseName;
    }
}
