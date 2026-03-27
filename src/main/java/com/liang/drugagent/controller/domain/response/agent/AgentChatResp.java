package com.liang.drugagent.controller.domain.response.agent;

import com.liang.drugagent.shared.model.EvidenceItem;
import com.liang.drugagent.shared.model.EvidenceGroup;
import com.liang.drugagent.shared.model.ReviewReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drug Agent 响应对象。
 *
 * @author liangjiajian
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResp {

    /** 会话ID */
    private String sessionId;

    /** 链路追踪ID */
    private String traceId;

    /** 场景标识 */
    private String scene;

    /** 路由原因 */
    private String routeReason;

    /** 路由来源 */
    private String routeSource;

    /** 置信度 */
    private Double confidence;

    /** 摘要 */
    private String summary;

    /** AI回答内容 */
    private String answer;

    /** 风险等级 */
    private String riskLevel;

    /** 综合评分 */
    private int score;

    /** 文档数量 */
    private int docCount;

    /** 管理摘要 */
    private String managementSummary;

    /** 建议操作列表 */
    private List<String> suggestedActions = new ArrayList<>();

    /** 关联案例ID */
    private String caseId;

    /** 文档ID列表 */
    private List<String> documentIds = new ArrayList<>();

    /** 审查报告 */
    private ReviewReport report;

    /** 证据列表 */
    private List<EvidenceItem> evidenceList = new ArrayList<>();

    /** 证据分组列表 */
    private List<EvidenceGroup> evidenceGroups = new ArrayList<>();

    /** 执行步骤列表 */
    private List<String> steps = new ArrayList<>();

    /** 结构化数据 */
    private Map<String, Object> structuredData;

    /** 是否需要澄清 */
    private boolean requiresClarification;

    /** 澄清问题 */
    private String clarificationQuestion;

}
