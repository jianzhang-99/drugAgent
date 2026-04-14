import type { ThinkingStep } from '../types/agent';

type StepSeed = Omit<ThinkingStep, 'status' | 'detail'> & {
  defaultDetail: string;
};

const TENDER_REVIEW_STEP_SEEDS: Record<string, StepSeed> = {
  route: {
    code: 'route',
    title: '场景识别',
    type: 'ROUTE',
    order: 1,
    defaultDetail: '检测到上传文件，自动进入标书审查场景',
  },
  validate_data: {
    code: 'validate_data',
    title: '数据校验',
    type: 'EXECUTION',
    order: 2,
    defaultDetail: '检查文件完整性和格式...',
  },
  prepare_data: {
    code: 'prepare_data',
    title: '文档解析',
    type: 'EXECUTION',
    order: 3,
    defaultDetail: '解析标书文本与结构化字段...',
  },
  rule_analysis: {
    code: 'rule_analysis',
    title: '规则命中分析',
    type: 'EXECUTION',
    order: 4,
    defaultDetail: '执行确定性规则，筛出相似特征...',
  },
  semantic_analysis: {
    code: 'semantic_analysis',
    title: 'LLM语义分析',
    type: 'EXECUTION',
    order: 5,
    defaultDetail: '对语义相似片段做补强分析...',
  },
  apply_exemption: {
    code: 'apply_exemption',
    title: '误报豁免',
    type: 'EXECUTION',
    order: 6,
    defaultDetail: '对可能的误报场景进行降权处理...',
  },
  risk_fusion: {
    code: 'risk_fusion',
    title: '风险融合',
    type: 'EXECUTION',
    order: 7,
    defaultDetail: '融合规则命中与语义分析结果...',
  },
  assemble_evidence: {
    code: 'assemble_evidence',
    title: '证据组装',
    type: 'EXECUTION',
    order: 8,
    defaultDetail: '组织命中规则对应的证据链...',
  },
  generate_report: {
    code: 'generate_report',
    title: '报告生成',
    type: 'FINALIZE',
    order: 9,
    defaultDetail: '生成最终审查报告...',
  },
};

function buildStep(code: keyof typeof TENDER_REVIEW_STEP_SEEDS, status: string, detail?: string): ThinkingStep {
  const seed = TENDER_REVIEW_STEP_SEEDS[code];
  return {
    code: seed.code,
    title: seed.title,
    type: seed.type,
    order: seed.order,
    status,
    detail: detail?.trim() || seed.defaultDetail,
  };
}

function toCanonicalCode(step?: ThinkingStep | null): string | null {
  const code = step?.code?.trim();
  const title = step?.title?.trim();

  switch (code) {
    case 'route':
    case 'validate_data':
    case 'prepare_data':
    case 'rule_analysis':
    case 'semantic_analysis':
    case 'risk_fusion':
      return code;
    case 'exemption':
    case 'apply_exemption':
      return 'apply_exemption';
    case 'evidence_assembly':
    case 'assemble_evidence':
      return 'assemble_evidence';
    case 'report_generation':
    case 'generate_report':
      return 'generate_report';
    case 'data_loading':
      return 'data_loading';
    case 'l4_validation':
      return 'l4_validation';
    default:
      break;
  }

  switch (title) {
    case '场景识别':
      return 'route';
    case '数据校验':
      return 'validate_data';
    case '数据校验与文档解析':
    case '文档解析':
    case '结构化加载':
      return 'prepare_data';
    case '规则命中分析':
      return 'rule_analysis';
    case 'LLM语义分析':
      return 'semantic_analysis';
    case '误报豁免':
      return 'apply_exemption';
    case '风险融合':
      return 'risk_fusion';
    case '证据组装':
      return 'assemble_evidence';
    case '报告生成':
      return 'generate_report';
    default:
      return null;
  }
}

export function createInitialTenderReviewThinkingSteps(): ThinkingStep[] {
  return [
    buildStep('route', 'COMPLETED'),
    buildStep('validate_data', 'PROCESSING'),
  ];
}

export function normalizeThinkingStep(step?: ThinkingStep | null): ThinkingStep[] {
  const canonicalCode = toCanonicalCode(step);
  if (!canonicalCode || !step) {
    return [];
  }

  if (canonicalCode === 'l4_validation') {
    return [];
  }

  if (canonicalCode === 'data_loading') {
    return [buildStep('prepare_data', 'COMPLETED', '标书文本与结构化字段解析完成')];
  }

  if (canonicalCode === 'prepare_data') {
    const normalizedStatus = step.status || 'PROCESSING';
    const validateDetail = normalizedStatus === 'FAILED'
      ? '文件校验已通过，文档解析阶段出现异常'
      : '文件完整性和格式校验通过';

    return [
      buildStep('validate_data', 'COMPLETED', validateDetail),
      buildStep('prepare_data', normalizedStatus, step.detail),
    ];
  }

  if (canonicalCode in TENDER_REVIEW_STEP_SEEDS) {
    return [buildStep(canonicalCode as keyof typeof TENDER_REVIEW_STEP_SEEDS, step.status || 'INFO', step.detail)];
  }

  return [];
}

export function normalizeThinkingSteps(steps?: ThinkingStep[] | null): ThinkingStep[] {
  if (!steps?.length) {
    return [];
  }

  const merged = new Map<string, ThinkingStep>();
  for (const step of steps) {
    for (const normalized of normalizeThinkingStep(step)) {
      merged.set(normalized.code, normalized);
    }
  }

  return Array.from(merged.values()).sort((a, b) => (a.order || 0) - (b.order || 0));
}

export function mergeNormalizedThinkingSteps(
  currentSteps: ThinkingStep[] = [],
  incomingSteps: Array<ThinkingStep | null | undefined> = []
): ThinkingStep[] {
  const merged = new Map<string, ThinkingStep>();

  for (const step of normalizeThinkingSteps(currentSteps)) {
    merged.set(step.code, step);
  }

  for (const step of incomingSteps) {
    for (const normalized of normalizeThinkingStep(step)) {
      merged.set(normalized.code, normalized);
    }
  }

  return Array.from(merged.values()).sort((a, b) => (a.order || 0) - (b.order || 0));
}
