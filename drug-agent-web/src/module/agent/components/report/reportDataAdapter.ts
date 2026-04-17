import type { DrugAgentResp, EvidenceGroup, RiskItem } from '../../types/agent';
import type {
  ActionPlan,
  DocumentIndex,
  EvidenceChain,
  ExecutiveSummary,
  ReportData,
  ReportMetadata,
  RiskOverview,
} from '../../types/report.types';

type LegacyReportData = Record<string, any>;

const REASON_TEXT: Record<string, string> = {
  'MULTI_RULE_CO_OCCURRENCE': '同一批文件同时命中多类异常信号',
  'MULTI_HIT_ACCUMULATION': '同类异常重复出现，说明不是单点偶发',
  'CROSS_DOCUMENT_VALIDATION': '异常在多份文件之间形成交叉印证',
  'CROSS_DOCUMENT_EVIDENCE': '异常在多份文件之间形成交叉印证',
  'EVIDENCE_SUFFICIENT': '当前证据数量和强度已足以支撑进一步复核',
  'HIGH_PRIORITY_RULE': '命中了高优先级风险规则',
  'SYNERGY_BONUS': '多条异常共同增强了整体风险判断',
  'EXEMPTION_DOWNGRADE': '部分线索已按模板或引用场景做降权处理',
  'W-M1': '报价梯度异常',
  'W-M2': '联系人或联系方式异常接近',
  'W-M3': '核心团队成员重复',
  'W-M4': '版式模板同源',
  'W-P1': '技术方案雷同',
  'W-P2': '实施路径或结构高度相似',
  'W-P3': '服务承诺雷同',
  'W-P4': '风险识别抄袭',
  'W-P5': '错误复现',
};

export function normalizeReportData(result: DrugAgentResp | null): ReportData | null {
  if (!result) {
    return null;
  }

  const raw = result.reportData as LegacyReportData | undefined;
  if (raw?.executiveSummary) {
    return polishReportData(raw as ReportData, result);
  }
  if (raw?.page1Summary) {
    return adaptLegacyStructuredReport(raw, result);
  }
  if (result.report) {
    return transformReviewReport(result);
  }
  return null;
}

function adaptLegacyStructuredReport(raw: LegacyReportData, result: DrugAgentResp): ReportData {
  const legacyDocuments = Array.isArray(raw.page1Summary?.documents) ? raw.page1Summary.documents : [];
  const documents = buildDocuments(legacyDocuments, result);
  const aliasMap = buildAliasMap(documents, legacyDocuments, result);

  const topRisks = buildLegacyTopRisks(raw, aliasMap);
  const distributions = buildLegacyDistributions(raw, aliasMap);
  const evidences = buildLegacyEvidences(raw, documents, aliasMap);

  const executiveSummary: ExecutiveSummary = {
    conclusionText: humanizeText(
      raw.page1Summary?.conclusion || result.summary || '本次比对已完成，但暂未生成明确审查结论。',
      aliasMap,
    ),
    riskLevel: normalizeLevel(raw.page1Summary?.riskLevel),
    riskScore: Number(raw.page1Summary?.riskScore || 0),
    overallConclusion: humanizeText(
      raw.page1Summary?.conclusion || result.summary || '本次比对已完成，但暂未生成明确审查结论。',
      aliasMap,
    ),
    recommendedAction: humanizeText(
      raw.page1Summary?.recommendedAction || buildDecisionAction(result),
      aliasMap,
    ),
    reviewNote: '本报告用于辅助识别标书疑似围标、串标或非独立编制风险，不直接替代最终评审结论。',
    metrics: {
      effectiveHits: Number(raw.page1Summary?.coreEvidenceCount || evidences.length || 0),
      evidenceClusters: evidences.length,
      documentCount: documents.length,
      coreEvidenceCount: Number(raw.page1Summary?.coreEvidenceCount || evidences.length || 0),
      highConfidenceHits: topRisks.filter((item: any) => item.riskLevel === 'high').length,
      deduplicatedRules: topRisks.length,
      partyCount: documents.length,
    },
  };

  const actionPlan: ActionPlan = {
    tasks: [],
    level1Actions: collectActionTexts(raw.page5ActionSuggestions?.level1?.actions),
    level2Actions: collectActionTexts(raw.page5ActionSuggestions?.level2?.actions),
    level3Actions: collectActionTexts(raw.page5ActionSuggestions?.level3?.actions),
    responsibilityMatrix: collectResponsibilities(raw.page5ActionSuggestions),
  };

  const metadata: ReportMetadata = {
    taskId: String(raw.page6Appendix?.taskInfo?.taskId || result.traceId || '-'),
    reportId: String(result.caseId || raw.page6Appendix?.taskInfo?.taskId || '-'),
    generatedAt: String(raw.page6Appendix?.taskInfo?.reviewTime || result.report?.generatedAt || new Date().toLocaleString('zh-CN')),
    traceId: result.traceId,
    projectTarget: documents.map((item) => item.partyName).join('、') || '-',
    reviewType: documents.length > 2 ? '多文件比对' : '双文件比对',
    reviewScope: `${documents.length} 份文件`,
    systemVersion: String(raw.page6Appendix?.taskInfo?.modelVersion || 'drug-agent'),
    hitRules: Array.isArray(raw.page6Appendix?.ruleList)
      ? raw.page6Appendix.ruleList.map((rule: any) => ({
          ruleCode: String(rule.ruleCode || '-'),
          ruleName: translateCode(String(rule.ruleCode || rule.description || '-')),
          hitCount: 1,
          remark: humanizeText(String(rule.description || ''), aliasMap),
        }))
      : [],
  };

  return polishReportData(
    {
      executiveSummary,
      riskOverview: { topRisks, distributions },
      documents: attachRiskInfoToDocuments(documents, topRisks),
      evidences,
      actionPlan,
      metadata,
    },
    result,
  );
}

