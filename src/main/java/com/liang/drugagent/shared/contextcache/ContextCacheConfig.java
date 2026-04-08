package com.liang.drugagent.shared.contextcache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Context Cache 配置。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCacheConfig {

    /**
     * 缓存 ID（由 API 返回）
     */
    private String cacheId;

    /**
     * 缓存内容的长度（token 数）
     */
    private Integer contentLength;

    /**
     * 缓存创建时间（Unix 时间戳）
     */
    private Long createdAt;

    /**
     * 缓存过期时间（Unix 时间戳）
     */
    private Long expiresAt;

    /**
     * 缓存状态：active / expired / deleted
     */
    private String status;

    /**
     * 缓存使用的模型
     */
    private String model;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;
}
