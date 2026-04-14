/**
 * 标书审查报告类型定义
 * 用于结构化展示审查决策报告的六个页面
 */

/**
 * 报告整体数据结构
 */
export interface ReportData {
  /** Page1 审查结论总览 */
  page1Summary: Page1Summary;
  /** Page2 风险总览 */
  page2RiskOverview: Page2RiskOverview;
  /** Page3 核心证据 */
  page3CoreEvidence: Page3CoreEvidence;
  /** Page4 详细比对 */
  page4DetailComparison: Page4DetailComparison;
  /** Page5 处置建议 */
  page5ActionSuggestions: Page5ActionSuggestions;
  /** Page6 附录 */
  page6Appendix: Page6Appendix;
}

/**
 * Page1 审查结论总览
 */
export interface Page1Summary {
  /** 风险等级：high, medium, low, safe */
  riskLevel: string;
  /** 审查结论（一段话） */
  conclusion: string;
  /** 建议处置动作 */
  recommendedAction?: string;
  /** 风险评分 0-100 */
  riskScore: number;
  /** 核心证据数量 */
  coreEvidenceCount: number;
  /** 命中规则数量 */
  ruleHitCount: number;
  /** 核心风险 Top 3 */
  coreRiskTop3: CoreRisk[];
  /** 风险分布 {类型: 等级} */
  riskDistribution: Record<string, string>;
  /** 涉及文档列表 */
  documents: DocumentInfo[];
}

/**
 * 核心风险项
 */
export interface CoreRisk {
  /** 排名 1-3 */
  rank: number;
  /** 风险类型 */
  riskType: string;
  /** 风险标题 */
  title: string;
  /** 风险等级 */
  level: string;
  /** 风险摘要 */
  summary: string;
  /** 建议动作 */
  action: string;
}

/**
 * 文档信息
 */
export interface DocumentInfo {
  /** 文档ID */
  docId: string;
  /** 文档名称 */
  docName: string;
  /** 投标方/参与方 */
  party: string;
  /** 角色（招标方/投标方） */
  role: string;
  /** 内部编号 */
  internalId: string;
}

/**
 * Page2 风险总览
 */
export interface Page2RiskOverview {
  /** 风险分类列表 */
  riskCategories: RiskCategory[];
}

/**
 * 风险分类
 */
export interface RiskCategory {
  /** 风险类型编码 */
  type: string;
  /** 风险分类名称 */
  categoryName: string;
  /** 风险等级 */
  level: string;
  /** 命中数量 */
  hitCount: number;
  /** 是否需要人工复核 */
  needHumanReview: boolean;
  /** 风险解释 */
  explanation: string;
  /** 代表性证据 */
  representativeEvidence: string;
  /** 建议动作 */
  action: string;
}

/**
 * Page3 核心证据
 */
export interface Page3CoreEvidence {
  /** 证据列表（只展示 3-5 条关键证据） */
  evidenceList: Evidence[];
}

/**
 * 证据项
 */
export interface Evidence {
  /** 证据编号 */
  id: string;
  /** 证据类型 */
  type: string;
  /** 风险等级 */
  level: string;
  /** 可信度 */
  confidence: string;
  /** 证据标题 */
  title: string;
  /** 证据解释 */
  explanation: string;
  /** 关键发现 */
  keyFindings: Record<string, any>;
  /** 判定依据 */
  basis: string;
  /** 建议动作 */
  action: string;
}

/**
 * Page4 详细比对
 */
export interface Page4DetailComparison {
  /** 报价对比 */
  priceComparison: PriceComparison;
  /** 团队对比 */
  teamComparison: TeamComparison;
  /** 文本高亮对比列表 */
  textHighlights: TextHighlight[];
}

/**
 * 报价对比
 */
export interface PriceComparison {
  /** 表头 */
  headers: string[];
  /** 报价行数据 */
  rows: PriceRow[];
}

/**
 * 报价行
 */
export interface PriceRow {
  /** 项目名称 */
  item: string;
  /** 文档A报价 */
  docA: string;
  /** 文档B报价 */
  docB: string;
  /** 差异 */
  diff: string;
  /** 判定 */
  verdict: string;
}

/**
 * 团队对比
 */
export interface TeamComparison {
  /** 表头 */
  headers: string[];
  /** 团队行数据 */
  rows: TeamRow[];
}

/**
 * 团队行
 */
export interface TeamRow {
  /** 角色 */
  role: string;
  /** 文档A团队成员 */
  docA: string;
  /** 文档B团队成员 */
  docB: string;
  /** 判定 */
  verdict: string;
}

/**
 * 文本高亮对比
 */
export interface TextHighlight {
  /** 分类 */
  category: string;
  /** 文本A */
  textA: string;
  /** 文本B */
  textB: string;
  /** 相似度 */
  similarity: string;
  /** 判定 */
  verdict: string;
  /** 判定说明 */
  analysis?: string;
}

/**
 * Page5 处置建议
 */
export interface Page5ActionSuggestions {
  /** 一级动作（立即执行） */
  level1: ActionLevel;
  /** 二级动作（进一步核验） */
  level2: ActionLevel;
  /** 三级动作（必要时追溯） */
  level3: ActionLevel;
  /** 留痕建议 */
  retentionAdvice?: string[];
}

/**
 * 动作级别
 */
export interface ActionLevel {
  /** 标题 */
  title: string;
  /** 该层级动作目标 */
  objective?: string;
  /** 动作列表 */
  actions: Action[];
}

/**
 * 动作项
 */
export interface Action {
  /** 动作描述 */
  action: string;
  /** 责任角色 */
  role: string;
  /** 优先级 */
  priority: string;
}

/**
 * Page6 附录
 */
export interface Page6Appendix {
  /** 规则清单 */
  ruleList: RuleInfo[];
  /** 原始证据片段 */
  evidenceFragments: EvidenceFragment[];
  /** 任务信息 */
  taskInfo: TaskInfo;
}

/**
 * 规则信息
 */
export interface RuleInfo {
  /** 规则ID */
  ruleId: string;
  /** 规则编码 */
  ruleCode: string;
  /** 规则描述 */
  description: string;
}

/**
 * 证据片段
 */
export interface EvidenceFragment {
  /** 片段ID */
  fragmentId: string;
  /** 内容 */
  content: string;
  /** 来源 */
  source: string;
}

/**
 * 任务信息
 */
export interface TaskInfo {
  /** 任务ID */
  taskId: string;
  /** 审查时间 */
  reviewTime: string;
  /** 模型版本 */
  modelVersion: string;
}
