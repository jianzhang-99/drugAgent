package com.liang.drugagent.scene.tender_review.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标书案例文档持久化实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tender_case_document")
public class TenderCaseDocumentEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String caseId;

    private String ossFileId;

    private String fileName;

    private String documentName;

    private String fileType;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
