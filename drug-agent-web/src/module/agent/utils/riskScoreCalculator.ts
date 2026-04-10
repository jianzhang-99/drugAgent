/**
 * 围标风险综合评分算法
 *
 * 采用多维度加权模型，综合以下四个维度计算 0~100 分的风险分：
 *
 * ┌─────────────────────────────────────────────────────┐
 * │ 维度               权重   说明                        │
 * ├─────────────────────────────────────────────────────┤
 * │ D1 证据相似度       40%   evidenceGroups 相似度分布   │
 * │ D2 规则命中强度     30%   riskItems 数量 × 等级权重   │
 * │ D3 风险等级基准     20%   riskLevel 直接映射底分       │
 * │ D4 路由置信度修正   10%   confidence 系数对最终分调整  │
 * └─────────────────────────────────────────────────────┘
 *
 * 分数含义：
 *   85~100 → 高风险（围标特征明显）
 *   60~84  → 中风险（存在疑似特征）
 *   30~59  → 低风险（少量相似，需关注）
 *   0~29   → 安全（相似度极低）
 */

import type { ResultData, EvidenceGroup, RiskItem } from '../types/agent';

// ──────────────── 类型 ────────────────

export interface RiskScoreBreakdown {
  /** 最终综合分 0~100 */
  total: number;
  /** 各维度得分（0~100），用于可视化 */
  dimensions: {
    evidenceSimilarity: number;   // D1
    ruleHitStrength: number;      // D2
    riskLevelBase: number;        // D3
    confidenceBonus: number;      // D4
  };
  /** 风险等级（由算法推导，可能与后端不同） */
  derivedLevel: 'high' | 'medium' | 'low' | 'safe';
  /** 置信描述 */
  interpretation: string;
}

// ──────────────── 常量 ────────────────

/** 各维度权重（总和 = 1） */
const WEIGHTS = {
  evidenceSimilarity : 0.40,
  ruleHitStrength    : 0.30,
  riskLevelBase      : 0.20,
  confidenceBonus    : 0.10,
} as const;

/** riskLevel 映射基准分 */
const RISK_LEVEL_BASE: Record<string, number> = {
  high   : 90,
  medium : 65,
  low    : 35,
  safe   : 5,
  unknown: 50,
};

/** riskItem 等级权重 */
const RISK_ITEM_WEIGHT: Record<string, number> = {
  high   : 3.0,
  medium : 1.5,
  low    : 0.6,
  unknown: 0.4,
};

// ──────────────── 子维度计算 ────────────────

/**
 * D1 证据相似度评分
 *
 * 采用「最高相似度 × 0.5 + 加权平均相似度 × 0.5」
 * 使单个极端相似和整体趋势都能反映。
 */
function calcEvidenceSimilarityScore(data: ResultData): number {
  // 优先用 evidenceGroups
  const groups = (data as any).evidenceGroups as EvidenceGroup[] | undefined;

  let similarities: number[] = [];

  if (groups && groups.length > 0) {
    similarities = groups
      .map(g => (g.similarity ?? 0))
      .filter(s => s > 0);
  }

  // 兜底：使用 evidenceList 中的 similarity 字段
  if (similarities.length === 0 && data.evidenceList && data.evidenceList.length > 0) {
    similarities = data.evidenceList
      .map(e => e.similarity ?? 0)
      .filter(s => s > 0);
  }

  // 后端有 overview.score，转换为相似度
  if (similarities.length === 0 && data.report?.overview?.score !== undefined) {
    return Math.min(100, Math.max(0, data.report.overview.score));
  }

  // 后端有 data.score 兜底
  if (similarities.length === 0 && data.score !== undefined) {
    return Math.min(100, Math.max(0, data.score));
  }

  if (similarities.length === 0) return 50; // 无数据时取中间值

  const maxSimilarity = Math.max(...similarities) * 100;
  const avgSimilarity = (similarities.reduce((a, b) => a + b, 0) / similarities.length) * 100;

  // 加权：最大值权重更高，反映"一票高风险"原则
  return Math.min(100, maxSimilarity * 0.6 + avgSimilarity * 0.4);
}

/**
 * D2 规则命中强度评分
 *
 * 计算公式：min(RawScore, 100)
 * RawScore = Σ(riskItem.weight) × 归一化因子
 * 上限为 100，每10点加权权重 → 满分
 */
