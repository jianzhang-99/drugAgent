package com.liang.drugagent.tool.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 标书审查工具请求对象。
 *
 * <p>作为 LLM 调用工具时的入参，封装标书审查所需的完整数据。
 * 当 LLM 决定调用标书审查工具时，由 orchestrator 构建此对象。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不可变：使用 record 类型确保线程安全</li>
 *   <li>自校验：内置 validate() 方法确保数据完整性</li>
 *   <li>可追溯：清晰的字段命名和文档</li>
 * </ul>
 *
 * @author liangjiajian
 */
public record ReviewTenderToolReq(

        /**
         * 关联会话ID。
         */
        String sessionId,

        /**
         * 待审查文件ID列表。
         */
        List<String> fileIds,

        /**
         * 审查重点：围标风险/技术方案雷同/商务条款。
         */
        String reviewFocus,

        /**
         * 用户额外补充。
         */
        String userInstruction,

        /**
         * 是否需要完整报告。
         */
        Boolean needStructuredReport
) {

    /**
     * 紧凑构造方法，初始化空集合。
     */
    public ReviewTenderToolReq {
        if (fileIds == null) {
            fileIds = new ArrayList<>();
        }
        if (needStructuredReport == null) {
            needStructuredReport = false;
        }
    }

    /**
     * 校验请求参数。
     *
     * @throws IllegalArgumentException 校验失败时抛出
     */
    public void validate() {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new IllegalArgumentException("待审查文件ID列表不能为空");
        }
        if (fileIds.size() < 2) {
            throw new IllegalArgumentException("至少需要 2 份标书文件进行比对审查");
        }
        for (String fileId : fileIds) {
            if (fileId == null || fileId.isBlank()) {
                throw new IllegalArgumentException("文件ID不能为空");
            }
        }
        if (sessionId != null && sessionId.isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空字符串");
        }
    }

    /**
     * 创建默认请求。
     */
    public static ReviewTenderToolReq empty() {
        return new ReviewTenderToolReq(null, new ArrayList<>(), null, null, false);
    }

    /**
     * 创建请求（简化版，只提供文件ID列表）。
     */
    public static ReviewTenderToolReq of(List<String> fileIds) {
        return new ReviewTenderToolReq(null, fileIds, null, null, false);
    }

    /**
     * 创建请求。
     */
    public static ReviewTenderToolReq of(String sessionId, List<String> fileIds, String reviewFocus) {
        return new ReviewTenderToolReq(sessionId, fileIds, reviewFocus, null, false);
    }

    /**
     * 创建请求。
     */
    public static ReviewTenderToolReq of(String sessionId, List<String> fileIds, String reviewFocus,
                                         String userInstruction, Boolean needStructuredReport) {
        return new ReviewTenderToolReq(sessionId, fileIds, reviewFocus, userInstruction, needStructuredReport);
    }

    /**
     * 获取审查重点，如果为空则返回默认值。
     */
    public String getReviewFocusOrDefault() {
        return reviewFocus != null && !reviewFocus.isBlank() ? reviewFocus : "全面审查";
    }

    /**
     * 检查是否需要生成结构化报告。
     */
    public boolean isNeedStructuredReport() {
        return Boolean.TRUE.equals(needStructuredReport);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewTenderToolReq that = (ReviewTenderToolReq) o;
        return Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(fileIds, that.fileIds) &&
                Objects.equals(reviewFocus, that.reviewFocus) &&
                Objects.equals(userInstruction, that.userInstruction) &&
                Objects.equals(needStructuredReport, that.needStructuredReport);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, fileIds, reviewFocus, userInstruction, needStructuredReport);
    }

    @Override
    public String toString() {
        return "ReviewTenderToolReq{" +
                "sessionId='" + sessionId + '\'' +
                ", fileIds=" + (fileIds != null ? fileIds.size() + " files" : "null") +
                ", reviewFocus='" + reviewFocus + '\'' +
                ", userInstruction='" + userInstruction + '\'' +
                ", needStructuredReport=" + needStructuredReport +
                '}';
    }
}
