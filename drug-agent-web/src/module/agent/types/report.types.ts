/**
 * 标书审查报告类型定义 (V2 用户视角版)
 * 参照"产品侧统一标书审查视图"设计，聚焦用户可理解的风险信息。
 */

// ========== 报告整体 ==========
export interface ReportData {
  executiveSummary: ExecutiveSummary;   // 总体结论
  riskOverview: RiskOverview;           // 风险总览
  documents: DocumentIndex[];          // 本次比对文件
  evidences: EvidenceChain[];         // 关键证据明细
  actionPlan: ActionPlan;             // 处置建议
  metadata: ReportMetadata;             // 报告元信息
}

// ========== 1. 总体结论 ==========
export interface ExecutiveSummary {
  /** 结论正文（人话，不是"高风险"） */
  conclusionText: string;
  /** 风险综合判定标签，如"重大围标风险" */
  riskLevelLabel?: string;
  /** 3个业务指标（按原型：有效违规命中/关联证据簇/参与审查文书） */
  metrics: {
    effectiveHits: number;       // 有效违规命中数（原 needReview）
    evidenceClusters: number;     // 关联证据簇数（原 traceableEvidence）
    documentCount: number;       // 参与审查文书数
    /** 兼容旧版 V3 指标 */
    coreEvidenceCount?: number;
    highConfidenceHits?: number;
    deduplicatedRules?: number;
    partyCount?: number;
  };
  /** 审查说明（一句话） */
  reviewNote: string;
  /** 兼容旧版 V3 */
  riskLevel?: string;
  riskScore?: number;
  overallConclusion?: string;
  recommendedAction?: string;
}

/** 向后兼容别名 */
export type ExecutiveSummaryMetricsLegacy = {
  needReview: number;
  keyRiskItems: number;
  traceableEvidence: number;
};

export interface ReportMetadata {
  generatedAt: string;        // 生成时间
  reviewScope?: string;       // 审查范围（可选）
  /** 原型中的 DOCUMENT ID，如 TSR-20240416-X99 */
  documentId?: string;
  /** 兼容旧版 V3 */
  taskId?: string;
  reportId?: string;
  traceId?: string;
  projectTarget?: string;
  reviewType?: string;
  systemVersion?: string;
  hitRules?: Array<{ ruleCode: string; ruleName: string; hitCount: number; remark?: string }>;
}

// ========== 2. 本次比对文件 ==========
export interface DocumentIndex {
  /** 文档编号，如"文档 A"或"DOC-001" */
  docCode: string;
  /** 原型要求的文档ID，如"DOC-001" */
  docId?: string;
  partyName: string;   // 投标主体，如"晟博云创"
  fileName: string;    // 文件名，如"投标人A_晟博云创_W-M1测试标书.md"
  /** 文档性质（原型：审计主文档/关键参检文档） */
  docNature?: string;
  /** 最后修改人 */
  lastModifier?: string;
  role?: string;        // 角色描述，如"第一份投标文件参与比对"
  /** 兼容旧版 V3 docRole */
  docRole?: string;
  /** 兼容旧版 V3 id */
  id?: string;
}

// ========== 3. 风险总览 ==========
export interface RiskOverview {
  /** 重点风险判断（Chapter4），最多3条 */
  topRisks: TopRisk[];
  /** 只展示"有发现"的风险方向 */
  distributions: RiskDistribution[];
}

export interface RiskDistribution {
  riskType: string;        // 风险方向，如"报价异常"、"核心团队重复"、"关键条款相似"
  found: boolean;          // 是否有发现
  foundDescription: string; // 发现情况描述
  needReview: boolean;     // 是否必须复核
  needReviewText: string;  // 复核必要性描述
  brief: string;           // 简要说明
  /** 兼容旧版 adapter */
  hitCount?: number;
  explanation?: string;
  /** 兼容旧版 V3 */
  level?: string;
}

