// 文件路径: src/main/java/com/liang/drugagent/common/log/annotation/Loggable.java
package com.liang.drugagent.common.log.annotation;

import com.liang.drugagent.common.log.LogConstants;

import java.lang.annotation.*;

/**
 * 日志可注解。
 *
 * <p>用于方法上，自动记录方法调用的入参、耗时、返回值。
 *
 * <p>使用示例：
 * <pre>
 * {@code
 * @Loggable(step = LogConstants.Step.PARSE, desc = "解析投标文件")
 * public void parseDocument(String docId) {
 *     // ...
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {

    /** 步骤（英文码）。 */
    LogConstants.Step step() default LogConstants.Step.EXECUTE;

    /** 步骤中文名（可覆盖自动推断）。 */
    String stepName() default "";

    /** 描述信息。 */
    String desc() default "";

    /** 是否记录入参。 */
    boolean logParams() default true;

    /** 是否记录返回值摘要。 */
    boolean logResult() default true;

    /** 慢方法阈值（毫秒），超过此阈值记录WARN。 */
    long threshold() default 1000;
}