function transformReviewReport(result: DrugAgentResp): ReportData {
  const riskItems = result.report?.riskItems || [];
  const evidenceGroups = result.evidenceGroups || [];
  const documents = buildDocumentsFromNames(result.documentNames || [], result.documentIds || []);
  const aliasMap = buildAliasMap(documents, [], result);

  // V2: 风险等级标签（原型要求）
  const riskLevel = normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel);
  const riskLevelLabelMap: Record<string, string> = {
    high: '重大围标风险',
    medium: '中度围标风险',
    low: '轻度异常信号',
    safe: '暂未发现明显异常',
  };
  const riskLevelLabel = riskLevelLabelMap[riskLevel] || '待定风险';

  // V2: 重点风险判断（含规则编码）
  const topRisks = dedupeTopRisks(riskItems.map((item, index) => {
    const riskType = resolveRiskType(item);
    const ruleCode = item.reasonCodes?.[0] || '';
    const riskDesc = buildRiskDescription(item, riskType, aliasMap);
    return {
      rank: index + 1,
      ruleCode: ruleCode || undefined,
      riskName: normalizeRiskTitle(item.title, riskType),
      level: toRiskLevel(item.riskLevel || riskType),
      riskDesc,
      keyFact: riskDesc,
      whyReview: buildWhyReview(riskType),
      action: buildReviewAction(riskType),
    };
  })).slice(0, 3).map((item, index) => ({ ...item, rank: index + 1 }));

  // V2: 关键证据明细（含证据链ID/相似度/AI判定）
  const evidences = evidenceGroups.map((group, index) => {
    const category = resolveEvidenceCategory(group);
    const ruleCode = topRisks[0]?.ruleCode || 'UNKNOWN';
    const similarityValue = group.similarity != null ? (group.similarity * 100).toFixed(1) : undefined;
    const displayTitle = resolveRiskTypeLabel(category);
    const docAName = documents[0]?.partyName || '投标主体 A';
    const docBName = documents[1]?.partyName || '投标主体 B';
    const rawDocAContent = group.items?.[0]?.content || group.evidenceList?.[0]?.content || group.contentA || '';
    const rawDocBContent = group.items?.[1]?.content || group.evidenceList?.[1]?.content || group.contentB || '';
    return {
      evidenceId: `E${String(index + 1).padStart(2, '0')}`,
      type: category,
      level: toEvidenceLevel(inferEvidenceLevel(group)),
      evidenceChainId: `RULE-${ruleCode}-${String(index + 1).padStart(3, '0')}`,
      displayTitle,
      similarity: similarityValue ? `${similarityValue}%` : undefined,
      sourceType: resolveRiskTypeLabel(category),
      docAName,
      docBName,
      docASummary: buildEvidenceExcerpt(rawDocAContent, aliasMap),
      docBSummary: buildEvidenceExcerpt(rawDocBContent, aliasMap),
      docAContent: buildEvidenceSideContent(rawDocAContent, 'A', aliasMap),
      docBContent: buildEvidenceSideContent(rawDocBContent, 'B', aliasMap),
      comparisonFinding: buildEvidenceSummary(group, category, aliasMap),
      aiJudgment: buildAIJudgment(group, category, documents),
      reviewSuggestion: categoryAction(category),
    };
  });

  // V2: 风险总览 - 只展示有发现的风险
  const distributions = buildRiskDistribution(riskItems, evidenceGroups)
    .filter((item): item is NonNullable<typeof item> => !!item && (item.hitCount ?? 0) > 0)
    .map(item => ({
      riskType: item.riskType,
      found: true,
      foundDescription: '发现明显异常',
      needReview: true,
      needReviewText: '必须复核',
      brief: humanizeText(item.explanation || '', aliasMap),
    }));

  // V2: 结论正文
  const conclusionText = result.summary || result.report?.overview?.summary ||
    '本次比对已完成，但暂未生成明确审查结论。';

  // V2: 处置建议 - 任务清单
  const tasks = [
    { priority: 'high' as const, action: '复核报价明细和报价形成依据', role: '评标专家 / 招采人员', goal: '判断报价异常是否成立' },
    { priority: 'high' as const, action: '核查核心人员归属和授权关系', role: '风控 / 合规人员', goal: '判断团队重复是否合理' },
    { priority: 'medium' as const, action: '对关键相似段落做人工原文比对', role: '评标专家', goal: '判断是否超出通用模板范围' },
    { priority: 'medium' as const, action: '查询历史投标记录和历史模板', role: '招采管理员', goal: '判断是否存在长期协同或同源文件' },
    { priority: 'low' as const, action: '归档系统报告、人工复核意见和佐证材料', role: '项目负责人', goal: '形成可追溯审查闭环' },
  ];

  // 生成报告文档ID
  const now = new Date();
  const docId = `TSR-${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${result.traceId?.slice(-3) || Math.random().toString(36).slice(2, 5).toUpperCase()}`;

  return {
    executiveSummary: {
      conclusionText: humanizeText(conclusionText, aliasMap),
      riskLevelLabel,
      metrics: {
        effectiveHits: topRisks.filter(r => r.level === 'high').length || riskItems.length || 1,
        evidenceClusters: evidenceGroups.length || evidences.length,
        documentCount: documents.length,
      },
      reviewNote: '本报告用于辅助识别标书疑似围标、串标或非独立编制风险，不直接替代最终评审结论。',
    },
    riskOverview: {
      topRisks,
      distributions,
    },
    documents: documents.map((doc, index) => ({
      docCode: `文档 ${String.fromCharCode(65 + index)}`,
      docId: doc.docId?.startsWith('doc_') ? `DOC-${String(index + 1).padStart(3, '0')}` : doc.docId,
      partyName: doc.partyName,
      fileName: doc.fileName,
      docNature: index === 0 ? '审计主文档' : '关键参检文档',
      lastModifier: 'Admin_User',
      role: `${index === 0 ? '第一' : '第二'}份投标文件参与比对`,
    })),
    evidences,
    actionPlan: { tasks },
    metadata: {
      generatedAt: result.report?.generatedAt || new Date().toLocaleString('zh-CN'),
      reviewScope: `${documents.length} 份文件，${documents.length} 家投标主体`,
      documentId: docId,
    },
  };
}

