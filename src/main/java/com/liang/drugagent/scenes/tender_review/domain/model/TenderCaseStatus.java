package com.liang.drugagent.scenes.tender_review.domain.model;

/**
 * 标书审查任务与文档处理状态枚举。
 *
 * <p>描述标书审查任务的完整生命周期状态：
 * <ul>
 *   <li>PENDING: 任务已创建，等待处理</li>
 *   <li>PARSING: 正在解析文档</li>
 *   <li>PARSED: 文档解析完成</li>
 *   <li>RUNNING: 审查任务执行中</li>
 *   <li>COMPLETED: 审查完成</li>
 *   <li>FAILED: 审查失败</li>
 * </ul>
 *
 * @author drug-agent
 */
public enum TenderCaseStatus {

    /** 任务已创建，等待处理 */
    PENDING,

    /** 正在解析文档 */
    PARSING,

    /** 文档解析完成 */
    PARSED,

    /** 审查任务执行中 */
    RUNNING,

    /** 审查完成 */
    COMPLETED,

    /** 审查失败 */
    FAILED
}
