package com.liang.drugagent.shared.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RAG 文件关联实体。
 *
 * <p>对应数据库表 {@code rag_file}，关联 OSS 文件与向量库 sourceId，
 * 支持文件溯源、按源删除向量、状态管理。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("rag_file")
public class RagFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ossId;

    private String sourceId;

    private String storedName;

    private String originalName;

    private String fileType;

    private BigDecimal fileSize;

    private String orgId;

    private String scene;

    private String docType;

    private Integer chunkCount;

    private Integer status;

    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