/** 根据证据组和文档信息构建 AI 判定逻辑说明 */
function buildAIJudgment(group: EvidenceGroup, category: string, documents: DocumentIndex[]): string {
  const docAName = documents[0]?.partyName || '投标主体 A';
  const docBName = documents[1]?.partyName || '投标主体 B';
  const similarity = group.similarity != null ? (group.similarity * 100).toFixed(1) : '';
  const typeLabel = resolveRiskTypeLabel(category);
  const similarityText = similarity ? `，文本相似度约 ${similarity}%` : '';
  return `AI 复核结论：${docAName} 与 ${docBName} 在“${typeLabel}”维度存在明显一致性${similarityText}，建议结合原文上下文和人工复核意见确认是否升级处理。`;
}

function dedupeTopRisks(risks: RiskOverview['topRisks']): RiskOverview['topRisks'] {
  const merged = new Map<string, RiskOverview['topRisks'][number]>();
  risks.forEach((risk) => {
    const key = risk.ruleCode || risk.riskType || risk.riskName;
    const existing = merged.get(key);
    if (!existing) {
      merged.set(key, risk);
      return;
    }
    if (risk.riskDesc.length > existing.riskDesc.length) {
      merged.set(key, { ...risk, rank: existing.rank });
    }
  });
  return Array.from(merged.values());
}

