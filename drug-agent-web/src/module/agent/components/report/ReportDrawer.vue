<template>
  <teleport to="body">
    <transition name="modal-fade">
      <div v-if="visible" class="modal-mask" @click.self="handleClose">
        <div class="modal-container">
          <div class="modal-header">
            <div class="header-left">
              <div class="header-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                </svg>
              </div>
              <div class="header-titles">
                <h3>标书审查决策报告</h3>
                <span class="trace-badge">TRACE: {{ store.currentResult?.traceId || '—' }}</span>
              </div>
            </div>

            <div class="header-actions">
              <button class="export-btn" @click="handleExportPdf">导出 PDF</button>
              <button class="close-btn" @click="handleClose" aria-label="关闭">×</button>
            </div>
          </div>

          <div class="modal-body doc-wrapper">
            <FormalReportDocument :data="reportData || undefined" />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import type { DrugAgentResp, EvidenceGroup, RiskItem } from '../../types/agent';
import type {
  ReportData,
  ExecutiveSummary,
  RiskOverview as RiskOverviewType,
  DocumentIndex,
  EvidenceChain,
  ActionPlan,
  ReportMetadata
} from '../../types/report.types';
import FormalReportDocument from './FormalReportDocument.vue';
import { exportElementToPdf } from '../../utils/pdfExporter';

const store = useAgentStore();

const visible = computed({
  get: () => !!store.currentResult,
  set: (value) => {
    if (!value) store.setCurrentResult(null);
  },
});

const reportData = computed<ReportData | null>(() => {
  const result = store.currentResult as DrugAgentResp | null;
  if (!result) return null;
  if (result.reportData) return result.reportData;
  if (result.report) return transformReportToReportData(result);
  return null;
});

function handleClose() {
  store.setCurrentResult(null);
}

