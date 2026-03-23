package com.liang.drugagent.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 统一记录 HTTP 请求链路日志，并为整个请求注入 traceId。
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 执行请求链路追踪过滤。
     *
     * <p>主要流程：
     * <ol>
     *   <li>解析或生成 traceId</li>
     *   <li>将 traceId 放入 MDC 供日志使用</li>
     *   <li>在响应头中返回 traceId</li>
     *   <li>记录请求入口日志</li>
     *   <li>执行过滤链</li>
     *   <li>记录请求出口日志（含耗时）</li>
     * </ol>
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤链
     * @throws ServletException servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        long startTime = System.currentTimeMillis();

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            log.info(">>> [{}] {}?{} | IP: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString() == null ? "" : request.getQueryString(),
                    resolveClientIp(request));
            filterChain.doFilter(request, response);
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            log.info("<<< [{}] {} | HTTP {} | {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost);
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 解析请求的 traceId。
     *
     * <p>优先从请求头 X-Trace-Id 获取，如果不存在则生成新的 UUID。</p>
     *
     * @param request HTTP 请求
     * @return traceId 字符串
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析客户端真实 IP 地址。
     *
     * <p>优先从 X-Forwarded-For 头获取（适用于反向代理场景），
     * 如果不存在则使用请求的远程地址。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