function normalizeRiskTitle(title: string | undefined, riskType: string): string {
  const raw = String(title || '').trim();
  if (raw && !/^风险识别抄袭$/.test(raw)) {
    return raw;
  }
  return resolveRiskTypeLabel(riskType);
}

function buildRiskDescription(item: RiskItem, riskType: string, aliasMap: Record<string, string>): string {
  const summary = humanizeText(item.summary || '', aliasMap);
  const similarity = extractSimilarity(summary);
  const focus = extractQuotedFocus(summary);
  const similarityText = similarity ? `，相似度约 ${similarity}` : '';

  if (riskType === 'pricing') {
    return `多份投标文件的报价结构或分项报价呈现异常接近${similarityText}。建议重点核验报价形成依据，判断是否存在协同报价或统一测算底稿。`;
  }
  if (riskType === 'team') {
    return `不同投标主体在联系人、核心人员或授权关系上出现重叠线索${similarityText}。该类信息通常具有主体独立性，需确认是否存在同一控制或人员交叉使用。`;
  }
  if (riskType === 'template') {
    return `多份投标文件在目录结构、章节顺序或版式骨架上高度接近${similarityText}。若相似范围覆盖非标准化章节，应进一步核查是否源自同一模板或统一底稿。`;
  }
  if (riskType === 'text_similarity') {
    const focusText = focus ? `“${focus}”相关内容` : '关键条款、技术响应或风险处置表述';
    return `多份投标文件在${focusText}上高度一致${similarityText}。相似内容集中在需要各投标主体独立编制的表达区域，建议作为重点围标风险线索复核。`;
  }
  return summary || '系统识别到需要进一步复核的异常线索，建议结合原文、历史投标记录和外围材料确认风险是否成立。';
}

function buildWhyReview(riskType: string): string {
  return {
    pricing: '报价逻辑应能反映各主体独立测算过程。',
    team: '人员和授权关系是判断投标主体独立性的关键证据。',
    text_similarity: '非通用条款高度一致时，可能指向同源编制或协同修改。',
    template: '版式和结构同源需要结合章节内容判断是否超出公共模板范围。',
    auxiliary: '辅助线索需要与其他证据交叉验证后再定性。',
  }[normalizeRiskType(riskType)] || '辅助线索需要与其他证据交叉验证后再定性。';
}

function buildReviewAction(riskType: string): string {
  return {
    pricing: '调取报价明细、成本测算表和历史报价记录，核对异常接近项是否具备合理商业解释。',
    team: '核验人员社保、劳动合同、授权文件和项目履历，确认是否存在交叉任职或代持授权。',
    text_similarity: '对相似段落进行原文级比对，标注通用模板部分与个性化响应部分，重点复核后者是否同源。',
    template: '核对目录、页眉页脚、表格样式和章节顺序，并与招标文件模板区分公共格式与异常同源痕迹。',
    auxiliary: '补充历史投标、工商关系和文件流转记录，形成可解释的证据链后再升级处理。',
  }[normalizeRiskType(riskType)] || '补充历史投标、工商关系和文件流转记录，形成可解释的证据链后再升级处理。';
}

function extractSimilarity(text: string): string {
  const match = String(text || '').match(/相似度\s*(?:达|为|约)?\s*([0-9.]+%?)/);
  if (!match?.[1]) {
    return '';
  }
  return match[1].endsWith('%') ? match[1] : `${match[1]}%`;
}

