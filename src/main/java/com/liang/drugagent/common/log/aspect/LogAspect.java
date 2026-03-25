// 文件路径: src/main/java/com/liang/drugagent/common/log/aspect/LogAspect.java
package com.liang.drugagent.common.log.aspect;

import com.liang.drugagent.common.log.MDCTraceUtil;
import com.liang.drugagent.common.log.annotation.Loggable;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 日志切面。
 *
 * <ul>
 *   <li>方法入参</li>
 *   <li>执行耗时</li>
 *   <li>返回值摘要</li>
 *   <li>异常信息</li>
 * </ul>
 */
@Aspect
@Component
@Slf4j
public class LogAspect {

    private static final int MAX_PARAM_LENGTH = 200;
    private static final int MAX_RESULT_LENGTH = 500;

    @Around("@annotation(loggable)")
    public Object around(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {
        // 设置步骤信息
        String stepName = loggable.stepName();
        if (stepName.isBlank()) {
            stepName = loggable.step().getChineseName();
        }
        MDCTraceUtil.putStep(loggable.step().name(), stepName);

        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;

        try {
            // 记录入参
            if (loggable.logParams()) {
                logParams(joinPoint, loggable.desc());
            }

            result = joinPoint.proceed();
            return result;

        } catch (Throwable t) {
            error = t;
            throw t;

        } finally {
            long costMs = System.currentTimeMillis() - startTime;
            MDCTraceUtil.putCostMs(costMs);

            // 记录结果或异常
            if (error != null) {
                log.error("[{}] {} 异常: {} - {}",
                        stepName,
                        loggable.desc(),
                        error.getClass().getSimpleName(),
                        error.getMessage());
            } else if (costMs > loggable.threshold()) {
                log.warn("[{}] {} 耗时较长: {}ms", stepName, loggable.desc(), costMs);
                if (loggable.logResult()) {
                    logResult(joinPoint, result, stepName, loggable.desc());
                }
            } else {
                log.info("[{}] {} 完成，耗时: {}ms", stepName, loggable.desc(), costMs);
                if (loggable.logResult()) {
                    logResult(joinPoint, result, stepName, loggable.desc());
                }
            }
        }
    }

    private void logParams(ProceedingJoinPoint joinPoint, String desc) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (paramNames == null || args == null || paramNames.length == 0) {
                return;
            }

            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                Object arg = args[i];
                if (arg != null) {
                    String argStr = arg.toString();
                    if (argStr.length() > MAX_PARAM_LENGTH) {
                        argStr = argStr.substring(0, MAX_PARAM_LENGTH) + "...";
                    }
                    params.put(paramNames[i], argStr);
                }
            }

            log.debug("[{}] 入参: {}", desc, params);

        } catch (Exception e) {
            log.debug("[{}] 入参记录失败: {}", desc, e.getMessage());
        }
    }

    private void logResult(ProceedingJoinPoint joinPoint, Object result, String stepName, String desc) {
        try {
            if (result == null) {
                return;
            }

            String resultStr = result.toString();
            if (resultStr.length() > MAX_RESULT_LENGTH) {
                resultStr = resultStr.substring(0, MAX_RESULT_LENGTH) + "...";
            }

            log.debug("[{}] {} 返回: {}", stepName, desc, resultStr);

        } catch (Exception e) {
            log.debug("[{}] {} 返回记录失败: {}", stepName, desc, e.getMessage());
        }
    }
}