async function handleExportPdf() {
  const result = store.currentResult as DrugAgentResp | null;
  const data = reportData.value;
  if (!result || !data) return;

  const topRisks = (data.riskOverview.topRisks || [])
    .map(
      (risk) => `
        <div class="risk-card">
          <div class="risk-top">
            <span>风险 ${risk.rank}</span>
            <span class="badge">${levelLabel(risk.riskLevel)}</span>
          </div>
          <h3>${escapeHtml(risk.riskName)}</h3>
          <p>${escapeHtml(risk.description)}</p>
          <div class="muted">建议动作：${escapeHtml(risk.action)}</div>
        </div>
      `
    )
    .join('');

  const actions = (data.actionPlan.level1Actions || [])
    .map(
      (item, index) => `
        <li><strong>${index + 1}.</strong> ${escapeHtml(item)}</li>
      `
    )
    .join('');

  const documents = (data.documents || [])
    .map(
      (doc) => `
        <div class="doc-card">
          <div class="doc-tag">${doc.docCode}</div>
          <div>
            <div class="doc-party">${escapeHtml(doc.partyName)}</div>
            <div>${escapeHtml(doc.fileName)}</div>
            <div class="muted">${escapeHtml(doc.docRole)}</div>
          </div>
        </div>
      `
    )
    .join('');

  const html = `<!DOCTYPE html>
  <html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <title>标书审查决策报告</title>
    <style>
      * { box-sizing: border-box; }
      body { margin: 0; padding: 32px; font-family: "PingFang SC", "Microsoft YaHei", sans-serif; color: #0f172a; background: #ffffff; }
      h1, h2, h3, p { margin: 0; }
      .header { margin-bottom: 24px; }
      .trace { margin-top: 8px; color: #64748b; font-size: 12px; }
      .hero { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 16px; padding: 20px; border-radius: 20px; background: linear-gradient(135deg, #fff5f5, #ffffff); border: 1px solid #fecaca; margin-bottom: 24px; }
      .level { display: inline-block; padding: 6px 12px; border-radius: 999px; background: #fee2e2; color: #b91c1c; font-weight: 700; font-size: 12px; margin-bottom: 10px; }
      .hero-title { font-size: 24px; line-height: 1.5; margin-bottom: 12px; }
      .hero-action { padding: 12px 14px; border-radius: 14px; background: #ffffff; color: #334155; line-height: 1.7; }
      .metrics { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
      .metric { padding: 16px; border-radius: 16px; background: #ffffff; border: 1px solid #e2e8f0; }
      .metric-label { display: block; color: #64748b; font-size: 12px; margin-bottom: 8px; }
      .metric-value { font-size: 30px; font-weight: 800; }
      .section { margin-bottom: 24px; }
      .section-title { font-size: 18px; font-weight: 800; margin-bottom: 12px; }
      .risk-grid, .doc-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
      .risk-card, .doc-card { padding: 16px; border: 1px solid #e2e8f0; border-radius: 16px; background: #ffffff; }
      .risk-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 12px; color: #64748b; }
      .badge { padding: 4px 8px; border-radius: 999px; background: #fee2e2; color: #b91c1c; font-weight: 700; }
      .risk-card h3 { margin-bottom: 10px; font-size: 18px; }
      .risk-card p { line-height: 1.7; color: #334155; margin-bottom: 10px; }
      .doc-card { display: flex; gap: 12px; align-items: flex-start; }
      .doc-tag { width: 32px; height: 32px; border-radius: 10px; background: #0f172a; color: #ffffff; display: flex; align-items: center; justify-content: center; font-weight: 800; flex-shrink: 0; }
      .doc-party { font-weight: 700; margin-bottom: 6px; }
      ul { margin: 0; padding-left: 18px; }
      li { margin-bottom: 10px; line-height: 1.7; }
      .muted { color: #64748b; font-size: 12px; }
    </style>
  </head>
  <body>
    <div class="header">
      <h1>标书审查决策报告</h1>
      <div class="trace">TRACE: ${escapeHtml(result.traceId || '-')}</div>
    </div>
    <section class="hero">
      <div>
        <div class="level">${levelLabel(data.executiveSummary.riskLevel)}</div>
        <div class="hero-title">${escapeHtml(data.executiveSummary.overallConclusion)}</div>
        <div class="hero-action">${escapeHtml(data.executiveSummary.recommendedAction || '')}</div>
      </div>
      <div class="metrics">
        <div class="metric"><span class="metric-label">风险分</span><span class="metric-value">${data.executiveSummary.riskScore}</span></div>
        <div class="metric"><span class="metric-label">涉及文档</span><span class="metric-value">${data.executiveSummary.metrics.documentCount}</span></div>
        <div class="metric"><span class="metric-label">核心证据</span><span class="metric-value">${data.executiveSummary.metrics.coreEvidenceCount}</span></div>
        <div class="metric"><span class="metric-label">命中规则</span><span class="metric-value">${data.executiveSummary.metrics.deduplicatedRules}</span></div>
      </div>
    </section>
    <section class="section">
      <div class="section-title">核心风险 Top 3</div>
      <div class="risk-grid">${topRisks}</div>
    </section>
    <section class="section">
      <div class="section-title">涉及文档</div>
      <div class="doc-grid">${documents}</div>
    </section>
    <section class="section">
      <div class="section-title">建议处置动作</div>
      <ul>${actions}</ul>
    </section>
  </body>
  </html>`;

  // 创建临时容器用于 PDF 导出
  const container = document.createElement('div');
  container.innerHTML = html;
  container.style.position = 'absolute';
  container.style.left = '-9999px';
  container.style.top = '0';
  container.style.width = '210mm';
  document.body.appendChild(container);

  try {
    await exportElementToPdf(container, `标书审查报告_${result.traceId || Date.now()}`);
  } finally {
    document.body.removeChild(container);
  }
}