function extractQuotedFocus(text: string): string {
  const quoted = String(text || '').match(/[“"]([^”"]{2,40})[”"]/);
  return quoted?.[1]?.trim() || '';
}

function polishReportData(data: ReportData, result: DrugAgentResp): ReportData {
  // V2/V3: 如果已经有结论正文（V2 conclusionText 或 V3 overallConclusion），直接返回
  if (data.executiveSummary?.conclusionText || data.executiveSummary?.overallConclusion) {
    return data;
  }
  // 否则走 transformReviewReport 路径
  return transformReviewReport(result);
}

function buildLegacyTopRisks(raw: LegacyReportData, aliasMap: Record<string, string>) {
  const risks = Array.isArray(raw.page1Summary?.coreRiskTop3) ? raw.page1Summary.coreRiskTop3 : [];
  const evidences = Array.isArray(raw.page3CoreEvidence?.evidenceList) ? raw.page3CoreEvidence.evidenceList : [];

  return risks.map((risk: any, index: number) => {
    const matchedEvidence = evidences.find((item: any) =>
      `${item.title || ''}${item.type || ''}`.includes(risk.title || ''),
    );
    const riskType = normalizeRiskType(risk.riskType || inferRiskTypeByText(risk.title || risk.summary || ''));
    return {
      rank: Number(risk.rank || index + 1),
      riskName: humanizeText(risk.title || '未命名风险', aliasMap),
      riskType: riskType as any,
      riskLevel: toRiskLevel(risk.level),
      description: humanizeText(risk.summary || matchedEvidence?.explanation || '存在需要重点复核的异常线索。', aliasMap),
      keyFact: humanizeText(
        matchedEvidence?.explanation || risk.summary || '系统已识别到需进一步确认的关键线索。',
        aliasMap,
      ),
      basis: humanizeReasons([matchedEvidence?.basis, translateCode(risk.title)].filter(Boolean) as string[]),
      action: humanizeText(risk.action || categoryAction(riskType), aliasMap),
    };
  });
}

function buildLegacyDistributions(raw: LegacyReportData, aliasMap: Record<string, string>) {
  const categories = Array.isArray(raw.page2RiskOverview?.riskCategories) ? raw.page2RiskOverview.riskCategories : [];
  return categories
    .filter((item: any) => Number(item.hitCount || 0) > 0)
    .map((item: any) => ({
      riskType: item.categoryName || resolveRiskTypeLabel(item.type),
      level: normalizeLevel(item.level),
      hitCount: Number(item.hitCount || 0),
      needReview: Boolean(item.needHumanReview),
      explanation: humanizeText(item.explanation || item.representativeEvidence || '', aliasMap),
    }));
}

function buildLegacyEvidences(
  raw: LegacyReportData,
  documents: DocumentIndex[],
  aliasMap: Record<string, string>,
): EvidenceChain[] {
  const evidenceList = Array.isArray(raw.page3CoreEvidence?.evidenceList) ? raw.page3CoreEvidence.evidenceList : [];
  const detail = raw.page4DetailComparison || {};
  const priceRows = Array.isArray(detail.priceComparison?.rows) ? detail.priceComparison.rows : [];
  const teamRows = Array.isArray(detail.teamComparison?.rows) ? detail.teamComparison.rows : [];
  const textRows = Array.isArray(detail.textHighlights) ? detail.textHighlights : [];
  const cursors = { price: 0, team: 0, text: 0 };

  return evidenceList.map((item: any, index: number) => {
    const evidenceType = normalizeEvidenceType(item.type || inferRiskTypeByText(item.title || item.explanation || ''));
    const comparison =
      evidenceType === 'price_diff'
        ? priceRows[cursors.price++]
        : evidenceType === 'team_diff'
          ? teamRows[cursors.team++]
          : textRows[cursors.text++];

    return {
      evidenceId: String(item.id || `E${String(index + 1).padStart(2, '0')}`),
      title: humanizeText(item.title || '关键证据', aliasMap),
      type: evidenceType as any,
      level: toEvidenceLevel(item.level),
      summary: humanizeText(item.explanation || item.title || '', aliasMap),
      analysis: humanizeText(comparison?.analysis || item.action || '建议结合原文和外围材料继续复核。', aliasMap),
      basis: humanizeReasons([item.basis].filter(Boolean) as string[]),
      docAName: documents[0]?.fileName || '文档 A',
      docBName: documents[1]?.fileName || '文档 B',
      diffPayload: {
        docA_id: documents[0]?.docId || '',
        docB_id: documents[1]?.docId || '',
        contentA: humanizeText(comparison?.textA || comparison?.docA || '-', aliasMap),
        contentB: humanizeText(comparison?.textB || comparison?.docB || '-', aliasMap),
        similarityScore: comparison?.similarity,
        divergence: comparison?.diff,
        diffVerdict: mapVerdict(comparison?.verdict),
      },
    };
  });
}

function buildDocuments(legacyDocuments: any[], result: DrugAgentResp): DocumentIndex[] {
  if (legacyDocuments.length > 0) {
    return legacyDocuments.map((doc: any, index: number) => ({
      docId: String(doc.docId || result.documentIds?.[index] || `doc_${index}`),
      docCode: String.fromCharCode(65 + index),
      partyName: inferPartyName(doc.party || doc.docName || result.documentNames?.[index] || '', index),
      fileName: String(doc.docName || result.documentNames?.[index] || `比对文件${index + 1}`),
      docRole: normalizeDocRole(doc.role || '比对文件'),
      hitRiskCount: 0,
      involvedRisks: [],
    }));
  }
  return buildDocumentsFromNames(result.documentNames || [], result.documentIds || []);
}

function buildDocumentsFromNames(documentNames: string[], documentIds: string[]): DocumentIndex[] {
  return documentNames.map((name, index) => ({
    docId: documentIds[index] || `doc_${index}`,
    docCode: String.fromCharCode(65 + index),
    partyName: inferPartyName(name, index),
    fileName: cleanFileName(name, index),
    docRole: '投标文件',
    hitRiskCount: 0,
    involvedRisks: [],
  }));
}

/** 清除系统内部标识（如 UPLOAD-0-xxx），只保留人类可读的文件名 */
function cleanFileName(name: string, index: number): string {
  if (!name) return `投标文件${index + 1}`;
  // 若文件名是系统生成的 ID 格式（如 UPLOAD-xxx、UUID）则用推断的公司名替代
  if (/^(UPLOAD|upload|FILE|file)[-_]/.test(name) || /^[0-9a-f-]{32,}/.test(name)) {
    return `投标文件${index + 1}`;
  }
  return name;
}

function attachRiskInfoToDocuments(documents: DocumentIndex[], topRisks: RiskOverview['topRisks']): DocumentIndex[] {
  const involvedRisks = [...new Set((topRisks || []).map((item) => item.riskName).filter(Boolean))];
  const hitRiskCount = involvedRisks.length;
  return documents.map((doc) => ({
    ...doc,
    hitRiskCount,
    involvedRisks,
  }));
}

function buildAliasMap(
  documents: DocumentIndex[],
  legacyDocuments: any[],
  result: DrugAgentResp,
): Record<string, string> {
  const map: Record<string, string> = {};
  documents.forEach((doc, index) => {
    const label = `${doc.docCode}：${doc.partyName}`;
    [doc.docId, result.documentIds?.[index], legacyDocuments[index]?.docId, legacyDocuments[index]?.internalId]
      .filter(Boolean)
      .forEach((alias) => {
        map[String(alias)] = label;
      });
  });
  return map;
}

function collectActionTexts(actions: any[]): string[] {
  return Array.isArray(actions)
    ? actions.map((item) => String(item?.action || '')).filter(Boolean)
    : [];
}

function collectResponsibilities(raw: any) {
  const levels = [raw?.level1, raw?.level2, raw?.level3].filter(Boolean);
  return levels.flatMap((level: any) =>
    (level?.actions || []).map((item: any) => ({
      action: String(item.action || ''),
      role: String(item.role || '待确认'),
      priority: normalizeLevel(item.priority || 'medium') as 'high' | 'medium' | 'low',
      remark: String(level?.objective || ''),
    })),
  );
}

function humanizeText(text: string | undefined, aliasMap: Record<string, string>): string {
  let value = String(text || '').trim();
  if (!value) {
    return '';
  }
  Object.entries(aliasMap).forEach(([alias, label]) => {
    value = value.split(alias).join(label);
  });
  // 去除系统内部 ID（如 UPLOAD-0-xxx）
  value = value.replace(/\b(UPLOAD|FILE|DOC)[-_]\d+[-_][a-zA-Z0-9]+\b/gi, '').trim();
  return value.replace(/\s+/g, ' ').trim();
}

function humanizeReasons(reasons: string[]): string {
  const normalized = reasons
    .flatMap((item) => String(item || '').split(/[、,，]/))
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const parts = item.split('/');
      if (parts.length > 1) {
        return parts.map((part) => translateCode(part)).join('、');
      }
      return translateCode(item);
    });

  return [...new Set(normalized)].filter(Boolean).join('；');
}

