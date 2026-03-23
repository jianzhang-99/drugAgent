package com.liang.drugagent.repository;

import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocument;

import java.util.List;
import java.util.Optional;

/**
 * 标书案例仓储接口。
 *
 * @author liangjiajian
 */
public interface TenderCaseRepository {

    /**
     * 创建案例。
     */
    TenderCase create(TenderCase tenderCase);

    /**
     * 根据ID获取案例。
     */
    Optional<TenderCase> findById(String caseId);

    /**
     * 列出所有案例。
     */
    List<TenderCase> listAll();

    /**
     * 更新案例。
     */
    boolean update(TenderCase tenderCase);

    /**
     * 根据文档ID获取文档。
     */
    Optional<TenderDocument> findDocumentById(String docId);

    /**
     * 存储文件内容。
     */
    void storeFileContent(String docId, byte[] content);

    /**
     * 获取文件内容。
     */
    Optional<byte[]> getFileContent(String docId);

    /**
     * 执行审查。
     */
    TenderCase executeReview(String caseId);
}