function transformReportToReportData(result: DrugAgentResp): ReportData {
  const riskItems = result.report?.riskItems || [];
  const evidenceGroups = result.evidenceGroups || [];

  // 构建涉及文档的真实风险统计
  const docs = (result.documentNames || []).map((name, index) => {
    const docId = result.documentIds?.[index] || `doc_${index}`;
    // 统计该文档涉及的风险和证据
    const relatedRisks = riskItems.filter(item => {
      const itemText = `${item.title || ''} ${item.summary || ''}`;
      return itemText.includes(name) || item.evidenceTitles?.some(t => t.includes(name));
    });
    const relatedEvidence = evidenceGroups.filter(g =>
      g.items?.some(item => item.source?.includes(name)) ||
      groupTitleIncludes(g, name)
    );
    const riskTypes = [...new Set([
      ...relatedRisks.map(r => resolveRiskType(r)),
      ...relatedEvidence.map(g => resolveEvidenceCategory(g))
    ])];

    return {
      id: docId,
      docCode: String.fromCharCode(65 + index),
      partyName: inferPartyName(name, index),
      fileName: name,
      docRole: '投标文件',
      hitRiskCount: relatedRisks.length + relatedEvidence.length,
      involvedRisks: riskTypes.map(t => categoryName(t))
    };
  });

  // 计算真实风险分布
  const distributions = buildRiskDistribution(riskItems, evidenceGroups);

  const executiveSummary: ExecutiveSummary = {
    riskLevel: normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel) as any,
    riskScore: result.score || result.report?.overview?.score || 0,
    overallConclusion: result.summary || result.report?.overview?.summary || '本次比对已完成，但暂未生成明确审查结论。',
    recommendedAction: buildDecisionAction(result),
    metrics: {
      coreEvidenceCount: evidenceGroups.length,
      highConfidenceHits: riskItems.filter(i => normalizeLevel(i.riskLevel) === 'high').length,
      deduplicatedRules: [...new Set(riskItems.map(i => resolveRiskType(i)))].length,
      documentCount: docs.length,
      partyCount: docs.length
    }
  };

  // 构建 Top 风险，优先使用真实证据信息
  const topRisks = riskItems.slice(0, 3).map((item, index) => {
    const evidenceForRisk = evidenceGroups.filter(g =>
      `${g.title || ''} ${g.summary || ''}`.includes(item.title || '')
    )[0];
    const realKeyFact = evidenceForRisk
      ? `相似度${evidenceForRisk.similarity ? Math.round(evidenceForRisk.similarity * 100) : '-'}%，${evidenceForRisk.summary || ''}`
      : item.summary || '存在需要重点复核的异常线索';

    return {
      rank: index + 1,
      riskName: item.title || '未命名风险',
      riskType: resolveRiskType(item) as any,
      riskLevel: normalizeLevel(item.riskLevel) as any,
      description: item.summary || '存在需要重点复核的异常线索。',
      keyFact: realKeyFact,
      basis: item.reasonCodes?.join('；') || categoryName(resolveRiskType(item)),
      action: item.recommendations?.[0] || categoryAction(resolveRiskType(item))
    };
  });

  const riskOverview: RiskOverviewType = {
    topRisks,
    distributions
  };

  const actionPlan: ActionPlan = {
    level1Actions: [
      '人工复核核心证据，优先确认报价异常、团队重合和关键条款相似是否成立。',
      '核查投标主体是否由独立团队编制，确认是否满足升级处理条件。'
    ],
    level2Actions: [
      '核查工商关联关系、人员社保归属、授权关系和联系方式。',
      '补充查看历史投标记录和同类项目资料。'
    ],
    level3Actions: [
      '对升级事项完成合规留档，并保留后续核验过程记录。'
    ],
    responsibilityMatrix: [
      { action: '人工复核核心证据', role: '评标专家', priority: 'high', remark: '优先确认报价异常、团队重合' },
      { action: '核查主体关联关系', role: '风控专员', priority: 'high', remark: '工商、社保、授权链' },
      { action: '历史记录追溯', role: '招采管理员', priority: 'medium', remark: '查看同类项目投标记录' }
    ]
  };

  const evidences: EvidenceChain[] = evidenceGroups.map((group, index) => ({
    evidenceId: `E${String(index + 1).padStart(2, '0')}`,
    title: group.title || '异常证据',
    type: resolveEvidenceType(group),
    level: inferEvidenceLevel(group),
    summary: group.summary || '',
    analysis: categoryAction(resolveEvidenceCategory(group)),
    diffPayload: {
      docA_id: '',
      docB_id: '',
      contentA: group.items?.[0]?.content || group.contentA || '',
      contentB: group.items?.[1]?.content || group.contentB || '',
      similarityScore: group.similarity ? `${Math.round(group.similarity * 100)}%` : undefined,
      divergence: undefined,
      diffVerdict: group.similarity && group.similarity > 0.9 ? 'warning' : group.similarity ? 'fuzzy_match' : 'safe'
    }
  }));

  const metadata: ReportMetadata = {
    taskId: result.traceId || '-',
    reportId: result.report?.reportId || '-',
    generatedAt: new Date().toLocaleString('zh-CN'),
    projectTarget: result.report?.projectName || (result.documentNames?.[0] || '-').split(/[_－-]/)[0],
    reviewType: docs.length > 2 ? '多文档比对' : '双文档比对',
    reviewScope: `${docs.length} 份文件，${docs.length} 家投标主体`,
    systemVersion: 'v3-standard',
    hitRules: riskItems.map((item, index) => ({
      ruleCode: item.reasonCodes?.[0] || `RULE-${index + 1}`,
      ruleName: item.title || '风险规则',
      hitCount: 1,
      remark: item.summary || ''
    }))
  };

  return {
    executiveSummary,
    riskOverview,
    documents: docs,
    evidences,
    actionPlan,
    metadata
  };
}
function buildRiskDistribution(riskItems: RiskItem[], evidenceGroups: EvidenceGroup[]): RiskOverviewType['distributions'] {
  const typeMap: Record<string, { label: string; extractor: (item: RiskItem | EvidenceGroup) => boolean }> = {
    pricing: { label: '报价风险', extractor: (item) => resolveRiskType(item as RiskItem) === 'pricing' || resolveEvidenceCategory(item as EvidenceGroup) === 'pricing' },
    team: { label: '团队风险', extractor: (item) => resolveRiskType(item as RiskItem) === 'team' || resolveEvidenceCategory(item as EvidenceGroup) === 'team' },
    text_similarity: { label: '文本相似风险', extractor: (item) => resolveRiskType(item as RiskItem) === 'text_similarity' || resolveEvidenceCategory(item as EvidenceGroup) === 'text_similarity' },
    template: { label: '模板同源风险', extractor: (item) => resolveRiskType(item as RiskItem) === 'template' || resolveEvidenceCategory(item as EvidenceGroup) === 'template' },
    auxiliary: { label: '其他辅助风险', extractor: (item) => resolveRiskType(item as RiskItem) === 'auxiliary' || resolveEvidenceCategory(item as EvidenceGroup) === 'auxiliary' },
  };

  const distributions: RiskOverviewType['distributions'] = [];
  const allItems = [...riskItems, ...evidenceGroups];

  for (const [type, config] of Object.entries(typeMap)) {
    const matchedItems = allItems.filter(item => config.extractor(item));
    if (matchedItems.length === 0) continue; // 只展示真实命中的风险

    const levels = matchedItems.map(item => {
      if ('riskLevel' in item) return normalizeLevel(item.riskLevel);
      if ('similarity' in item) return inferEvidenceLevel(item);
      return 'safe';
    });
    const maxLevel = levels.reduce((max, l) => severity(l) > severity(max) ? l : max, 'safe');

    distributions.push({
      riskType: config.label,
      level: maxLevel as any,
      hitCount: matchedItems.length,
      needReview: matchedItems.length > 0,
      explanation: matchedItems[0] && 'summary' in matchedItems[0]
        ? matchedItems[0].summary
        : `${config.label}当前命中 ${matchedItems.length} 条线索，建议结合证据链人工复核。`
    });
  }

  return distributions;
}



