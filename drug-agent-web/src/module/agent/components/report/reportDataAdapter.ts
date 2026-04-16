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
  'W-P1': '技术方案雷同',
  'W-P2': '实施路径或结构高度相似',
  'W-P3': '服务承诺雷同',
  'W-P4': '模板同源',
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
    metrics: {
      coreEvidenceCount: Number(raw.page1Summary?.coreEvidenceCount || evidences.length || 0),
      highConfidenceHits: topRisks.filter((item: any) => item.riskLevel === 'high').length,
      deduplicatedRules: topRisks.length,
      documentCount: documents.length,
      partyCount: documents.length,
    },
  };

  const actionPlan: ActionPlan = {
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

  const topRisks = riskItems.slice(0, 3).map((item, index) => {
    const riskType = resolveRiskType(item);
    return {
      rank: index + 1,
      riskName: humanizeText(item.title || '未命名风险', aliasMap),
      riskType: riskType as any,
      riskLevel: toRiskLevel(item.riskLevel || riskType),
      description: humanizeText(item.summary || '存在需要重点复核的异常线索。', aliasMap),
      keyFact: humanizeText(item.summary || '系统已识别到异常线索。', aliasMap),
      basis: humanizeReasons(item.reasonCodes || [resolveRiskTypeLabel(riskType)]),
      action: humanizeText(item.recommendations?.[0] || categoryAction(riskType), aliasMap),
    };
  });

  const evidences: EvidenceChain[] = evidenceGroups.map((group, index) => ({
    evidenceId: `E${String(index + 1).padStart(2, '0')}`,
    title: humanizeText(group.title || '异常证据', aliasMap),
    type: resolveEvidenceType(group),
    level: toEvidenceLevel(inferEvidenceLevel(group)),
    summary: humanizeText(group.summary || '', aliasMap),
    analysis: categoryAction(resolveEvidenceCategory(group)),
    basis: resolveRiskTypeLabel(resolveEvidenceCategory(group)),
    docAName: documents[0]?.fileName || '文档 A',
    docBName: documents[1]?.fileName || '文档 B',
    diffPayload: {
      docA_id: documents[0]?.id || '',
      docB_id: documents[1]?.id || '',
      contentA: humanizeText(group.items?.[0]?.content || group.contentA || '-', aliasMap),
      contentB: humanizeText(group.items?.[1]?.content || group.contentB || '-', aliasMap),
      similarityScore: group.similarity ? `${Math.round(group.similarity * 100)}%` : undefined,
      divergence: undefined,
      diffVerdict: group.similarity && group.similarity > 0.9 ? 'warning' : group.similarity ? 'fuzzy_match' : 'safe',
    },
  }));

  return polishReportData(
    {
      executiveSummary: {
        riskLevel: normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel),
        riskScore: Number(result.score || result.report?.overview?.score || 0),
        overallConclusion: humanizeText(
          result.summary || result.report?.overview?.summary || '本次比对已完成，但暂未生成明确审查结论。',
          aliasMap,
        ),
        recommendedAction: buildDecisionAction(result),
        metrics: {
          coreEvidenceCount: evidences.length,
          highConfidenceHits: topRisks.filter((item: any) => item.riskLevel === 'high').length,
          deduplicatedRules: topRisks.length,
          documentCount: documents.length,
          partyCount: documents.length,
        },
      },
      riskOverview: {
        topRisks,
        distributions: buildRiskDistribution(riskItems, evidenceGroups).map((item) => ({
          ...item,
          explanation: humanizeText(item.explanation, aliasMap),
        })),
      },
      documents: attachRiskInfoToDocuments(documents, topRisks),
      evidences,
      actionPlan: {
        level1Actions: [
          '人工复核核心证据，优先确认报价异常、团队重合和关键条款相似是否成立。',
          '核查投标主体是否由独立团队编制，确认是否满足升级处理条件。',
        ],
        level2Actions: [
          '核查工商关联关系、人员社保归属、授权关系和联系方式。',
          '补充查看历史投标记录和同类项目资料。',
        ],
        level3Actions: ['对升级事项完成合规留档，并保留后续核验过程记录。'],
        responsibilityMatrix: [
          { action: '人工复核核心证据', role: '评标专家', priority: 'high', remark: '优先确认报价异常、团队重合' },
          { action: '核查主体关联关系', role: '风控专员', priority: 'high', remark: '工商、社保、授权链' },
          { action: '历史记录追溯', role: '招采管理员', priority: 'medium', remark: '查看同类项目投标记录' },
        ],
      },
      metadata: {
        taskId: result.traceId || '-',
        reportId: result.caseId || '-',
        generatedAt: result.report?.generatedAt || new Date().toLocaleString('zh-CN'),
        traceId: result.traceId,
        projectTarget: documents.map((item) => item.partyName).join('、') || '-',
        reviewType: documents.length > 2 ? '多文件比对' : '双文件比对',
        reviewScope: `${documents.length} 份文件`,
        systemVersion: 'drug-agent',
        hitRules: riskItems.map((item, index) => ({
          ruleCode: item.reasonCodes?.[0] || `RULE-${index + 1}`,
          ruleName: translateCode(item.reasonCodes?.[0] || item.title || `RULE-${index + 1}`),
          hitCount: 1,
          remark: humanizeText(item.summary || '', aliasMap),
        })),
      },
    },
    result,
  );
}

