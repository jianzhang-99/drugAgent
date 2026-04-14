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
              <el-radio-group v-model="activeTab" size="small" class="tab-group">
                <el-radio-button label="summary">审查结论</el-radio-button>
                <el-radio-button label="risk">风险总览</el-radio-button>
                <el-radio-button label="evidence">核心证据</el-radio-button>
                <el-radio-button label="comparison">详细比对</el-radio-button>
                <el-radio-button label="action">处置建议</el-radio-button>
                <el-radio-button label="appendix">附录</el-radio-button>
              </el-radio-group>
              <button class="export-btn" @click="handleExportPdf">导出 PDF</button>
              <button class="close-btn" @click="handleClose" aria-label="关闭">×</button>
            </div>
          </div>

          <div class="modal-body">
            <ReportSummary v-if="activeTab === 'summary'" :data="reportData?.page1Summary" />
            <RiskOverview v-else-if="activeTab === 'risk'" :data="reportData?.page2RiskOverview" />
            <CoreEvidence v-else-if="activeTab === 'evidence'" :data="reportData?.page3CoreEvidence" />
            <DetailComparison v-else-if="activeTab === 'comparison'" :data="reportData?.page4DetailComparison" />
            <ActionSuggestions v-else-if="activeTab === 'action'" :data="reportData?.page5ActionSuggestions" />
            <Appendix v-else-if="activeTab === 'appendix'" :data="reportData?.page6Appendix" />
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
  Action,
  Page1Summary,
  Page2RiskOverview,
  Page3CoreEvidence,
  Page4DetailComparison,
  Page5ActionSuggestions,
  Page6Appendix,
  ReportData,
  RiskCategory,
} from '../../types/report.types';
import ReportSummary from './ReportSummary.vue';
import RiskOverview from './RiskOverview.vue';
import CoreEvidence from './CoreEvidence.vue';
import DetailComparison from './DetailComparison.vue';
import ActionSuggestions from './ActionSuggestions.vue';
import Appendix from './Appendix.vue';