function translateCode(code: string): string {
  const trimmed = String(code || '').trim();
  if (!trimmed) {
    return '';
  }
  if (REASON_TEXT[trimmed]) {
    return REASON_TEXT[trimmed];
  }
  if (trimmed.includes('命中规则')) {
    return trimmed.replace(/W-[A-Z]\d/g, (value) => REASON_TEXT[value] || value);
  }
  return trimmed.replace(
    /(MULTI_RULE_CO_OCCURRENCE|MULTI_HIT_ACCUMULATION|CROSS_DOCUMENT_VALIDATION|CROSS_DOCUMENT_EVIDENCE|EVIDENCE_SUFFICIENT|HIGH_PRIORITY_RULE|SYNERGY_BONUS|EXEMPTION_DOWNGRADE|W-M1|W-M2|W-M3|W-M4|W-P1|W-P2|W-P3|W-P4|W-P5)/g,
    (value) => REASON_TEXT[value] || value,
  );
}

function normalizeLevel(level?: string): 'high' | 'medium' | 'low' | 'safe' {
  const value = String(level || '').toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  return 'safe';
}

function toRiskLevel(level?: string): 'high' | 'medium' | 'low' {
  const normalized = normalizeLevel(level);
  return normalized === 'safe' ? 'low' : normalized;
}