function polishReportData(data: ReportData, result: DrugAgentResp): ReportData {
  const documents = Array.isArray(data.documents) ? data.documents : [];
  const aliasMap = buildAliasMap(documents, [], result);

  return {
    ...data,
    executiveSummary: {
      ...data.executiveSummary,
      riskLevel: normalizeLevel(data.executiveSummary?.riskLevel),
      overallConclusion: humanizeText(data.executiveSummary?.overallConclusion || '', aliasMap),
      recommendedAction: humanizeText(data.executiveSummary?.recommendedAction || '', aliasMap),
    },
    riskOverview: {
      topRisks: (data.riskOverview?.topRisks || []).map((item, index) => ({
        ...item,
        rank: item.rank || index + 1,
        riskName: humanizeText(item.riskName, aliasMap),
        riskType: normalizeRiskType(item.riskType) as any,
        riskLevel: toRiskLevel(item.riskLevel),
        description: humanizeText(item.description, aliasMap),
        keyFact: humanizeText(item.keyFact, aliasMap),
        basis: humanizeReasons(Array.isArray(item.basis) ? (item.basis as any) : [item.basis].filter(Boolean) as string[]),
        action: humanizeText(item.action, aliasMap),
      })),
      distributions: (data.riskOverview?.distributions || []).map((item) => ({
        ...item,
        level: normalizeLevel(item.level),
        explanation: humanizeText(item.explanation, aliasMap),
      })),
    },
    documents: attachRiskInfoToDocuments(documents, data.riskOverview?.topRisks || []),
    evidences: (data.evidences || []).map((item) => ({
        ...item,
        type: normalizeEvidenceType(item.type) as any,
        level: toEvidenceLevel(item.level),
      title: humanizeText(item.title, aliasMap),
      summary: humanizeText(item.summary, aliasMap),
      analysis: humanizeText(item.analysis, aliasMap),
      basis: humanizeReasons([item.basis].filter(Boolean) as string[]),
      docAName: item.docAName || documents[0]?.fileName || '文档 A',
      docBName: item.docBName || documents[1]?.fileName || '文档 B',
      diffPayload: {
        ...item.diffPayload,
        contentA: humanizeText(item.diffPayload?.contentA || '-', aliasMap),
        contentB: humanizeText(item.diffPayload?.contentB || '-', aliasMap),
      },
    })),
    actionPlan: {
      ...data.actionPlan,
      level1Actions: (data.actionPlan?.level1Actions || []).map((item) => humanizeText(item, aliasMap)),
      level2Actions: (data.actionPlan?.level2Actions || []).map((item) => humanizeText(item, aliasMap)),
      level3Actions: (data.actionPlan?.level3Actions || []).map((item) => humanizeText(item, aliasMap)),
      responsibilityMatrix: (data.actionPlan?.responsibilityMatrix || []).map((item) => ({
        ...item,
        action: humanizeText(item.action, aliasMap),
      })),
    },
    metadata: {
      ...data.metadata,
      traceId: data.metadata?.traceId || result.traceId,
    },
  };
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
        docA_id: documents[0]?.id || '',
        docB_id: documents[1]?.id || '',
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
      id: String(doc.docId || result.documentIds?.[index] || `doc_${index}`),
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
    id: documentIds[index] || `doc_${index}`,
    docCode: String.fromCharCode(65 + index),
    partyName: inferPartyName(name, index),
    fileName: name,
    docRole: '投标文件',
    hitRiskCount: 0,
    involvedRisks: [],
  }));
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
    [doc.id, result.documentIds?.[index], legacyDocuments[index]?.docId, legacyDocuments[index]?.internalId]
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
    /(MULTI_RULE_CO_OCCURRENCE|MULTI_HIT_ACCUMULATION|CROSS_DOCUMENT_VALIDATION|CROSS_DOCUMENT_EVIDENCE|EVIDENCE_SUFFICIENT|HIGH_PRIORITY_RULE|SYNERGY_BONUS|EXEMPTION_DOWNGRADE|W-M1|W-M2|W-M3|W-P1|W-P2|W-P3|W-P4|W-P5)/g,
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
  const text = `${item.riskType || ''} ${item.title || ''} ${item.summary || ''}`;
  return inferRiskTypeByText(text);
}