const store = useAgentStore();
const activeTab = ref('summary');

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

  const topRisks = (data.page1Summary.coreRiskTop3 || [])
    .map(
      (risk) => `
        <div class="risk-card">
          <div class="risk-top">
            <span>风险 ${risk.rank}</span>
            <span class="badge">${levelLabel(risk.level)}</span>
          </div>
          <h3>${escapeHtml(risk.title)}</h3>
          <p>${escapeHtml(risk.summary)}</p>
          <div class="muted">建议动作：${escapeHtml(risk.action)}</div>
        </div>
      `
    )
    .join('');

  const actions = collectActions(data.page5ActionSuggestions)
    .slice(0, 6)
    .map(
      (item, index) => `
        <li><strong>${index + 1}.</strong> ${escapeHtml(item.action)}<span class="muted">（${escapeHtml(item.role)}｜${escapeHtml(item.priority)}）</span></li>
      `
    )
    .join('');

  const documents = (data.page1Summary.documents || [])
    .map(
      (doc, index) => `
        <div class="doc-card">
          <div class="doc-tag">${String.fromCharCode(65 + index)}</div>
          <div>
            <div class="doc-party">${escapeHtml(doc.party)}</div>
            <div>${escapeHtml(doc.docName)}</div>
            <div class="muted">${escapeHtml(doc.role)}｜内部编号：${escapeHtml(doc.internalId)}</div>
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
        <div class="level">${levelLabel(data.page1Summary.riskLevel)}</div>
        <div class="hero-title">${escapeHtml(data.page1Summary.conclusion)}</div>
        <div class="hero-action">${escapeHtml(data.page1Summary.recommendedAction || '')}</div>
      </div>
      <div class="metrics">
        <div class="metric"><span class="metric-label">风险分</span><span class="metric-value">${data.page1Summary.riskScore}</span></div>
        <div class="metric"><span class="metric-label">涉及文档</span><span class="metric-value">${data.page1Summary.documents?.length || 0}</span></div>
        <div class="metric"><span class="metric-label">核心证据</span><span class="metric-value">${data.page1Summary.coreEvidenceCount}</span></div>
        <div class="metric"><span class="metric-label">命中规则</span><span class="metric-value">${data.page1Summary.ruleHitCount}</span></div>
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

  const printWindow = window.open('', '_blank');
  if (!printWindow) return;
  printWindow.document.write(html);
  printWindow.document.close();
  printWindow.focus();
  setTimeout(() => printWindow.print(), 500);
}

function transformReportToReportData(result: DrugAgentResp): ReportData {
  const riskItems = result.report?.riskItems || [];
  const evidenceGroups = result.evidenceGroups || [];

  const page1Summary: Page1Summary = {
    riskLevel: normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel),
    conclusion:
      result.summary ||
      result.report?.overview?.summary ||
      result.report?.conclusion ||
      '本次比对已完成，但暂未生成明确审查结论。',
    recommendedAction: buildDecisionAction(result),
    riskScore: result.score || result.report?.overview?.score || 0,
    coreEvidenceCount: Math.min(evidenceGroups.length || result.evidenceList?.length || 0, 5),
    ruleHitCount: result.report?.overview?.rawHitCount || riskItems.length,
    coreRiskTop3: riskItems.slice(0, 3).map((item, index) => ({
      rank: index + 1,
      riskType: resolveRiskType(item),
      title: item.title || '未命名风险',
      level: normalizeLevel(item.riskLevel),
      summary: item.summary || '存在需要重点复核的异常线索。',
      action: item.recommendations?.[0] || categoryAction(resolveRiskType(item)),
    })),
    riskDistribution: buildRiskDistribution(riskItems),
    documents: (result.documentNames || []).map((name, index) => ({
      docId: result.documentIds?.[index] || `doc_${index}`,
      docName: name,
      party: inferPartyName(name, index),
      role: `对比文档 ${index + 1}`,
      internalId: result.documentIds?.[index] || `UPLOAD-${index}`,
    })),
  };

  const page2RiskOverview: Page2RiskOverview = {
    riskCategories: buildRiskCategories(riskItems, result),
  };

  const page3CoreEvidence: Page3CoreEvidence = {
    evidenceList: evidenceGroups.slice(0, 5).map((group, index) => ({
      id: `E${String(index + 1).padStart(2, '0')}`,
      type: resolveEvidenceType(group),
      level: inferEvidenceLevel(group),
      confidence: inferConfidence(group),
      title: group.title || `核心证据 ${index + 1}`,
      explanation: group.summary || '该证据能够支撑当前风险判断。',
      keyFindings: buildEvidenceFindings(group),
      basis: buildEvidenceBasis(group),
      action: categoryAction(resolveEvidenceCategory(group)),
    })),
  };

  const page4DetailComparison: Page4DetailComparison = {
    priceComparison: buildPriceComparison(result),
    teamComparison: buildTeamComparison(result),
    textHighlights: buildTextHighlights(result),
  };

  const page5ActionSuggestions: Page5ActionSuggestions = {
    level1: {
      title: '一级动作｜立即执行',
      objective: '快速判断是否需要升级处理',
      actions: [
        {
          action: '人工复核核心证据，优先确认报价异常、团队重合和关键条款相似是否成立。',
          role: '评标专家',
          priority: '高',
        },
        {
          action: '核查投标主体是否由独立团队编制，确认是否满足升级处理条件。',
          role: '风控专员',
          priority: '高',
        },
      ],
    },
    level2: {
      title: '二级动作｜进一步核验',
      objective: '补强证据链',
      actions: [
        {
          action: '核查工商关联关系、人员社保归属、授权关系和联系方式。',
          role: '风控专员',
          priority: '高',
        },
        {
          action: '补充查看历史投标记录和同类项目资料。',
          role: '招采管理员',
          priority: '中',
        },
      ],
    },
    level3: {
      title: '三级动作｜必要时追溯',
      objective: '形成完整判断依据',
      actions: [
        {
          action: '对升级事项完成合规留档，并保留后续核验过程记录。',
          role: '合规负责人',
          priority: '中',
        },
      ],
    },
    retentionAdvice: [
      '保留本次报告、关键证据片段和人工复核记录。',
      '对升级事项同步保存外围核验材料和历史投标记录。',
    ],
  };

  const page6Appendix: Page6Appendix = {
    ruleList: riskItems.map((item, index) => ({
      ruleId: `R${String(index + 1).padStart(3, '0')}`,
      ruleCode: item.reasonCodes?.[0] || `RULE-${index + 1}`,
      description: item.summary || item.title || '风险说明',
    })),
    evidenceFragments: evidenceGroups.flatMap((group, index) =>
      (group.items || []).slice(0, 2).map((item, subIndex) => ({
        fragmentId: `${group.id || `frag_${index}`}_${subIndex + 1}`,
        content: item.content || '暂无原始片段',
        source: item.source || group.title || '原文',
      }))
    ),
    taskInfo: {
      taskId: result.traceId || '-',
      reviewTime: new Date().toLocaleString('zh-CN'),
      modelVersion: 'fallback-report-v2',
    },
  };

  return {
    page1Summary,
    page2RiskOverview,
    page3CoreEvidence,
    page4DetailComparison,
    page5ActionSuggestions,
    page6Appendix,
  };
}

function buildRiskDistribution(riskItems: RiskItem[]) {
  const distribution: Record<string, string> = {
    报价风险: 'safe',
    团队风险: 'safe',
    文本相似风险: 'safe',
    模板同源风险: 'safe',
    其他辅助风险: 'safe',
  };

  riskItems.forEach((item) => {
    const type = resolveRiskType(item);
    const level = normalizeLevel(item.riskLevel);
    const label = categoryName(type);
    if (severity(level) > severity(distribution[label])) {
      distribution[label] = level;
    }
  });

  return distribution;
}

function buildRiskCategories(riskItems: RiskItem[], result: DrugAgentResp): RiskCategory[] {
  const categories = ['pricing', 'team', 'text_similarity', 'template', 'auxiliary'];
  return categories.map((type) => {
    const items = riskItems.filter((item) => resolveRiskType(item) === type);
    const topItem = items[0];
    const level = items.reduce((current, item) => {
      const next = normalizeLevel(item.riskLevel);
      return severity(next) > severity(current) ? next : current;
    }, 'safe');

    return {
      type,
      categoryName: categoryName(type),
      level,
      hitCount: items.length,
      needHumanReview: items.length > 0,
      explanation:
        topItem?.summary ||
        `${categoryName(type)}当前${
          items.length ? `命中 ${items.length} 条线索，建议结合证据链人工复核。` : '未见明显异常。'
        }`,
      representativeEvidence:
        topItem?.evidenceTitles?.join('；') ||
        result.evidenceGroups?.find((group) => resolveEvidenceCategory(group) === type)?.summary ||
        '当前未提取到代表性证据。',
      action: topItem?.recommendations?.[0] || categoryAction(type),
    };
  });
}

function buildPriceComparison(result: DrugAgentResp): Page4DetailComparison['priceComparison'] {
  const pricingGroups = (result.evidenceGroups || []).filter(
    (group) => resolveEvidenceCategory(group) === 'pricing'
  );

  return {
    headers: ['项目', '文档 A', '文档 B', '差异/说明', '判定'],
    rows: pricingGroups.map((group) => ({
      item: group.title || '报价异常项',
      docA: group.items?.[0]?.content || group.contentA || '-',
      docB: group.items?.[1]?.content || group.contentB || '-',
      diff: group.summary || '见证据说明',
      verdict: group.similarity && group.similarity > 0.85 ? '高度异常' : '异常',
    })),
  };
}

function buildTeamComparison(result: DrugAgentResp): Page4DetailComparison['teamComparison'] {
  const teamGroups = (result.evidenceGroups || []).filter(
    (group) => resolveEvidenceCategory(group) === 'team'
  );

  return {
    headers: ['角色/字段', '文档 A', '文档 B', '判定'],
    rows: teamGroups.map((group) => ({
      role: group.title || '核心岗位',
      docA: group.items?.[0]?.content || group.contentA || '-',
      docB: group.items?.[1]?.content || group.contentB || '-',
      verdict: '异常',
    })),
  };
}

function buildTextHighlights(result: DrugAgentResp): Page4DetailComparison['textHighlights'] {
  return (result.evidenceGroups || [])
    .filter((group) => ['text_similarity', 'template'].includes(resolveEvidenceCategory(group)))
    .slice(0, 6)
    .map((group) => ({
      category: group.title || resolveEvidenceType(group),
      textA: group.contentA || group.items?.[0]?.content || '',
      textB: group.contentB || group.items?.[1]?.content || '',
      similarity: group.similarity ? `${Math.round(group.similarity * 100)}%` : '-',
      verdict: group.similarity && group.similarity > 0.9 ? '高度相似' : '存在相似',
      analysis: group.summary || '建议结合上下文继续人工复核。',
    }));
}

function buildEvidenceFindings(group: EvidenceGroup) {
  return {
    证据类型: resolveEvidenceType(group),
    证据片段数: group.items?.length || 0,
    相似度: group.similarity ? `${Math.round(group.similarity * 100)}%` : '未提供',
  };
}

function buildEvidenceBasis(group: EvidenceGroup) {
  const parts = [group.title, group.summary].filter(Boolean);
  return parts.length ? parts.join('；') : '基于结构化证据分组与内容比对结果判定。';
}

function buildDecisionAction(result: DrugAgentResp) {
  const level = normalizeLevel(result.riskLevel || result.report?.overview?.riskLevel);
  if (level === 'high') return '建议立即启动人工复核，并优先核查主体关联关系、人员归属和报价形成依据。';
  if (level === 'medium') return '建议尽快开展人工核验，补充核查关键条款和历史投标记录。';
  return '建议保留结果并做抽样复核。';
}

function inferPartyName(name: string, index: number) {
  const parts = name.replace(/\.(pdf|doc|docx|txt)$/i, '').split(/[_－-]/);
  const candidate = parts.find((part) => part && !part.includes('投标人') && !part.includes('标书'));
  return candidate?.trim() || `投标方${String.fromCharCode(65 + index)}`;
}

function resolveEvidenceType(group: EvidenceGroup) {
  return categoryName(resolveEvidenceCategory(group));
}

function resolveEvidenceCategory(group: EvidenceGroup) {
  const text = `${group.title || ''} ${group.summary || ''}`.toLowerCase();
  if (text.includes('报价')) return 'pricing';
  if (text.includes('团队') || text.includes('联系人') || text.includes('人员')) return 'team';
  if (text.includes('模板') || text.includes('同源') || text.includes('错误复现')) return 'template';
  if (text.includes('雷同') || text.includes('相似') || text.includes('条款') || text.includes('方案')) {
    return 'text_similarity';
  }
  return 'auxiliary';
}

function resolveRiskType(item: RiskItem) {
  const title = `${item.title || ''} ${item.summary || ''}`;
  if ((item.riskType || '').toLowerCase() === 'pricing' || title.includes('报价')) return 'pricing';
  if (title.includes('团队') || title.includes('人员') || title.includes('联系人')) return 'team';
  if (title.includes('模板') || title.includes('同源') || title.includes('错误复现')) return 'template';
  if (title.includes('雷同') || title.includes('相似') || title.includes('条款') || title.includes('方案')) {
    return 'text_similarity';
  }
  return 'auxiliary';
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

function normalizeLevel(level?: string) {
  if (!level) return 'safe';
  const value = level.toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  return 'safe';
}

function inferEvidenceLevel(group: EvidenceGroup) {
  if (group.similarity && group.similarity > 0.9) return 'high';
  if (group.similarity && group.similarity > 0.75) return 'medium';
  return 'low';
}

function inferConfidence(group: EvidenceGroup) {
  if (group.similarity && group.similarity > 0.9) return '高';
  if (group.similarity && group.similarity > 0.75) return '中';
  return '中';
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

function collectActions(page?: Page5ActionSuggestions): Action[] {
  if (!page) return [];
  return [page.level1, page.level2, page.level3].flatMap((level) => level?.actions || []);
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
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
  overflow: auto;
  padding: 22px;
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
