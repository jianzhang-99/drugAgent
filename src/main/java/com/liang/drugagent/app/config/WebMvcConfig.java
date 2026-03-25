// 文件路径: src/main/java/com/liang/drugagent/app/config/WebMvcConfig.java
package com.liang.drugagent.app.config;

import com.liang.drugagent.common.log.interceptor.TraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>注册日志拦截器等。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                );
    }
}
