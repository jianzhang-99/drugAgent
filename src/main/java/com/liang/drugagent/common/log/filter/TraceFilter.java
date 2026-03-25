// 文件路径: src/main/java/com/liang/drugagent/common/log/filter/TraceFilter.java
package com.liang.drugagent.common.log.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求日志过滤器。
 *
 * <p>职责：
 * <ul>
 *   <li>记录请求开始时间</li>
 *   <li>请求结束后打印耗时日志</li>
 *   <li>过滤静态资源，减少无用日志</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class TraceFilter implements Filter {

    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // 过滤静态资源
        String uri = httpRequest.getRequestURI();
        if (isStaticResource(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // 记录开始时间
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        try {
            chain.doFilter(request, response);
        } finally {
            // 记录请求耗时
            Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            if (startTime != null) {
                long costMs = System.currentTimeMillis() - startTime;
                String method = httpRequest.getMethod();
                String queryString = httpRequest.getQueryString();

                log.info("请求完成: {} {} {} 耗时: {}ms",
                        method,
                        uri,
                        queryString != null ? "?" + queryString : "",
                        costMs);
            }
        }
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.endsWith(".css")
                || uri.endsWith(".js")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".ico")
                || uri.startsWith("/doc/")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3/api-docs");
    }
}
