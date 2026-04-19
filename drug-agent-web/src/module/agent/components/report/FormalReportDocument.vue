<template>
  <div v-if="data" class="formal-document-layout">
    <div ref="docMainRef" class="doc-main" id="doc-scroll-container" @scroll="onScroll">
      <div class="page-container doc-content">
        <!-- 水印装饰 -->
        <div class="watermark">INTERNAL USE ONLY</div>

        <!-- 顶部 Header -->
        <header class="report-header">
          <div class="report-title-group">
            <div class="report-id">DOCUMENT ID: {{ data.metadata?.documentId || 'TSR-20240416-X99' }}</div>
            <h1>医药标书违规风险专业审查报告</h1>
          </div>
            <div class="risk-badge-large">
              <span class="label">风险综合判定</span>
              <span class="value">
              {{ riskHeadline }}
              </span>
            </div>
        </header>

        <!-- 一、审查执行摘要 -->
        <section id="chapter-1" class="doc-section">
          <h2>一、审查执行摘要</h2>
          <div class="summary-grid">
            <div class="stat-card danger">
              <div class="stat-label">有效违规命中 (Hits)</div>
              <div class="stat-value">{{ effectiveHitsValue }}</div>
            </div>
            <div class="stat-card active">
              <div class="stat-label">关联证据簇 (EVID)</div>
              <div class="stat-value">{{ data.executiveSummary?.metrics?.evidenceClusters ?? data.evidences?.length ?? 15 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">参与审查文书</div>
              <div class="stat-value">{{ data.executiveSummary?.metrics?.documentCount ?? data.documents?.length ?? 2 }}</div>
            </div>
          </div>
          <div class="conclusion-box">
            <p>{{ data.executiveSummary?.conclusionText || 'AI 审查引擎经由“确定性规约”与“LLM 语义分析器”交叉验证：【投标主体 A】与【投标主体 B】在多个不应重合的维度上表现出极高的协同特征。特别是在“元数据属性”、“罕见错误复现”及“团队主要成员”三项核心规则上均有显著命中，建议立即启动法律合规性程序并暂缓定标。' }}</p>
          </div>
        </section>

        <!-- 二、审查文档元数据记录 -->
        <section id="chapter-2" class="doc-section">
          <h2>二、审查文档元数据记录</h2>
          <div class="table-wrap">
            <table class="doc-table">
              <thead>
                <tr>
                  <th>投标主体名称</th>
                  <th>主文件名</th>
                  <th>文档性质</th>
                  <th>最后修改人</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(doc, index) in (data.documents?.length ? data.documents : defaultDocs)" :key="index">
                  <td>{{ doc.partyName }}</td>
                  <td class="file-name-cell">{{ doc.fileName }}</td>
                  <td>{{ doc.docNature || ('role' in doc ? doc.role : '-') }}</td>
                  <td>{{ doc.lastModifier || 'Admin_User' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 三、核心风险项识别 -->
        <section id="chapter-3" class="doc-section">
          <h2>三、核心风险项识别</h2>
          <template v-if="topRisks.length">
            <article v-for="risk in topRisks" :key="risk.rank" class="risk-item">
              <div class="risk-rank">
                <span class="rank-num">{{ String(risk.rank).padStart(2, '0') }}</span>
                <span class="rule-code-small">{{ risk.ruleCode || '审查项' }}</span>
              </div>
              <div class="risk-content">
                <div class="risk-header">
                  <h3>{{ risk.riskName }}</h3>
                  <span class="risk-level-tag tag-critical">
                    {{ riskLevelTagLabel(risk.level) }}
                  </span>
                </div>
                <div class="risk-body">
                  <div class="risk-field">
                    <span class="field-label">风险描述 / RISK DESCRIPTION</span>
                    <p class="field-value">{{ risk.riskDesc || risk.keyFact }}</p>
                  </div>
                  <div class="risk-field">
                    <span class="field-label">规则建议 / ACTION</span>
                    <p class="field-value">{{ risk.action }}</p>
                  </div>
                </div>
              </div>
            </article>
          </template>
        </section>

        <!-- 四、关键取证对比明细 (仅在有数据时显示) -->
        <section v-if="data.evidences?.length" id="chapter-4" class="doc-section">
          <h2>四、关键取证对比明细</h2>
          <div v-for="evidence in data.evidences" :key="evidence.evidenceId" class="evidence-box">
            <div class="evidence-header">
              证据链名称：{{ evidence.evidenceChainName || evidence.displayTitle || evidence.title || evidence.evidenceChainId || evidence.evidenceId }}<template v-if="evidence.similarity"> | 文本相似度：{{ evidence.similarity }}</template>
            </div>
            <div class="evidence-diff">
              <div class="diff-col">
                <div class="diff-label">{{ evidence.docAName }} 原文片段</div>
                <div class="diff-text">{{ evidence.docAContent || '（当前未返回原文片段）' }}</div>
              </div>
              <div class="diff-col">
                <div class="diff-label">{{ evidence.docBName }} 原文片段</div>
                <div class="diff-text">{{ evidence.docBContent || '（当前未返回原文片段）' }}</div>
              </div>
            </div>
            <div class="finding-footer">
              <strong>AI 判定逻辑 (Judgement)：</strong>
              {{ evidence.aiJudgment || evidence.comparisonFinding }}
            </div>
          </div>
        </section>

        <!-- 五、处置建议 -->
        <section v-if="data.actionPlan?.tasks?.length" id="chapter-5" class="doc-section">
          <h2>五、处置建议</h2>
          <div class="table-wrap">
            <table class="doc-table">
              <thead>
                <tr>
                  <th width="100">优先级</th>
                  <th>建议动作</th>
                  <th>责任角色</th>
                  <th>目标</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(task, index) in data.actionPlan.tasks" :key="index">
                  <td>
                    <span class="priority-badge" :class="priorityClass(task.priority)">
                      {{ priorityLabel(task.priority) }}
                    </span>
                  </td>
                  <td>{{ task.action }}</td>
                  <td>{{ task.role }}</td>
                  <td>{{ task.goal }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <!-- Footer -->
        <footer class="doc-footer">
          <div class="seal-group">
            <div class="seal-item">
              <p>系统自动生成的电子验证章</p>
              <div class="seal-line"></div>
              <p>[ DRUG-AGENT AUDIT SERVICE ]</p>
            </div>
            <div class="seal-item">
              <p>合规部专家复核（签字/盖章）</p>
              <div class="seal-line"></div>
              <p>DATE: {{ new Date().getFullYear() }} / ____ / ____</p>
            </div>
          </div>
          <p class="copyright">医药监管智能分析系统 &copy; {{ new Date().getFullYear() }} 系统由 AI 引擎驱动，仅供内部合规参考</p>
        </footer>
      </div>
    </div>

    <!-- 侧边目录导航 -->
    <aside class="doc-sidebar">
      <div class="sidebar-title">报告目录</div>
      <ul class="anchor-list">
        <li
          v-for="nav in navs"
          :key="nav.id"
          :class="{ active: activeAnchor === nav.id }"
          @click="scrollTo(nav.id)"
        >
          {{ nav.label }}
        </li>
      </ul>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ReportData, TopRisk } from '../../types/report.types';

const props = defineProps<{ data?: ReportData }>();

const riskHeadline = computed(() => {
  const levelLabel = props.data?.executiveSummary?.riskLevelLabel || '重大风险';
  const primaryRiskName = props.data?.executiveSummary?.primaryRiskName;
  return primaryRiskName ? `${levelLabel} · ${primaryRiskName}` : levelLabel;
});

const docMainRef = ref<HTMLElement | null>(null);
defineExpose({ docMainRef });

const defaultDocs = [
  { partyName: '国药控股XX分公司', fileName: '公司技术响应方案-Final.pdf', docNature: '审计主文档', lastModifier: 'Admin_User' },
  { partyName: 'XX生物医药贸易有限公司', fileName: '投标技术建议书_副本.pdf', docNature: '关键参检文档', lastModifier: 'Admin_User' }
];

const navs = [
  { id: 'chapter-1', label: '一、审查执行摘要' },
  { id: 'chapter-2', label: '二、审查文档元数据' },
  { id: 'chapter-3', label: '三、核心风险项识别' },
  { id: 'chapter-4', label: '四、关键取证对比' },
  { id: 'chapter-5', label: '五、处置建议' },
];

const activeAnchor = ref('chapter-1');

const topRisks = computed<TopRisk[]>(() => {
  if (!props.data?.riskOverview) return [];
  if (props.data.riskOverview.topRisks?.length) {
    return props.data.riskOverview.topRisks;
  }
  return props.data.riskOverview.distributions
    ?.filter(d => d.found)
    .map((d, i) => ({
      rank: i + 1,
      riskName: d.riskType,
      level: 'high' as const,
      riskDesc: d.brief || '规则命中不同投标主体的文档具有完全一样的标识',
      keyFact: d.brief || '',
      whyReview: d.needReviewText,
      action: '核实投标主体是否隶属于同一实际控制人',
    })) ?? [];
});

function scrollTo(id: string) {
  const el = document.getElementById(id);
  const container = document.getElementById('doc-scroll-container');
  if (el && container) {
    container.scrollTo({ top: el.offsetTop - 80, behavior: 'smooth' });
    activeAnchor.value = id;
  }
}

function onScroll(event: Event) {
  const container = event.target as HTMLElement;
  const position = container.scrollTop + 120;
  for (let i = navs.length - 1; i >= 0; i -= 1) {
    const el = document.getElementById(navs[i].id);
    if (el && el.offsetTop <= position) {
      activeAnchor.value = navs[i].id;
      break;
    }
  }
}

function priorityLabel(value?: string) {
  if (value === 'high') return '高';
  if (value === 'medium') return '中';
  if (value === 'low') return '低';
  return value || '-';
}

function priorityClass(value?: string) {
  if (value === 'high') return 'priority-high';
  if (value === 'medium') return 'priority-medium';
  if (value === 'low') return 'priority-low';
  return 'priority-neutral';
}

function riskLevelTagLabel(value?: string) {
  if (value === 'medium') return '中风险';
  if (value === 'low') return '低风险';
  return '重大风险';
}

const effectiveHitsValue = computed(() => {
  return props.data?.executiveSummary?.metrics?.effectiveHits ?? 7;
});
</script>

<style scoped>
.formal-document-layout {
  display: flex;
  height: calc(100vh - 140px);
  max-width: 1400px;
  margin: 0 auto;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.doc-main {
  flex: 1;
  overflow-y: auto;
  scroll-behavior: smooth;
  padding: 40px 0;
}

.page-container {
  max-width: 1000px;
  margin: 0 auto;
  background: #ffffff;
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1);
  padding: 60px 80px;
  position: relative;
  min-height: 1414px;
}

.watermark {
  position: absolute;
  top: 20px;
  right: 20px;
  font-size: 10px;
  font-weight: 800;
  color: #e2e8f0;
  letter-spacing: 0.1em;
}

/* Header Section */
.report-header {
  border-bottom: 2px solid #0f172a;
  padding-bottom: 24px;
  margin-bottom: 40px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.report-id {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.report-title-group h1 {
  font-size: 30px;
  font-weight: 900;
  color: #0f172a;
  margin: 0;
  letter-spacing: -0.01em;
}

.risk-badge-large {
  background: #b91c1c;
  color: white;
  padding: 12px 20px;
  border-radius: 2px;
  text-align: center;
  min-width: 180px;
}

.risk-badge-large .label {
  display: block;
  font-size: 10px;
  font-weight: 700;
  opacity: 0.9;
  margin-bottom: 4px;
}

.risk-badge-large .value {
  font-size: 18px;
  font-weight: 800;
}

/* Section Styling */
.doc-section {
  margin-bottom: 48px;
}

.doc-section h2 {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.doc-section h2::before {
  content: "";
  width: 4px;
  height: 18px;
  background: #1e40af;
  display: inline-block;
}

/* Executive Summary Cards */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  background: #f8fafc;
  padding: 24px;
  border-bottom: 4px solid #e2e8f0;
}

.stat-card.danger { border-bottom-color: #b91c1c; }
.stat-card.active { border-bottom-color: #1e40af; }

.stat-label {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 40px;
  font-weight: 900;
  line-height: 1;
  color: #0f172a;
}

.conclusion-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 24px;
  border-radius: 4px;
}

.conclusion-box p {
  font-size: 15px;
  color: #1e293b;
  font-weight: 500;
  line-height: 1.8;
  margin: 0;
}

/* Tables */
.table-wrap {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.doc-table th {
  text-align: left;
  padding: 14px 16px;
  background: #f8fafc;
  color: #475569;
  font-weight: 700;
  border-bottom: 1px solid #e2e8f0;
}

.doc-table td {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  color: #1e293b;
}

/* Risk Items */
.risk-item {
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  margin-bottom: 24px;
  overflow: hidden;
  display: flex;
}

.risk-rank {
  width: 70px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.risk-rank .rank-num { font-size: 32px; font-weight: 900; color: #e2e8f0; line-height: 1; }
.risk-rank .rule-code-small { font-size: 10px; font-weight: 800; color: #475569; margin-top: 4px; }

.risk-content { flex: 1; padding: 24px; }

.risk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}

.risk-header h3 { font-size: 18px; font-weight: 800; color: #0f172a; margin: 0; }

.risk-level-tag {
  font-size: 10px;
  font-weight: 800;
  padding: 4px 10px;
  border-radius: 2px;
  background: #fee2e2;
  color: #b91c1c;
  text-transform: uppercase;
}

.risk-body {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 32px;
}

.field-label {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
  display: block;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.field-value { font-size: 14px; color: #1e293b; line-height: 1.6; margin: 0; }

/* Evidence Box */
.evidence-box {
  background: #fff;
  border: 1px solid #d8e2ef;
  margin-bottom: 28px;
  border-radius: 6px;
  overflow: hidden;
}

.evidence-header {
  padding: 16px 22px;
  background: #f8fbff;
  border-bottom: 1px solid #d8e2ef;
  font-size: 14px;
  font-weight: 700;
  color: #475569;
}

.evidence-diff {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.diff-col {
  min-width: 0;
  padding: 18px 22px 20px;
}

.diff-col + .diff-col {
  border-left: 1px solid #d8e2ef;
}

.diff-label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  letter-spacing: 0.01em;
}

.diff-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 1.8;
  color: #1e293b;
}

.finding-footer {
  padding: 16px 22px 18px;
  border-top: 1px solid #d8e2ef;
  background: #fff9e8;
  font-size: 14px;
  line-height: 1.8;
  color: #334155;
}

.finding-footer strong {
  color: #0f172a;
}

/* Sidebar */
.doc-sidebar {
  width: 240px;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  padding: 30px 20px;
}

.sidebar-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 20px;
}

.anchor-list { list-style: none; padding: 0; }
.anchor-list li {
  padding: 10px 14px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 4px;
}
.anchor-list li:hover { background: #f1f5f9; color: #0f172a; }
.anchor-list li.active { background: #1e40af; color: white; font-weight: 600; }

/* Footer */
.doc-footer {
  margin-top: 60px;
  padding-top: 40px;
  border-top: 1px solid #e2e8f0;
  text-align: center;
}

.seal-group {
  display: flex;
  justify-content: center;
  gap: 120px;
  margin-bottom: 30px;
}

.seal-item p { font-size: 12px; color: #475569; font-weight: 500; }
.seal-line { width: 140px; height: 1px; background: #cbd5e1; margin: 12px auto; }
.copyright { font-size: 10px; color: #94a3b8; margin-top: 40px; }

@media print {
  .formal-document-layout { display: block; }
  .doc-sidebar { display: none; }
  .page-container { box-shadow: none; padding: 40px; }
}
</style>