// ========== 4. 重点风险判断 ==========
export interface TopRisk {
  rank: number;             // 1, 2, 3
  /** 原型中的规则编码，如 W-M7、W-P1 */
  ruleCode?: string;
  riskName: string;         // 风险名称，如"报价结构异常"
  level: 'high' | 'medium' | 'low'; // 风险等级
  riskDesc: string;         // 风险说明（原型中的 RISK DESCRIPTION）
  keyFact: string;          // 关键事实
  whyReview: string;        // 为什么需要复核
  action: string;           // 建议动作（原型中的 ACTION）
  /** 兼容旧版 V3 */
  riskLevel?: string;
  riskType?: string;
  description?: string;
}

// ========== 5. 关键证据明细 ==========
export interface EvidenceChain {
  evidenceId: string;       // "E01", "E02"
  type: string;            // 证据类型，如"报价异常"、"关键条款相似"
  level: 'high' | 'medium' | 'low';

  // 原型要求：证据链主标识，如 "RULE-W-P1-002"
  evidenceChainId?: string;
  /** 文本相似度，如 "98.2%" */
  similarity?: string;

  // 证据指向
  sourceType: string;       // 指向的风险类型

  // 涉及文件
  docAName: string;         // "文档 A（晟博云创）"
  docBName: string;         // "文档 B（晟拓数科）"

  // A/B 原始内容（最关键）
  docAContent: string;      // 文档A原文/数据
  docBContent: string;      // 文档B原文/数据

  // 对比发现
  comparisonFinding: string;

  /** 原型要求的 AI 判定逻辑说明 */
  aiJudgment?: string;

  // 复核建议
  reviewSuggestion: string;

  /** 兼容旧版 V3 */
  title?: string;
  summary?: string;

  /** 兼容旧版 V3 diffPayload */
  diffPayload?: {
    docA_id: string;
    docB_id: string;
    contentA: string;
    contentB: string;
    similarityScore?: string;
    divergence?: string;
    diffVerdict?: string;
  };
}

// ========== 6. 处置建议 ==========
export interface ActionPlan {
  // 三级任务清单
  tasks: TaskItem[];
  /** 兼容旧版 V3 */
  level1Actions?: string[];
  level2Actions?: string[];
  level3Actions?: string[];
  responsibilityMatrix?: TaskRoleAssignV3[];
}

export interface TaskItem {
  priority: 'high' | 'medium' | 'low';
  action: string;           // 建议动作
  role: string;             // 责任角色
  goal: string;             // 目标
}

// ========== 7. 报告边界说明 ==========
// 直接用字符串数组，不需要单独类型
// type ReportBoundary = string[];

// ============================================================================
// 以下类型为兼容旧版报告组件保留（已废弃，请勿在新代码中使用）
// ============================================================================

/**
 * @deprecated 旧版报告顶层结构，请使用新版 ReportData
 */
export interface ReportDataV3 {
  executiveSummary: ExecutiveSummaryV3;
  riskOverview: RiskOverviewV3;
  documents: DocumentIndexV3[];
  evidences: EvidenceChainV3[];
  actionPlan: ActionPlanV3;
  metadata: ReportMetadataV3;
}

/**
 * @deprecated 旧版总体结论，请使用新版 ExecutiveSummary
 */
export interface ExecutiveSummaryV3 {
  riskLevel: 'high' | 'medium' | 'low' | 'safe';
  riskScore: number;
  overallConclusion: string;
  recommendedAction: string;
  metrics: {
    coreEvidenceCount: number;
    highConfidenceHits: number;
    deduplicatedRules: number;
    documentCount: number;
    partyCount: number;
  };
}

/**
 * @deprecated 旧版风险总览，请使用新版 RiskOverview
 */
export interface RiskOverviewV3 {
  topRisks: TopRiskCardV3[];
  distributions: RiskDistributionV3[];
}