function toEvidenceLevel(level?: string): 'high' | 'medium' | 'low' {
  const normalized = normalizeLevel(level);
  return normalized === 'safe' ? 'low' : normalized;
}

function normalizeRiskType(type?: string): string {
  const value = String(type || '').toLowerCase();
  if (value.includes('pricing') || value.includes('报价')) return 'pricing';
  if (value.includes('team') || value.includes('人员') || value.includes('联系人')) return 'team';
  if (value.includes('template') || value.includes('同源') || value.includes('错误')) return 'template';
  if (value.includes('text') || value.includes('plagiarism') || value.includes('雷同') || value.includes('相似')) return 'text_similarity';
  return 'auxiliary';
}

function normalizeEvidenceType(type?: string): string {
  const riskType = normalizeRiskType(type);
  if (riskType === 'pricing') return 'price_diff';
  if (riskType === 'team') return 'team_diff';
  if (riskType === 'template') return 'structure_diff';
  if (riskType === 'text_similarity') return 'text_diff';
  return 'other';
}

function normalizeDocRole(role: string): string {
  if (role.includes('技术')) return '技术标';
  if (role.includes('商务')) return '商务标';
  return '投标文件';
}

function resolveRiskType(item: RiskItem): string {
  const text = `${item.riskType || ''} ${(item.reasonCodes || []).join(' ')} ${item.title || ''} ${item.summary || ''}`;
  return inferRiskTypeByText(text);
}

function resolveEvidenceCategory(group: EvidenceGroup): string {
  return inferRiskTypeByText(`${group.title || ''} ${group.summary || ''}`);
}

function inferEvidenceLevel(group: EvidenceGroup): 'high' | 'medium' | 'low' {
  if (group.similarity && group.similarity > 0.9) return 'high';
  if (group.similarity && group.similarity > 0.75) return 'medium';
  return 'low';
}

function inferRiskTypeByText(text: string): string {
  const value = String(text || '').toLowerCase();
  if (value.includes('报价')) return 'pricing';
  if (value.includes('团队') || value.includes('人员') || value.includes('联系人')) return 'team';
  if (value.includes('w-m4') || value.includes('模板') || value.includes('同源') || value.includes('错误复现')) return 'template';
  if (value.includes('w-p4') || value.includes('抄袭') || value.includes('雷同') || value.includes('相似') || value.includes('条款') || value.includes('方案')) return 'text_similarity';
  return 'auxiliary';
}

function resolveRiskTypeLabel(type: string): string {
  return {
    pricing: '报价异常',
    team: '核心团队重复',
    text_similarity: '关键条款相似',
    template: '模板同源',
    auxiliary: '辅助线索',
  }[normalizeRiskType(type)] || '其他异常';
}

