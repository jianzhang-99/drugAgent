package com.liang.drugagent.shared.rag.entity;

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
     * 文件唯一标识。
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 会话ID，RAG文件可为空。
     */
    private String sessionId;

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 文件后缀（如：pdf、docx）。
     */
    private String fileSuffix;

    /**
     * 文件大小（字节）。
     */
    private Long fileSize;

    /**
     * OSS对象路径。
     */
    private String ossUrl;

    /**
     * 文件类型：1-对话附件，2-RAG知识库。
     */
    private Integer fileType;

    /**
     * 上传状态：0-待上传，1-成功，2-失败，3-已删除。
     */
    private Integer uploadStatus;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
