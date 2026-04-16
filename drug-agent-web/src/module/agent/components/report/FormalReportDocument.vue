<template>
  <div v-if="data" class="formal-document-layout">
    <div ref="docMainRef" class="doc-main" id="doc-scroll-container" @scroll="onScroll">
      <div class="doc-content">
        <header class="hero-panel">
          <div class="hero-copy">
            <div class="eyebrow">标书审查报告</div>
            <h1 class="hero-title">{{ data.executiveSummary?.overallConclusion }}</h1>
            <p class="hero-action">建议动作：{{ data.executiveSummary?.recommendedAction }}</p>
            <div class="hero-meta">
              <span v-if="data.metadata?.traceId">TRACE: {{ data.metadata.traceId }}</span>
              <span>生成时间：{{ data.metadata?.generatedAt || '-' }}</span>
            </div>
          </div>

          <div class="metric-grid">
            <div class="metric-card">
              <span class="metric-label">综合风险分</span>
              <span class="metric-value">{{ data.executiveSummary?.riskScore }}</span>
              <span class="metric-hint">用于排序和复核优先级参考</span>
            </div>
            <div class="metric-card">
              <span class="metric-label">重点风险项</span>
              <span class="metric-value">{{ data.riskOverview?.topRisks?.length || 0 }}</span>
              <span class="metric-hint">建议优先关注的核心问题</span>
            </div>
            <div class="metric-card">
              <span class="metric-label">关键证据数</span>
              <span class="metric-value">{{ data.executiveSummary?.metrics?.coreEvidenceCount || 0 }}</span>
              <span class="metric-hint">可直接支撑结论的证据</span>
            </div>
            <div class="metric-card">
              <span class="metric-label">比对文件数</span>
              <span class="metric-value">{{ data.executiveSummary?.metrics?.documentCount || 0 }}</span>
              <span class="metric-hint">纳入本次分析的文件数量</span>
            </div>
          </div>
        </header>

        <section id="chapter-1" class="doc-section">
          <h2>一、总体结论</h2>
          <div class="summary-card">
            <div class="summary-badge" :class="metricColor(data.executiveSummary?.riskLevel)">
              {{ levelLabel(data.executiveSummary?.riskLevel) }}
            </div>
            <p>{{ data.executiveSummary?.overallConclusion }}</p>
            <p>建议优先处理：{{ data.executiveSummary?.recommendedAction }}</p>
          </div>
          <div class="insight-grid">
            <div class="insight-card">
              <div class="insight-title">当前判断</div>
              <p>{{ levelSummary(data.executiveSummary?.riskLevel) }}</p>
            </div>
            <div class="insight-card">
              <div class="insight-title">为什么需要关注</div>
              <p>系统同时结合风险规则、关键证据和双文档对比结果进行判断，不只看单条命中。</p>
            </div>
            <div class="insight-card">
              <div class="insight-title">如何使用本报告</div>
              <p>先看重点风险，再对照关键证据，最后决定是否升级人工复核或外部核验。</p>
            </div>
          </div>
        </section>

        <section id="chapter-2" class="doc-section">
          <h2>二、风险总览</h2>
          <div class="overview-grid">
            <article
              v-for="dist in data.riskOverview?.distributions"
              :key="dist.riskType"
              class="overview-card"
            >
              <div class="overview-head">
                <h3>{{ dist.riskType }}</h3>
                <span class="overview-level" :class="metricColor(dist.level)">
                  {{ levelLabel(dist.level) }}
                </span>
              </div>
              <p>{{ dist.explanation }}</p>
              <div class="overview-meta">
                <span>命中 {{ dist.hitCount }} 项</span>
                <span>{{ dist.needReview ? '建议人工复核' : '可继续观察' }}</span>
              </div>
            </article>
          </div>
        </section>

        <section id="chapter-3" class="doc-section">
          <h2>三、本次比对文件</h2>
          <div class="document-grid">
            <article v-for="doc in data.documents" :key="doc.id" class="document-card">
              <div class="document-head">
                <span class="doc-code">{{ doc.docCode }}</span>
                <div>
                  <h3>{{ doc.partyName }}</h3>
                  <p>{{ doc.fileName }}</p>
                </div>
              </div>
              <div class="document-meta">
                <span>{{ doc.docRole }}</span>
                <span>涉及 {{ doc.hitRiskCount }} 项重点风险</span>
              </div>
              <p class="document-risk-label">主要关联问题</p>
              <div class="chip-list">
                <span v-for="risk in doc.involvedRisks" :key="risk" class="chip">{{ risk }}</span>
              </div>
            </article>
          </div>
        </section>

        <section id="chapter-4" class="doc-section">
          <h2>四、重点风险判断</h2>
          <template v-if="data.riskOverview?.topRisks?.length">
            <article v-for="risk in data.riskOverview.topRisks" :key="risk.rank" class="risk-card">
              <div class="risk-head">
                <div>
                  <div class="risk-order">风险 {{ risk.rank }}</div>
                  <h3>{{ risk.riskName }}</h3>
                </div>
                <span class="overview-level" :class="metricColor(risk.riskLevel)">
                  {{ levelLabel(risk.riskLevel) }}
                </span>
              </div>
              <div class="risk-detail-grid">
                <div class="detail-card">
                  <div class="detail-label">风险说明</div>
                  <p>{{ risk.description }}</p>
                </div>
                <div class="detail-card">
                  <div class="detail-label">关键事实</div>
                  <p>{{ risk.keyFact }}</p>
                </div>
                <div class="detail-card">
                  <div class="detail-label">系统为什么提示</div>
                  <p>{{ risk.basis }}</p>
                </div>
                <div class="detail-card action-card">
                  <div class="detail-label">建议动作</div>
                  <p>{{ risk.action }}</p>
                </div>
              </div>
            </article>
          </template>
          <div v-else class="empty-state">当前未提取到需要重点展示的风险项。</div>
        </section>

        <section id="chapter-5" class="doc-section">
          <h2>五、关键证据对比</h2>
          <template v-if="data.evidences?.length">
            <article v-for="evidence in data.evidences" :key="evidence.evidenceId" class="evidence-card">
              <div class="risk-head">
                <div>
                  <div class="risk-order">{{ evidence.evidenceId }}</div>
                  <h3>{{ evidence.title }}</h3>
                </div>
                <span class="overview-level" :class="metricColor(evidence.level)">
                  {{ levelLabel(evidence.level) }}
                </span>
              </div>

              <div class="evidence-summary">
                <div class="detail-card">
                  <div class="detail-label">证据摘要</div>
                  <p>{{ evidence.summary }}</p>
                </div>
                <div class="detail-card">
                  <div class="detail-label">判定依据</div>
                  <p>{{ evidence.basis || '系统结合原文片段和规则判断给出提示。' }}</p>
                </div>
              </div>

              <div class="diff-grid">
                <div class="diff-side">
                  <div class="diff-head">{{ evidence.docAName || '文档 A' }}</div>
                  <pre>{{ evidence.diffPayload?.contentA || '-' }}</pre>
                </div>
                <div class="diff-side">
                  <div class="diff-head">{{ evidence.docBName || '文档 B' }}</div>
                  <pre>{{ evidence.diffPayload?.contentB || '-' }}</pre>
                </div>
              </div>

              <div class="evidence-footer">
                <span v-if="evidence.diffPayload?.similarityScore">相似度：{{ evidence.diffPayload.similarityScore }}</span>
                <span v-if="evidence.diffPayload?.divergence">差异说明：{{ evidence.diffPayload.divergence }}</span>
                <span>{{ verdictLabel(evidence.diffPayload?.diffVerdict) }}</span>
              </div>
              <p class="evidence-analysis">复核建议：{{ evidence.analysis }}</p>
            </article>
          </template>
          <div v-else class="empty-state">当前未提取到可展示的双侧证据内容。</div>
        </section>

        <section id="chapter-6" class="doc-section">
          <h2>六、建议处置</h2>
          <div class="action-layout">
            <div class="action-column">
              <h3>立即处理</h3>
              <ol class="doc-list">
                <li v-for="(item, index) in data.actionPlan?.level1Actions" :key="`l1-${index}`">{{ item }}</li>
              </ol>
            </div>
            <div class="action-column">
              <h3>进一步核验</h3>
              <ol class="doc-list">
                <li v-for="(item, index) in data.actionPlan?.level2Actions" :key="`l2-${index}`">{{ item }}</li>
              </ol>
            </div>
            <div class="action-column">
              <h3>留痕与追溯</h3>
              <ol class="doc-list">
                <li v-for="(item, index) in data.actionPlan?.level3Actions" :key="`l3-${index}`">{{ item }}</li>
              </ol>
            </div>
          </div>

          <div v-if="data.actionPlan?.responsibilityMatrix?.length" class="responsibility-table-wrap">
            <h3>建议责任分工</h3>
            <table class="doc-table">
              <thead>
                <tr>
                  <th>动作</th>
                  <th>建议角色</th>
                  <th>优先级</th>
                  <th>补充说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in data.actionPlan.responsibilityMatrix" :key="index">
                  <td>{{ item.action }}</td>
                  <td>{{ item.role }}</td>
                  <td>{{ priorityLabel(item.priority) }}</td>
                  <td>{{ item.remark || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section id="chapter-7" class="doc-section bottom-space">
          <h2>七、使用边界说明</h2>
          <ul class="doc-list">
            <li>本报告用于辅助识别围标、串标或非独立编制风险，不直接替代最终评审或法律定性。</li>
            <li>若存在未上传附件、补充说明或历史投标材料，可能影响当前判断结果。</li>
            <li>建议结合项目背景、原文上下文和人工复核结论，决定是否升级处理。</li>
          </ul>
        </section>
      </div>
    </div>

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
import { ref } from 'vue';
import type { ReportData } from '../../types/report.types';

defineProps<{ data?: ReportData }>();

/** 暴露可滚动内容区域，供父组件（ReportDrawer）取元素做 PDF 导出 */
const docMainRef = ref<HTMLElement | null>(null);
defineExpose({ docMainRef });

const navs = [
  { id: 'chapter-1', label: '一、总体结论' },
  { id: 'chapter-2', label: '二、风险总览' },
  { id: 'chapter-3', label: '三、本次比对文件' },
  { id: 'chapter-4', label: '四、重点风险判断' },
  { id: 'chapter-5', label: '五、关键证据对比' },
  { id: 'chapter-6', label: '六、建议处置' },
  { id: 'chapter-7', label: '七、使用边界' },
];

const activeAnchor = ref('chapter-1');

function scrollTo(id: string) {
  const el = document.getElementById(id);
  const container = document.getElementById('doc-scroll-container');
  if (el && container) {
    container.scrollTo({ top: el.offsetTop - 80, behavior: 'smooth' });
    activeAnchor.value = id;
  }
}

let scrollTimeout: ReturnType<typeof setTimeout> | null = null;
function onScroll(event: Event) {
  if (scrollTimeout) clearTimeout(scrollTimeout);
  scrollTimeout = setTimeout(() => {
    const container = event.target as HTMLElement;
    const position = container.scrollTop + 120;
    for (let i = navs.length - 1; i >= 0; i -= 1) {
      const el = document.getElementById(navs[i].id);
      if (el && el.offsetTop <= position) {
        activeAnchor.value = navs[i].id;
        break;
      }
    }
  }, 50);
}

function levelLabel(value?: string) {
  if (value === 'high') return '高风险';
  if (value === 'medium') return '中风险';
  if (value === 'low') return '低风险';
  return '提示';
}

function levelSummary(value?: string) {
  if (value === 'high') return '当前线索强度较高，建议优先安排人工复核，并视情况补充外围核验材料。';
  if (value === 'medium') return '当前已发现较明确异常信号，建议补充核验关键条款、主体关系和历史记录。';
  if (value === 'low') return '当前仅发现少量异常提示，建议保留结果并做抽样检查。';
  return '当前结果更适合作为筛查提示，请结合上下文继续判断。';
}

function metricColor(value?: string) {
  if (value === 'high') return 'color-danger';
  if (value === 'medium') return 'color-warning';
  if (value === 'low') return 'color-safe';
  return 'color-neutral';
}

function verdictLabel(value?: string) {
  if (value === 'warning') return '判定：高度异常';
  if (value === 'anomaly_gap') return '判定：存在异常差异';
  if (value === 'fuzzy_match') return '判定：存在高相似内容';
  return '判定：建议结合上下文复核';
}

function priorityLabel(value?: string) {
  if (value === 'high') return '高';
  if (value === 'medium') return '中';
  if (value === 'low') return '低';
  return value || '-';
}
</script>

<style scoped>
.formal-document-layout {
  display: flex;
  height: calc(100vh - 140px);
  max-width: 1240px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
}

.doc-main {
  flex: 1;
  overflow-y: auto;
  padding: 36px 44px;
  scroll-behavior: smooth;
  color: #0f172a;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.doc-content {
  max-width: 920px;
}

.doc-sidebar {
  width: 220px;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  padding: 24px 16px;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 14px;
  padding-left: 12px;
}

.anchor-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.anchor-list li {
  padding: 8px 12px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.anchor-list li:hover {
  background: #eff6ff;
  color: #0f172a;
}

.anchor-list li.active {
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 700;
}

.hero-panel {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 20px;
  margin-bottom: 30px;
  padding: 24px;
  border-radius: 24px;
  border: 1px solid #fecaca;
  background: linear-gradient(135deg, #fff7f5, #ffffff 45%, #fff1f2);
}

.eyebrow {
  font-size: 13px;
  font-weight: 700;
  color: #b91c1c;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.hero-title {
  margin: 10px 0 14px;
  font-size: 32px;
  line-height: 1.45;
}

.hero-action {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  line-height: 1.8;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 14px;
  color: #64748b;
  font-size: 12px;
}

.metric-grid,
.overview-grid,
.document-grid,
.insight-grid,
.risk-detail-grid,
.evidence-summary,
.action-layout {
  display: grid;
  gap: 14px;
}

.metric-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.metric-card,
.summary-card,
.insight-card,
.overview-card,
.document-card,
.risk-card,
.evidence-card,
.detail-card,
.action-column {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 18px;
}

.metric-card {
  padding: 18px;
}

.metric-label,
.metric-hint,
.detail-label,
.document-risk-label,
.risk-order,
.insight-title {
  display: block;
}

.metric-label,
.detail-label,
.document-risk-label,
.insight-title,
.risk-order {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.metric-value {
  display: block;
  margin: 8px 0 6px;
  font-size: 32px;
  font-weight: 800;
}

.metric-hint {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.6;
}

.doc-section {
  margin-bottom: 34px;
}

.doc-section h2 {
  margin-bottom: 16px;
  font-size: 22px;
  font-weight: 800;
}

.doc-section h3 {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
}

.summary-card {
  padding: 20px 22px;
  line-height: 1.85;
}

.summary-badge,
.overview-level,
.chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.summary-badge,
.overview-level {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.summary-badge {
  margin-bottom: 12px;
}

.insight-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 14px;
}

.insight-card {
  padding: 16px;
}

.insight-card p,
.overview-card p,
.document-card p,
.detail-card p,
.evidence-analysis {
  margin: 0;
  color: #334155;
  line-height: 1.75;
}

.overview-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-card {
  padding: 18px;
}

.overview-head,
.document-head,
.risk-head,
.evidence-footer,
.document-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.overview-head,
.document-head,
.risk-head {
  align-items: flex-start;
}

.overview-meta,
.document-meta,
.evidence-footer {
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  flex-wrap: wrap;
}

.document-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.document-card {
  padding: 18px;
}

.doc-code {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #0f172a;
  color: #fff;
  font-weight: 800;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
}

.risk-card,
.evidence-card {
  padding: 20px;
  margin-bottom: 16px;
}

.risk-detail-grid,
.evidence-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 14px;
}

.detail-card {
  padding: 16px;
}

.action-card {
  background: linear-gradient(180deg, #fffaf0, #ffffff);
}

.diff-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 16px 0 12px;
}

.diff-side {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.diff-head {
  margin-bottom: 10px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.diff-side pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
  color: #0f172a;
}

.evidence-analysis {
  margin-top: 10px;
}

.action-layout {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.action-column {
  padding: 18px;
}

.doc-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.85;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 14px;
  font-size: 14px;
}

.doc-table th,
.doc-table td {
  border: 1px solid #cbd5e1;
  padding: 10px 12px;
  text-align: left;
}

.doc-table th {
  background: #f8fafc;
  font-weight: 700;
}

.responsibility-table-wrap {
  margin-top: 18px;
}

.empty-state {
  padding: 24px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
}

.color-danger {
  color: #b91c1c;
  background: #fee2e2;
}

.color-warning {
  color: #b45309;
  background: #fef3c7;
}

.color-safe {
  color: #166534;
  background: #dcfce7;
}

.color-neutral {
  color: #475569;
  background: #e2e8f0;
}

.bottom-space {
  margin-bottom: 60px;
}

@media (max-width: 1080px) {
  .formal-document-layout {
    display: block;
    height: auto;
  }

  .doc-sidebar {
    display: none;
  }

  .doc-main {
    padding: 28px 20px;
  }

  .hero-panel,
  .metric-grid,
  .overview-grid,
  .document-grid,
  .insight-grid,
  .risk-detail-grid,
  .evidence-summary,
  .diff-grid,
  .action-layout {
    grid-template-columns: 1fr;
  }
}
</style>