function categoryAction(type: string): string {
  return {
    pricing: '优先核查报价编制依据、报价逻辑和历史报价记录。',
    team: '核验人员社保、任职关系、授权链和项目履历。',
    text_similarity: '人工对照原文，确认关键条款是否超出通用表达范围。',
    template: '排查是否存在同源模板、统一底稿或错误复现。',
    auxiliary: '结合外围材料补强证据链，再决定是否升级处理。',
  }[normalizeRiskType(type)] || '结合外围材料补强证据链，再决定是否升级处理。';
}

function inferPartyName(name: string, index: number): string {
  const normalized = String(name || '').replace(/\.(pdf|doc|docx|txt|md)$/i, '');
  const parts = normalized.split(/[_－-]/);
  const candidate = parts.find((part) => part && !part.includes('投标人') && !part.includes('标书') && !part.includes('测试'));
  return candidate?.trim() || `投标方${String.fromCharCode(65 + index)}`;
}

function buildDecisionAction(result: DrugAgentResp): string {
  const level = normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel);
  if (level === 'high') return '建议立即启动人工复核，并优先确认报价、团队和关键条款异常是否成立。';
  if (level === 'medium') return '建议补充核验关键条款、联系人信息和历史投标记录。';
  return '建议保留审查结果，并对关键章节做抽样复核。';
}

function buildRiskDistribution(riskItems: RiskItem[], evidenceGroups: EvidenceGroup[]): RiskOverview['distributions'] {
  const types = ['pricing', 'team', 'text_similarity', 'template', 'auxiliary'];
  return (types
    .map((type) => {
      const riskMatches = riskItems.filter((item) => resolveRiskType(item) === type);
      const evidenceMatches = evidenceGroups.filter((item) => resolveEvidenceCategory(item) === type);
      const total = riskMatches.length + evidenceMatches.length;
      if (!total) {
        return null;
      }
      return {
        riskType: resolveRiskTypeLabel(type),
        hitCount: total,
        explanation: riskMatches[0]?.summary || evidenceMatches[0]?.summary || `${resolveRiskTypeLabel(type)}，建议人工复核。`,
      };
    })
    .filter(Boolean) as unknown) as RiskOverview['distributions'];
}

function buildEvidenceExcerpt(text: string, aliasMap: Record<string, string>, maxLength = 180): string {
  const value = formatEvidenceText(text, aliasMap);
  if (!value) {
    return '（当前未返回对照原文）';
  }
  const normalized = value.replace(/\s+/g, ' ').trim();
  if (normalized.length <= maxLength) {
    return normalized;
  }
  return `${normalized.slice(0, maxLength - 1).trimEnd()}…`;
}

function buildEvidenceSideContent(text: string, side: 'A' | 'B', aliasMap: Record<string, string>): string {
  const raw = String(text || '').trim();
  if (!raw || /^（?暂无对照内容）?$/.test(raw)) {
    return side === 'A' ? '（当前未返回 A 侧原文）' : '（当前未返回 B 侧原文）';
  }
  return formatEvidenceText(raw, aliasMap);
}

function formatEvidenceText(text: string, aliasMap: Record<string, string>): string {
  let value = humanizeText(text, aliasMap);
  if (!value) {
    return '';
  }

  value = value
    .replace(/分值\s*=\s*/g, '评分：')
    .replace(/等级\s*=\s*/g, '风险等级：')
    .replace(/原因\s*=\s*([A-Z0-9_,-，、\s]+)/g, (_, codes: string) => `原因：${translateReasonCodes(codes)}`)
    .replace(/,\s*/g, '，');

  return value.replace(/\s+/g, ' ').trim();
}

function translateReasonCodes(rawCodes: string): string {
  return String(rawCodes || '')
    .split(/[，,、\s]+/)
    .map((code) => translateCode(code))
    .filter(Boolean)
    .join('、');
}

function buildEvidenceSummary(group: EvidenceGroup, category: string, aliasMap: Record<string, string>): string {
  const summary = humanizeText(group.summary || '', aliasMap);
  if (summary) {
    return summary;
  }
  return `发现 ${resolveRiskTypeLabel(category)} 异常信号，建议结合原文与上下文继续复核。`;
}

function mapVerdict(verdict?: string): string {
  const value = String(verdict || '').toLowerCase();
  if (value.includes('高度') || value.includes('异常')) return 'warning';
  if (value.includes('相似')) return 'fuzzy_match';
  if (value.includes('差值')) return 'anomaly_gap';
  return 'safe';
}