function levelLabel(level?: string) {
  return {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '未见明显异常',
  }[normalizeLevel(level)] || '未见明显异常';
}

function severity(level?: string) {
  return {
    safe: 0,
    low: 1,
    medium: 2,
    high: 3,
  }[normalizeLevel(level)] || 0;
}

function escapeHtml(value: string | undefined | null) {
  if (!value) return '';
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function normalizeLevel(level?: string) {
  if (!level) return 'safe';
  const value = level.toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  return 'safe';
}

function resolveRiskType(item: RiskItem) {
  const title = `${item.title || ''} ${item.summary || ''}`;
  if ((item.riskType || '').toLowerCase() === 'pricing' || title.includes('报价')) return 'pricing';
  if (title.includes('团队') || title.includes('人员') || title.includes('联系人')) return 'team';
  if (title.includes('模板') || title.includes('同源') || title.includes('错误复现')) return 'template';
  if (title.includes('雷同') || title.includes('相似') || title.includes('条款') || title.includes('方案')) return 'text_similarity';
  return 'auxiliary';
}

function resolveEvidenceCategory(group: EvidenceGroup) {
  const text = `${group.title || ''} ${group.summary || ''}`.toLowerCase();
  if (text.includes('报价')) return 'pricing';
  if (text.includes('团队') || text.includes('联系人') || text.includes('人员')) return 'team';
  if (text.includes('模板') || text.includes('同源') || text.includes('错误复现')) return 'template';
  if (text.includes('雷同') || text.includes('相似') || text.includes('条款') || text.includes('方案')) return 'text_similarity';
  return 'auxiliary';
}

function resolveEvidenceType(group: EvidenceGroup): EvidenceChain['type'] {
  const category = resolveEvidenceCategory(group);
  if (category === 'pricing') return 'price_diff';
  if (category === 'team') return 'team_diff';
  if (category === 'template') return 'structure_diff';
  if (category === 'text_similarity') return 'text_diff';
  return 'other';
}

function inferEvidenceLevel(group: EvidenceGroup): 'high' | 'medium' | 'low' {
  if (group.similarity && group.similarity > 0.9) return 'high';
  if (group.similarity && group.similarity > 0.75) return 'medium';
  return 'low';
}

function categoryName(type: string) {
  return {
    pricing: '报价风险',
    team: '团队风险',
    text_similarity: '文本相似风险',
    template: '模板同源风险',
    auxiliary: '其他辅助风险',
  }[type] || '其他辅助风险';
}

function categoryAction(type: string) {
  return {
    pricing: '优先核查报价编制依据、报价逻辑和历史报价记录。',
    team: '核验人员社保、任职关系、授权链和项目履历。',
    text_similarity: '人工对照原文，确认关键条款是否超出通用表达范围。',
    template: '排查是否存在同源模板、统一底稿或错误复现。',
    auxiliary: '结合外围材料补强证据链，再决定是否升级处理。',
  }[type] || '结合外围材料补强证据链，再决定是否升级处理。';
}

function inferPartyName(name: string, index: number) {
  const parts = name.replace(/\.(pdf|doc|docx|txt)$/i, '').split(/[_－-]/);
  const candidate = parts.find((part) => part && !part.includes('投标人') && !part.includes('标书'));
  return candidate?.trim() || `投标方${String.fromCharCode(65 + index)}`;
}

function buildDecisionAction(result: DrugAgentResp) {
  const level = normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel);
  if (level === 'high') return '立即人工复核';
  if (level === 'medium') return '补充核验';
  return '正常流转';
}