export interface TopRiskCardV3 {
  rank: number;
  riskName: string;
  riskType: 'pricing' | 'team' | 'text_similarity' | 'template' | 'other';
  riskLevel: 'high' | 'medium' | 'low';
  description: string;
  keyFact: string;
  basis: string;
  action: string;
}

export interface RiskDistributionV3 {
  riskType: string;
  level: 'high' | 'medium' | 'low' | 'safe';
  hitCount: number;
  needReview: boolean;
  explanation: string;
}

/**
 * @deprecated 旧版文档信息，请使用新版 DocumentIndex
 */
export interface DocumentIndexV3 {
  id: string;
  docCode: string;
  partyName: string;
  fileName: string;
  docRole: string;
  hitRiskCount: number;
  involvedRisks: string[];
}

/**
 * @deprecated 旧版证据链，请使用新版 EvidenceChain
 */
export interface EvidenceChainV3 {
  evidenceId: string;
  title: string;
  type: 'text_diff' | 'price_diff' | 'team_diff' | 'structure_diff' | 'other';
  level: 'high' | 'medium' | 'low';
  summary: string;
  analysis: string;
  basis?: string;
  docAName?: string;
  docBName?: string;
  diffPayload: EvidenceDiffPayload;
}

export interface EvidenceDiffPayload {
  docA_id: string;
  docB_id: string;
  contentA: string;
  contentB: string;
  similarityScore?: string;
  divergence?: string;
  diffVerdict: 'exact_match' | 'fuzzy_match' | 'anomaly_gap' | 'safe' | 'warning';
}

/**
 * @deprecated 旧版处置建议，请使用新版 ActionPlan
 */
export interface ActionPlanV3 {
  level1Actions: string[];
  level2Actions: string[];
  level3Actions: string[];
  responsibilityMatrix: TaskRoleAssignV3[];
}

export interface TaskRoleAssignV3 {
  action: string;
  role: string;
  priority: 'high' | 'medium' | 'low';
  remark?: string;
}

/**
 * @deprecated 旧版报告元信息，请使用新版 ReportMetadata
 */
export interface ReportMetadataV3 {
  taskId: string;
  reportId: string;
  generatedAt: string;
  traceId?: string;
  projectTarget: string;
  reviewType: string;
  reviewScope: string;
  systemVersion: string;
  hitRules?: Array<{ ruleCode: string; ruleName: string; hitCount: number; remark?: string; }>;
}

/**
 * @deprecated 旧版 Page2 风险总览
 */
export interface Page2RiskOverview {
  riskCategories: Array<{
    type: string;
    categoryName: string;
    level: string;
    hitCount: number;
    needHumanReview: boolean;
    explanation: string;
    representativeEvidence: string;
    action: string;
  }>;
}

/**
 * @deprecated 旧版 Page3 核心证据
 */
export interface Page3CoreEvidence {
  evidenceList: Array<{
    id: string;
    type: string;
    level: string;
    confidence: string;
    title: string;
    explanation: string;
    keyFindings: Record<string, unknown>;
    basis: string;
    action: string;
  }>;
}

/**
 * @deprecated 旧版行动建议
 */
export interface ActionLevelItem {
  action: string;
  role: string;
  priority: string;
}

export interface ActionLevel {
  title: string;
  objective: string;
  actions: ActionLevelItem[];
}

/**
 * @deprecated 旧版 Page5 处置建议
 */
export interface Page5ActionSuggestions {
  level1?: ActionLevel;
  level2?: ActionLevel;
  level3?: ActionLevel;
  retentionAdvice?: string[];
}

/**
 * @deprecated 旧版 Page6 附录
 */
export interface Page6Appendix {
  ruleList?: Array<{
    ruleId: string;
    ruleCode: string;
    description: string;
  }>;
  evidenceFragments?: Array<{
    fragmentId: string;
    content: string;
    source: string;
  }>;
  taskInfo?: {
    taskId: string;
    reviewTime: string;
    modelVersion: string;
  };
}
