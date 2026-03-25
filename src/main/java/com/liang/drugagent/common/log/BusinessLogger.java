// 文件路径: src/main/java/com/liang/drugagent/common/log/BusinessLogger.java
package com.liang.drugagent.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务日志门面。
 *
 * <p>提供场景化的日志记录，自动携带 traceId、scene、step 等链路信息。
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * BusinessLogger logger = BusinessLogger.forScene(LogConstants.Scene.TENDER_REVIEW);
 * logger.info(LogConstants.Step.PARSE, "开始解析招标文件");
 * logger.warn(LogConstants.Step.COMPARE, "发现报价不一致");
 * logger.error(LogConstants.Step.REPORT, "报告生成失败", e);
 * }
 * </pre>
 */
public class BusinessLogger {

    private final Logger logger;
    private final String scene;
    private final String sceneName;

    private BusinessLogger(Logger logger, String scene, String sceneName) {
        this.logger = logger;
        this.scene = scene;
        this.sceneName = sceneName;
    }

    /** 根据场景枚举创建 Logger。 */
    public static BusinessLogger forScene(LogConstants.Scene scene) {
        Logger logger = LoggerFactory.getLogger("business." + scene.name());
        return new BusinessLogger(logger, scene.name(), scene.getChineseName());
    }

    /** 根据场景码创建 Logger。 */
    public static BusinessLogger forScene(String sceneCode) {
        Logger logger = LoggerFactory.getLogger("business." + sceneCode);
        String sceneName = getSceneChineseName(sceneCode);
        return new BusinessLogger(logger, sceneCode, sceneName);
    }

    private static String getSceneChineseName(String sceneCode) {
        try {
            LogConstants.Scene scene = LogConstants.Scene.valueOf(sceneCode);
            return scene.getChineseName();
        } catch (Exception e) {
            return sceneCode;
        }
    }

    /** 信息级别日志。 */
    public void info(LogConstants.Step step, String message) {
        putStep(step);
        logger.info("[{}] {}", step.getChineseName(), message);
    }

    /** 信息级别日志。 */
    public void info(String stepCode, String message) {
        putStep(stepCode, null);
        logger.info("[{}] {}", getStepChineseName(stepCode), message);
    }

    /** 警告级别日志。 */
    public void warn(LogConstants.Step step, String message) {
        putStep(step);
        logger.warn("[{}] {}", step.getChineseName(), message);
    }

    /** 警告级别日志。 */
    public void warn(String stepCode, String message) {
        putStep(stepCode, null);
        logger.warn("[{}] {}", getStepChineseName(stepCode), message);
    }

    /** 错误级别日志。 */
    public void error(LogConstants.Step step, String message, Throwable throwable) {
        putStep(step);
        logger.error("[{}] {}", step.getChineseName(), message, throwable);
    }

    /** 错误级别日志。 */
    public void error(String stepCode, String message, Throwable throwable) {
        putStep(stepCode, null);
        logger.error("[{}] {}", getStepChineseName(stepCode), message, throwable);
    }

    /** 调试级别日志。 */
    public void debug(LogConstants.Step step, String message) {
        putStep(step);
        logger.debug("[{}] {}", step.getChineseName(), message);
    }

    private void putStep(LogConstants.Step step) {
        MDCTraceUtil.putStep(step.name(), step.getChineseName());
    }

    private void putStep(String stepCode, String stepName) {
        if (stepName == null) {
            stepName = getStepChineseName(stepCode);
        }
        MDCTraceUtil.putStep(stepCode, stepName);
    }

    private String getStepChineseName(String stepCode) {
        try {
            LogConstants.Step step = LogConstants.Step.valueOf(stepCode);
            return step.getChineseName();
        } catch (Exception e) {
            return stepCode;
        }
    }
}
