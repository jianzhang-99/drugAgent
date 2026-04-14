/**
 * 标书审查报告类型定义 (V3 标准版)
 * 严格遵照“产品侧统一标书审查视图”设计的 JSON 字段，抛弃旧的 Page 级切分。
 */

/**
 * 报告顶层结构
 */
export interface ReportData {
  /** 审查结论与关键指标（适合首页/看板展示） */
  executiveSummary: ExecutiveSummary;
  /** 风险分布总览 */
  riskOverview: RiskOverview;
  /** 涉及文档清单 */
  documents: DocumentIndex[];
  /** 核心证据链与详细比对 */
  evidences: EvidenceChain[];
  /** 行动建议与分工矩阵 */
  actionPlan: ActionPlan;
  /** 审查元信息（对应原附录） */
  metadata: ReportMetadata;
}

/**
 * 1. 结论与大盘指标
 */
export interface ExecutiveSummary {
  /** 风险等级，决定主视觉色调：high, medium, low, safe */
  riskLevel: 'high' | 'medium' | 'low' | 'safe';
  /** 风险评分 0-100 */
  riskScore: number;
  /** 审查总体结论摘要（例如："本次审查发现关键条款..."） */
  overallConclusion: string;
  /** 一句话处置动作建议（例如："建议立即人工复核"） */
  recommendedAction: string;
  
  /** 核心关键指标 */
  metrics: {
    /** 核心支撑证据数 */
    coreEvidenceCount: number;
    /** 高置信度风险命中条数 */
    highConfidenceHits: number;
    /** 去重后的命中的风险大类/规则数 */
    deduplicatedRules: number;
    /** 参与比对的文档数 */
    documentCount: number;
    /** 参与比对的投标主体数 */
    partyCount: number;
  };
}

/**
 * 2. 核心风险概览
 */
export interface RiskOverview {
  /** 核心风险 Top3 列表 */
  topRisks: TopRiskCard[];
  /** 风险大类分布（雷达图/列表使用） */
  distributions: RiskDistribution[];
}

export interface TopRiskCard {
  /** 排行：1, 2, 3 */
  rank: number;
  /** 风险名称（例如："关键条款高度相似"） */
  riskName: string;
  /** 风险大类枚举 */
  riskType: 'pricing' | 'team' | 'text_similarity' | 'template' | 'other';
  /** 风险等级 */
  riskLevel: 'high' | 'medium' | 'low';
  /** 风险业务说明（一两句话） */
  description: string;
  /** 关键事实证据一句话 */
  keyFact: string;
  /** 系统判定出风险的直接依据 */
  basis: string;
  /** 业务侧专属的动作建议 */
  action: string;
}

export interface RiskDistribution {
  /** 风险大类名称，如"文本相似风险" */
  riskType: string;
  /** 该大类最高风险等级，如无问题不应返回或返回 safe */
  level: 'high' | 'medium' | 'low' | 'safe';
  /** 该类触发风险条件的次数 */
  hitCount: number;
  /** 是否需要人工复核介入 */
  needReview: boolean;
  /** 风险类别简短说明 */
  explanation: string;
}

/**
 * 3. 涉及文档信息
 */
export interface DocumentIndex {
  /** 文档内部唯一 ID */
  id: string;
  /** 对外呈现的序号/字母，如 A、B、C */
  docCode: string;
  /** 投标主体名称（提取） */
  partyName: string;
  /** 文件原有名称 */
  fileName: string;
  /** 文档角色：投标文件/技术标/商务标 */
  docRole: string;
  /** 参与了多少次核心风险事件 */
  hitRiskCount: number;
  /** 具体涉及到的风险类型名列表 */
  involvedRisks: string[];
}

/**
 * 4. 证据链条 (包含沉浸式 Diff 所需数据)
 */
export interface EvidenceChain {
  /** 证据编号 E01, E02 等 */
  evidenceId: string;
  /** 证据标题 */
  title: string;
  /** 证据所属形态分类 */
  type: 'text_diff' | 'price_diff' | 'team_diff' | 'structure_diff' | 'other';
  /** 该项证据对结论的支撑力度 */
  level: 'high' | 'medium' | 'low';
  /** 重点摘要描述，不堆砌长文 */
  summary: string;
  /** 人工复核指南/系统分析结果 */
  analysis: string;
  
  /** 双文档对比的实质差异数据 */
  diffPayload: EvidenceDiffPayload;
}

export interface EvidenceDiffPayload {
  /** 比对中的甲侧实体 ID */
  docA_id: string;
  /** 比对中的乙侧实体 ID */
  docB_id: string;
  /** 甲侧源数据/文本 (或者项目金额、人员名字) */
  contentA: string;
  /** 乙侧源数据/文本 (或者项目金额、人员名字) */
  contentB: string;
  
  /** (针对文本/结构时) 格式化后的相似度（如 "96%"） */
  similarityScore?: string;
  /** （针对价差/特定字段时）具体差异（如 "差额 500"） */
  divergence?: string;
  
  /** 高亮及界面状态的信号灯 */
  diffVerdict: 'exact_match' | 'fuzzy_match' | 'anomaly_gap' | 'safe' | 'warning';
}

/**
 * 5. 处置建议与任务矩阵
 */
export interface ActionPlan {
  /** 一级：立即执行或阻断 */
  level1Actions: string[];
  /** 二级：进一步外围核验 */
  level2Actions: string[];
  /** 三级：事后追溯留档 */
  level3Actions: string[];
  
  /** 推荐的任务到人分配矩阵 */
  responsibilityMatrix: TaskRoleAssign[];
}

export interface TaskRoleAssign {
  /** 要执行的动作 */
  action: string;
  /** 被分配的角色 */
  role: string; // '风控专员' | '评审专家' | '招采员' 等
  /** 任务优先级 */
  priority: 'high' | 'medium' | 'low';
  /** 补充说明 */
  remark?: string;
}

/**
 * 6. 后台/附加元信息
 */
export interface ReportMetadata {
  taskId: string;
  reportId: string;
  generatedAt: string;
  projectTarget: string; // "审查对象"
  reviewType: string;    // "审查类型"
  reviewScope: string;
  systemVersion: string;
  hitRules?: Array<{ ruleCode: string; ruleName: string; hitCount: number; remark?: string; }>;
}
