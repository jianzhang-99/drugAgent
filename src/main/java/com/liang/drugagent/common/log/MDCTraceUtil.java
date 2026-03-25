// 文件路径: src/main/java/com/liang/drugagent/common/log/MDCTraceUtil.java
package com.liang.drugagent.common.log;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * MDC 日志追踪工具类。
 *
 * <p>用于在日志中携带链路追踪信息：
 * <ul>
 *   <li>traceId - 全链路追踪ID</li>
 *   <li>scene/sceneName - 业务场景（英文+中文）</li>
 *   <li>step/stepName - 处理步骤（英文+中文）</li>
 *   <li>costMs - 耗时（毫秒）</li>
 *   <li>userId/sessionId - 用户和会话信息</li>
 * </ul>
 */
public class MDCTraceUtil {

    private MDCTraceUtil() {}

    /** 生成追踪ID。 */
    public static String generateTraceId() {
        return UUID.randomUUID().toString();
    }

    /** 获取或生成追踪ID。 */
    public static String getOrGenerateTraceId() {
        String traceId = MDC.get(LogConstants.MDC_TRACE_ID);
        return (traceId != null && !traceId.isBlank()) ? traceId : generateTraceId();
    }

    /** 设置追踪ID。 */
    public static void putTraceId(String traceId) {
        MDC.put(LogConstants.MDC_TRACE_ID, traceId);
    }

    /** 获取追踪ID。 */
    public static String getTraceId() {
        return MDC.get(LogConstants.MDC_TRACE_ID);
    }

    /** 设置场景（英文码+中文名）。 */
    public static void putScene(String scene, String chineseName) {
        MDC.put(LogConstants.MDC_SCENE, scene);
        if (chineseName != null) {
            MDC.put(LogConstants.MDC_SCENE_NAME, chineseName);
        }
    }

    /** 设置场景（自动获取中文名）。 */
    public static void putScene(LogConstants.Scene scene) {
        putScene(scene.name(), scene.getChineseName());
    }

    /** 获取场景。 */
    public static String getScene() {
        return MDC.get(LogConstants.MDC_SCENE);
    }

    /** 设置步骤（英文码+中文名）。 */
    public static void putStep(String step, String chineseName) {
        MDC.put(LogConstants.MDC_STEP, step);
        if (chineseName != null) {
            MDC.put(LogConstants.MDC_STEP_NAME, chineseName);
        }
    }

    /** 设置步骤（自动获取中文名）。 */
    public static void putStep(LogConstants.Step step) {
        putStep(step.name(), step.getChineseName());
    }

    /** 获取步骤。 */
    public static String getStep() {
        return MDC.get(LogConstants.MDC_STEP);
    }

    /** 设置耗时（毫秒）。 */
    public static void putCostMs(long costMs) {
        MDC.put(LogConstants.MDC_COST_MS, String.valueOf(costMs));
    }

    /** 设置用户ID。 */
    public static void putUserId(String userId) {
        MDC.put(LogConstants.MDC_USER_ID, userId);
    }

    /** 设置会话ID。 */
    public static void putSessionId(String sessionId) {
        MDC.put(LogConstants.MDC_SESSION_ID, sessionId);
    }

    /** 设置上下文（批量）。 */
    public static void setContext(String traceId, String scene, String sceneName,
                                   String step, String stepName, String userId, String sessionId) {
        if (traceId != null) putTraceId(traceId);
        if (scene != null) putScene(scene, sceneName);
        if (step != null) putStep(step, stepName);
        if (userId != null) putUserId(userId);
        if (sessionId != null) putSessionId(sessionId);
    }

    /** 清理所有MDC上下文。 */
    public static void clear() {
        MDC.remove(LogConstants.MDC_TRACE_ID);
        MDC.remove(LogConstants.MDC_SCENE);
        MDC.remove(LogConstants.MDC_SCENE_NAME);
        MDC.remove(LogConstants.MDC_STEP);
        MDC.remove(LogConstants.MDC_STEP_NAME);
        MDC.remove(LogConstants.MDC_COST_MS);
        MDC.remove(LogConstants.MDC_USER_ID);
        MDC.remove(LogConstants.MDC_SESSION_ID);
    }

    /**
     * 带耗时记录的代码块执行。
     *
     * @param operation 操作名称
     * @param supplier 要执行的代码
     * @return 执行结果
     */
    public static <T> T executeWithTimeRecording(String operation, Supplier<T> supplier) {
        long start = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            long cost = System.currentTimeMillis() - start;
            putCostMs(cost);
        }
    }
}
