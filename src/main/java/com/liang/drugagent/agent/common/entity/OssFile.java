package com.liang.drugagent.agent.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * OSS文件实体。
 *
 * <p>对应数据库表 {@code oss_file}，存储上传到COS的文件元信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("oss_file")
public class OssFile {

    /**
     * 文件唯一标识，采用UUID自动生成。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 文件名称（原始文件名）。
     */
    private String fileName;

    /**
     * 文件后缀名（如：pdf、docx）。
     */
    private String fileSuffix;

    /**
     * 文件大小（字节）。
     */
    private Long fileSize;

    /**
     * 文件内容类型（MIME类型）。
     */
    private String contentType;

    /**
     * OSS存储路径（objectKey）。
     */
    private String objectKey;

    /**
     * 文件唯一标识（MD5或SHA256）。
     */
    private String fileHash;

    /**
     * 关联的业务ID（如：会话ID、任务ID）。
     */
    private String bizId;

    /**
     * 业务类型（如：TENDER_REVIEW、CONTRACT_CHECK）。
     */
    private String bizType;

    /**
     * 文件状态：0-待上传，1-上传成功，2-上传失败，3-已删除。
     */
    private Integer status;

    /**
     * 上传者ID。
     */
    private String uploadBy;

    /**
     * 文件访问次数。
     */
    private Integer accessCount;

    /**
     * 软删除标记：0-未删除，1-已删除。
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 文件创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 文件更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 文件过期时间（可选，用于临时文件自动清理）。
     */
    private LocalDateTime expireAt;
}