function calcRuleHitStrengthScore(data: ResultData): number {
  const riskItems = data.report?.riskItems as RiskItem[] | undefined;

  if (!riskItems || riskItems.length === 0) {
    // 无 riskItems 时，用 rawHitCount 做估算
    const rawHitCount = data.report?.overview?.rawHitCount ?? 0;
    if (rawHitCount > 0) {
      return Math.min(100, rawHitCount * 15); // 每命中一条规则 +15 分
    }
    return 0;
  }

  let rawScore = 0;
  for (const item of riskItems) {
    const level = item.riskLevel ?? 'unknown';
    rawScore += RISK_ITEM_WEIGHT[level] ?? 0.4;
  }

  // 归一化：10 权重积分 = 满分
  return Math.min(100, (rawScore / 10) * 100);
}

/**
 * D3 风险等级基准分
 *
 * 直接映射后端 riskLevel，作为底线兜底。
 * 避免规则命中少但 LLM 判定高风险时评分偏低的问题。
 */
function calcRiskLevelBaseScore(data: ResultData): number {
  const level = data.riskLevel ?? 'unknown';
  return RISK_LEVEL_BASE[level] ?? 50;
}

/**
 * D4 置信度修正分
 *
 * confidence 为 0~1，将路由置信度作为修正项。
 * 高置信度 → 接近满分（100），低置信度 → 接近 0。
 * 这一维度权重最低（10%），仅作微调。
 */
function calcConfidenceBonusScore(data: ResultData): number {
  const confidence = data.confidence;
  if (confidence === undefined || confidence === null) return 70; // 无数据时取一个中位值
  return Math.min(100, Math.max(0, confidence * 100));
}

// ──────────────── 主函数 ────────────────

/**
 * 计算综合风险评分（主调用入口）
 *
 * @param data ResultData 对象
 * @returns RiskScoreBreakdown 含总分及各维度明细
 */
export function calcRiskScore(data?: ResultData): RiskScoreBreakdown {
  if (!data) {
    return {
      total: 0,
      dimensions: { evidenceSimilarity: 0, ruleHitStrength: 0, riskLevelBase: 0, confidenceBonus: 0 },
      derivedLevel: 'safe',
      interpretation: '暂无数据',
    };
  }

  const d1 = calcEvidenceSimilarityScore(data);
  const d2 = calcRuleHitStrengthScore(data);
  const d3 = calcRiskLevelBaseScore(data);
  const d4 = calcConfidenceBonusScore(data);

  // 加权求和
  const raw =
    d1 * WEIGHTS.evidenceSimilarity +
    d2 * WEIGHTS.ruleHitStrength +
    d3 * WEIGHTS.riskLevelBase +
    d4 * WEIGHTS.confidenceBonus;

  const total = Math.round(Math.min(100, Math.max(0, raw)));

  // 由算法推导风险等级
  const derivedLevel = total >= 85 ? 'high'
    : total >= 60 ? 'medium'
    : total >= 30 ? 'low'
    : 'safe';

  // 可读解释文本
  const interpretation = interpretScore(total, data.report?.riskItems?.length ?? 0);

  return {
    total,
    dimensions: {
      evidenceSimilarity: Math.round(d1),
      ruleHitStrength    : Math.round(d2),
      riskLevelBase      : Math.round(d3),
      confidenceBonus    : Math.round(d4),
    },
    derivedLevel,
    interpretation,
  };
}

/**
 * 根据分数生成可读说明
 */
function interpretScore(score: number, ruleCount: number): string {
  if (score >= 85) return `综合风险极高，命中 ${ruleCount} 条规则，建议立即介入核实`;
  if (score >= 70) return `围标特征较显著，已发现 ${ruleCount} 处疑似规律，需重点关注`;
  if (score >= 60) return `存在一定相似风险，命中 ${ruleCount} 条规则，建议人工复核`;
  if (score >= 40) return `相似度偏高，发现 ${ruleCount} 条潜在规则，可进一步观察`;
  if (score >= 20) return `风险较低，仅存在少量形式相似，暂无明显围标特征`;
  return '相似度极低，未发现围标风险特征';
}

/**
 * 根据分数获取对应等级颜色 token（CSS class 后缀）
 */
export function scoreToColorToken(score: number): 'high' | 'medium' | 'low' | 'safe' {
  if (score >= 85) return 'high';
  if (score >= 60) return 'medium';
  if (score >= 30) return 'low';
  return 'safe';
}

/**
 * 获取算法维度的中文标签
 */
export const DIMENSION_LABELS: Record<keyof RiskScoreBreakdown['dimensions'], string> = {
  evidenceSimilarity : '证据相似度',
  ruleHitStrength    : '规则命中强度',
  riskLevelBase      : '风险等级基准',
  confidenceBonus    : '置信度修正',
};
