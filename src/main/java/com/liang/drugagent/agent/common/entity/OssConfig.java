package com.liang.drugagent.agent.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * OSS配置实体。
 *
 * <p>对应数据库表 {@code oss_config}，存储腾讯云COS的Bucket配置信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oss_config")
public class OssConfig {

    /**
     * 配置唯一标识，采用UUID自动生成。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 存储桶名称。
     */
    private String bucketName;

    /**
     * 桶所在区域。
     */
    private String bucketRegion;

    /**
     * SecretId。
     */
    private String secretId;

    /**
     * SecretKey。
     */
    private String secretKey;

    /**
     * CDN加速域名（可选）。
     */
    private String cdnDomain;

    /**
     * 是否为默认配置：0-否，1-是。
     */
    private Integer isDefault;

    /**
     * 预签名URL过期时间（秒）。
     */
    private Integer expiresSeconds;

    /**
     * 是否启用：0-禁用，1-启用。
     */
    private Integer enabled;

    /**
     * 配置创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 配置更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