function resolveEvidenceCategory(group: EvidenceGroup): string {
  return inferRiskTypeByText(`${group.title || ''} ${group.summary || ''}`);
}

function resolveEvidenceType(group: EvidenceGroup): EvidenceChain['type'] {
  return normalizeEvidenceType(resolveEvidenceCategory(group)) as EvidenceChain['type'];
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
  if (value.includes('模板') || value.includes('同源') || value.includes('错误复现')) return 'template';
  if (value.includes('雷同') || value.includes('相似') || value.includes('条款') || value.includes('方案')) return 'text_similarity';
  return 'auxiliary';
}

function resolveRiskTypeLabel(type: string): string {
  return {
    pricing: '报价异常或报价模式高度接近',
    team: '核心团队或联系人信息存在交叉复用',
    text_similarity: '关键条款、技术方案或服务承诺高度相似',
    template: '目录结构、模板来源或错误表述存在同源特征',
    auxiliary: '存在可增强综合判断的辅助异常信号',
  }[normalizeRiskType(type)] || '存在需要进一步核实的异常线索';
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
  return types
    .map((type) => {
      const riskMatches = riskItems.filter((item) => resolveRiskType(item) === type);
      const evidenceMatches = evidenceGroups.filter((item) => resolveEvidenceCategory(item) === type);
      const total = riskMatches.length + evidenceMatches.length;
      if (!total) {
        return null;
      }
      const levels = [
        ...riskMatches.map((item) => normalizeLevel(item.riskLevel)),
        ...evidenceMatches.map((item) => inferEvidenceLevel(item)),
      ];
      const level = levels.includes('high') ? 'high' : levels.includes('medium') ? 'medium' : 'low';
      return {
        riskType: resolveRiskTypeLabel(type).replace('或', ' / '),
        level: level as 'high' | 'medium' | 'low' | 'safe',
        hitCount: total,
        needReview: true,
        explanation: riskMatches[0]?.summary || evidenceMatches[0]?.summary || `${resolveRiskTypeLabel(type)}，建议人工复核。`,
      };
    })
    .filter(Boolean) as RiskOverview['distributions'];
}

function mapVerdict(verdict?: string): EvidenceChain['diffPayload']['diffVerdict'] {
  const value = String(verdict || '').toLowerCase();
  if (value.includes('高度') || value.includes('异常')) return 'warning';
  if (value.includes('相似')) return 'fuzzy_match';
  if (value.includes('差值')) return 'anomaly_gap';
  return 'safe';
}
