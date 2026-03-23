package com.liang.drugagent.app.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson JSON 序列化配置类。
 *
 * <p>主要配置 Java 8 时间类型的序列化格式，确保与 JavaScript 时区解析兼容。</p>
 *
 * @author drug-agent
 */
@Configuration
public class JacksonConfig {

    /**
     * 时间格式化器，格式：yyyy-MM-dd'T'HH:mm:ssXXX
     * 例如：2024-01-15T10:30:00+08:00
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 注册 Java 8 时间模块，自定义 LocalDateTime 序列化格式。
     *
     * <p>将 LocalDateTime 转换为带时区偏移量的字符串，
     * 确保前端 JavaScript 可以正确解析。</p>
     *
     * @return 配置好的 JavaTimeModule
     */
    @Bean
    public JavaTimeModule javaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();

        // Serialize LocalDateTime with timezone offset so JavaScript can parse correctly
        module.addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                String formatted = value.atZone(ZoneId.systemDefault()).format(FORMATTER);
                gen.writeString(formatted);
            }
        });

        return module;
    }
}
