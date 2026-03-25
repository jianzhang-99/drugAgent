// 文件路径: src/main/java/com/liang/drugagent/common/log/interceptor/TraceInterceptor.java
package com.liang.drugagent.common.log.interceptor;

import com.liang.drugagent.common.log.MDCTraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 链路追踪拦截器。
 *
 * <p>在请求入口处：
 * <ul>
 *   <li>从 Header 提取或生成 traceId</li>
 *   <li>设置用户ID和会话ID到MDC</li>
 *   <li>请求结束后清理MDC</li>
 * </ul>
 */
@Slf4j
public class TraceInterceptor implements HandlerInterceptor {

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_SESSION_ID = "X-Session-Id";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        // 生成或获取 traceId
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = MDCTraceUtil.generateTraceId();
        }
        MDCTraceUtil.putTraceId(traceId);

        // 设置用户和会话信息
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            MDCTraceUtil.putUserId(userId);
        }

        String sessionId = request.getHeader(HEADER_SESSION_ID);
        if (sessionId != null && !sessionId.isBlank()) {
            MDCTraceUtil.putSessionId(sessionId);
        }

        // 将 traceId 返回给前端
        response.setHeader(HEADER_TRACE_ID, traceId);

        log.debug("链路追踪初始化: traceId={}, userId={}, sessionId={}",
                traceId, userId, sessionId);

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                               @NonNull HttpServletResponse response,
                               @NonNull Object handler,
                               Exception ex) {

        // 记录异常
        if (ex != null) {
            log.error("请求异常: traceId={}, error={}",
                    MDCTraceUtil.getTraceId(),
                    ex.getMessage());
        }

        // 清理 MDC（防止线程复用导致数据泄漏）
        MDCTraceUtil.clear();
    }
}
