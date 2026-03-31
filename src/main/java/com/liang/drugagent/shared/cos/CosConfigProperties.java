package com.liang.drugagent.shared.cos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云COS配置属性。
 *
 * <p>从配置文件读取 {@code tencent.cos.*} 相关配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.cos")
public class CosConfigProperties {

    /**
     * 是否启用COS。
     */
    private boolean enabled = false;

    /**
     * SecretId。
     */
    private String secretId;

    /**
     * SecretKey。
     */
    private String secretKey;

    /**
     * 存储桶名称。
     */
    private String bucketName;

    /**
     * 桶所在区域。
     */
    private String bucketRegion;

    /**
     * CDN加速域名（可选）。
     */
    private String cdnDomain;

    /**
     * 预签名URL过期时间（秒）。
     */
    private int expiresSeconds = 3600;
}