function groupTitleIncludes(group: EvidenceGroup, name: string): boolean {
  if (!name) return false;
  const lowerName = name.toLowerCase();
  return (group.title || '').toLowerCase().includes(lowerName) ||
         (group.summary || '').toLowerCase().includes(lowerName);
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(8px);
}

.modal-container {
  width: min(1180px, 100%);
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  box-shadow: 0 30px 100px rgba(15, 23, 42, 0.28);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #e2e8f0;
}

.header-left,
.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  color: #1d4ed8;
}

.header-titles h3 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #0f172a;
}

.trace-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.tab-group {
  max-width: 100%;
}

.export-btn,
.close-btn {
  border: none;
  cursor: pointer;
}

.export-btn {
  padding: 10px 14px;
  border-radius: 12px;
  background: #0f172a;
  color: #ffffff;
  font-weight: 700;
}

.close-btn {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #f1f5f9;
  color: #475569;
  font-size: 24px;
  line-height: 1;
}

.modal-body {
  overflow: hidden;
  padding: 0;
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
}

.doc-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-active .modal-container,
.modal-fade-leave-active .modal-container {
  transition: transform 0.24s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-from .modal-container,
.modal-fade-leave-to .modal-container {
  transform: translateY(12px) scale(0.98);
}

@media (max-width: 960px) {
  .modal-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .modal-mask {
    padding: 0;
  }

  .modal-container {
    max-height: 100vh;
    border-radius: 0;
  }

  .modal-body {
    padding: 16px;
  }
}
</style>
