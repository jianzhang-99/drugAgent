<template>
  <div v-if="data" class="formal-document-layout">
    <div ref="docMainRef" class="doc-main" id="doc-scroll-container" @scroll="onScroll">
      <div class="doc-content">
        <!-- 顶部 HeroPanel -->
        <header class="hero-panel">
          <div class="hero-copy">
            <div class="report-id-label">DOCUMENT ID: {{ data.metadata?.documentId || 'TSR-XXXXXXXX-XXX' }}</div>
            <h1 class="hero-title">医药标书违规风险专业审查报告</h1>
            <p class="hero-note">{{ data.executiveSummary?.conclusionText || data.executiveSummary?.reviewNote || '审查进行中' }}</p>
            <div class="hero-meta">
              <span>生成时间：{{ data.metadata?.generatedAt || '-' }}</span>
              <span>审查范围：{{ data.metadata?.reviewScope || '-' }}</span>
            </div>
          </div>
          <div class="hero-badge-area">
            <div class="risk-badge-large">
              <span class="badge-label">风险综合判定</span>
              <span class="badge-value" :class="riskBadgeClass">
                {{ data.executiveSummary?.riskLevelLabel || '待评定' }}
              </span>
            </div>
          </div>
        </header>

        <!-- Chapter1 审查执行摘要 -->
        <section id="chapter-1" class="doc-section">
          <h2>一、审查执行摘要</h2>
          <div class="summary-grid">
            <div class="stat-card" :class="effectiveHitsClass">
              <div class="stat-label">有效违规命中 (Hits)</div>
              <div class="stat-value">{{ effectiveHitsValue }}</div>
            </div>
            <div class="stat-card active">
              <div class="stat-label">关联证据簇 (EVID)</div>
              <div class="stat-value">{{ data.executiveSummary?.metrics?.evidenceClusters ?? data.evidences?.length ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">参与审查文书</div>
              <div class="stat-value">{{ data.executiveSummary?.metrics?.documentCount ?? data.documents?.length ?? 0 }}</div>
            </div>
          </div>
          <div class="conclusion-box">
            <p>{{ data.executiveSummary?.conclusionText || '暂无结论信息' }}</p>
          </div>
        </section>

        <!-- Chapter2 审查文档元数据记录 -->
        <section id="chapter-2" class="doc-section">
          <h2>二、审查文档元数据记录</h2>
          <div v-if="data.documents?.length" class="document-table-wrap">
            <table class="doc-table">
              <thead>
                <tr>
                  <th>文档 ID</th>
                  <th>投标主体名称</th>
                  <th>主文件名</th>
                  <th>文档性质</th>
                  <th>最后修改人</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="doc in data.documents" :key="doc.docCode">
                  <td><span class="file-code-tag">{{ doc.docId || doc.docCode }}</span></td>
                  <td>{{ doc.partyName }}</td>
                  <td class="file-name-cell">{{ doc.fileName }}</td>
                  <td>{{ doc.docNature || doc.role }}</td>
                  <td>{{ doc.lastModifier || 'Admin_User' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state">暂无文件信息</div>
        </section>

        <!-- Chapter3 风险总览 -->
        <section id="chapter-3" class="doc-section">
          <h2>三、风险总览</h2>
          <div v-if="data.riskOverview?.distributions?.length" class="overview-table-wrap">
            <table class="doc-table">
              <thead>
                <tr>
                  <th>风险方向</th>
                  <th>是否有发现</th>
                  <th>是否必须复核</th>
                  <th>简要说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="dist in data.riskOverview.distributions" :key="dist.riskType">
                  <td>{{ dist.riskType }}</td>
                  <td>
                    <span class="status-tag" :class="dist.found ? 'status-found' : 'status-none'">
                      {{ dist.found ? dist.foundDescription : '未发现' }}
                    </span>
                  </td>
                  <td>
                    <span class="status-tag" :class="dist.needReview ? 'status-review' : 'status-ok'">
                      {{ dist.needReview ? dist.needReviewText : '暂不需要' }}
                    </span>
                  </td>
                  <td>{{ dist.brief || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state">暂无风险信息</div>
        </section>

        <!-- Chapter4 核心风险项识别 -->
        <section id="chapter-4" class="doc-section">
          <h2>三、核心风险项识别（Workflow 规则命中）</h2>
          <template v-if="topRisks.length">
            <article v-for="risk in topRisks" :key="risk.rank" class="risk-item">
              <div class="risk-rank">
                <span class="rank-num">{{ String(risk.rank).padStart(2, '0') }}</span>
                <span class="rule-code-small">{{ risk.ruleCode || 'N/A' }}</span>
              </div>
              <div class="risk-content">
                <div class="risk-header">
                  <h3>{{ risk.riskName }}</h3>
                  <span class="risk-level-tag" :class="riskLevelTagClass(risk.level)">
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
          <div v-else class="empty-state">当前未提取到需要重点展示的风险项</div>
        </section>

        <!-- Chapter5 关键取证对比明细 -->
        <section id="chapter-5" class="doc-section">
          <h2>四、关键取证对比明细</h2>
          <template v-if="data.evidences?.length">
            <div v-for="evidence in data.evidences" :key="evidence.evidenceId" class="evidence-box">
              <div class="evidence-header">
                证据链主标识：{{ evidence.evidenceChainId || evidence.evidenceId }}
                <template v-if="evidence.similarity"> | 文本相似度：{{ evidence.similarity }}</template>
              </div>
              <div class="evidence-diff">
                <div class="diff-col">
                  <div class="diff-label">{{ evidence.docAName }} 原始文本描述</div>
                  <div class="diff-text">{{ evidence.docAContent }}</div>
                </div>
                <div class="diff-col">
                  <div class="diff-label">{{ evidence.docBName }} 原始文本描述</div>
                  <div class="diff-text">{{ evidence.docBContent }}</div>
                </div>
              </div>
              <div class="finding-footer">
                <strong>AI 判定逻辑 (Judgement)：</strong>
                {{ evidence.aiJudgment || evidence.comparisonFinding }}
              </div>
            </div>
          </template>
          <div v-else class="empty-state">当前未提取到可展示的证据明细</div>
        </section>

        <!-- Chapter6 处置建议 -->
        <section id="chapter-6" class="doc-section">
          <h2>六、处置建议</h2>
          <div v-if="data.actionPlan?.tasks?.length" class="action-table-wrap">
            <table class="doc-table">
              <thead>
                <tr>
                  <th>优先级</th>
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
          <div v-else class="empty-state">暂无处置建议</div>
        </section>

        <!-- Chapter7 报告边界说明 -->
        <section id="chapter-7" class="doc-section bottom-space">
          <h2>七、报告边界说明</h2>
          <ul class="boundary-list">
            <li>本报告用于辅助识别围标、串标或非独立编制风险，不直接替代最终评审或法律定性。</li>
            <li>若存在未上传附件、补充说明或历史投标材料，可能影响当前判断结果。</li>
            <li>建议结合项目背景、原文上下文和人工复核结论，决定是否升级处理。</li>
          </ul>
        </section>

        <!-- 原型 Footer: 电子验证章 + 合规复核 + 版权 -->
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

/** 暴露可滚动内容区域，供父组件做 PDF 导出 */
const docMainRef = ref<HTMLElement | null>(null);
defineExpose({ docMainRef });

const navs = [
  { id: 'chapter-1', label: '一、审查执行摘要' },
  { id: 'chapter-2', label: '二、审查文档元数据' },
  { id: 'chapter-3', label: '三、风险总览' },
  { id: 'chapter-4', label: '四、核心风险项识别' },
  { id: 'chapter-5', label: '五、关键取证对比' },
  { id: 'chapter-6', label: '六、处置建议' },
  { id: 'chapter-7', label: '七、报告边界' },
];

const activeAnchor = ref('chapter-1');

// 重点风险数据，支持新旧两种结构
const topRisks = computed<TopRisk[]>(() => {
  if (!props.data?.riskOverview) return [];
  // 优先使用新版 topRisks 字段
  if (props.data.riskOverview.topRisks?.length) {
    return props.data.riskOverview.topRisks;
  }
  // 兜底：从 distributions 中提取有发现的风险
  return props.data.riskOverview.distributions
    ?.filter(d => d.found)
    .map((d, i) => ({
      rank: i + 1,
      riskName: d.riskType,
      level: 'medium' as const,
      riskDesc: d.foundDescription,
      keyFact: d.brief || '',
      whyReview: d.needReviewText,
      action: '请结合证据明细进行人工复核',
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

// 原型新增：风险标签样式
function riskLevelTagClass(value?: string) {
  if (value === 'high') return 'tag-critical';
  if (value === 'medium') return 'tag-high';
  return 'tag-medium';
}

function riskLevelTagLabel(value?: string) {
  if (value === 'high') return '重大风险';
  if (value === 'medium') return '高风险';
  return '中风险';
}

// 原型新增：风险综合判定徽章颜色
const riskBadgeClass = computed(() => {
  const level = props.data?.executiveSummary?.riskLevelLabel || '';
  if (level.includes('重大')) return 'badge-danger';
  if (level.includes('中度')) return 'badge-warning';
  if (level.includes('轻度')) return 'badge-success';
  return 'badge-neutral';
});

// 原型新增：有效违规命中卡片样式
const effectiveHitsClass = computed(() => {
  const hits = props.data?.executiveSummary?.metrics?.effectiveHits ?? 0;
  return hits > 0 ? 'danger' : 'safe';
});

const effectiveHitsValue = computed(() => {
  return props.data?.executiveSummary?.metrics?.effectiveHits ?? 0;
});
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

/* Hero Panel */
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
  font-size: 28px;
  line-height: 1.45;
}

.hero-note {
  margin: 0;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  font-size: 14px;
  line-height: 1.6;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 14px;
  color: #64748b;
  font-size: 12px;
}

/* Metric Grid */
.metric-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-card {
  padding: 18px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 18px;
  text-align: center;
}

.metric-label {
  display: block;
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

/* Section */
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

/* Summary Card */
.summary-card {
  padding: 20px 22px;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;
}

.summary-text {
  margin: 0;
  font-size: 16px;
  line-height: 1.85;
  color: #334155;
}

/* Insight Grid */
.insight-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 14px;
}

.insight-card {
  padding: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 18px;
}

.insight-title {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.insight-card p {
  margin: 0;
  color: #334155;
  line-height: 1.75;
  font-size: 14px;
}

/* Document Table */
.document-table-wrap,
.overview-table-wrap,
.action-table-wrap {
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  overflow: hidden;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.doc-table th,
.doc-table td {
  border: 1px solid #cbd5e1;
  padding: 12px 14px;
  text-align: left;
}

.doc-table th {
  background: #f8fafc;
  font-weight: 700;
  color: #475569;
}

.doc-table td {
  color: #334155;
}

.doc-code-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #0f172a;
  color: #fff;
  font-weight: 800;
  font-size: 12px;
}

.file-name-cell {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Status Tags */
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.status-found {
  background: #fee2e2;
  color: #b91c1c;
}

.status-none {
  background: #dcfce7;
  color: #166534;
}

.status-review {
  background: #fef3c7;
  color: #b45309;
}

.status-ok {
  background: #e2e8f0;
  color: #475569;
}

/* Risk Card */
.risk-card {
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 18px;
}

.risk-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.risk-order {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 4px;
}

.risk-head h3 {
  margin: 0;
  font-size: 18px;
}

.level-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.risk-detail-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-card {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
}

.detail-label {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.detail-card p {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.action-card {
  background: linear-gradient(180deg, #fffaf0, #ffffff);
}

/* Evidence Card */
.evidence-card {
  padding: 20px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 18px;
}

.evidence-source {
  margin-bottom: 14px;
}

.source-tag {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
}

.diff-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 16px 0;
}

.diff-side {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.diff-side-a {
  border-left: 3px solid #3b82f6;
}

.diff-side-b {
  border-left: 3px solid #10b981;
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

.evidence-finding,
.evidence-review {
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: #f8fafc;
}

.evidence-review {
  background: #fffaf0;
}

/* Priority Badge */
.priority-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.priority-high {
  background: #fee2e2;
  color: #b91c1c;
}

.priority-medium {
  background: #fef3c7;
  color: #b45309;
}

.priority-low {
  background: #dcfce7;
  color: #166534;
}

.priority-neutral {
  background: #e2e8f0;
  color: #475569;
}

/* Boundary List */
.boundary-list {
  margin: 0;
  padding-left: 20px;
  line-height: 1.85;
  color: #334155;
}

.boundary-list li {
  margin-bottom: 8px;
}

/* Empty State */
.empty-state {
  padding: 24px;
  border-radius: 16px;
  background: #f8fafc;
  color: #64748b;
  text-align: center;
}

/* Color Utilities */
.color-danger { color: #b91c1c; }
.color-warning { color: #b45309; }
.color-safe { color: #166534; }
.color-neutral { color: #475569; }

.bottom-space { margin-bottom: 60px; }

/* ===== 原型 Header 区域 ===== */
.report-id-label {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  letter-spacing: 0.05em;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 36px;
  padding-bottom: 24px;
  border-bottom: 2px solid #0f172a;
}

.hero-copy { flex: 1; }

.hero-badge-area { flex-shrink: 0; }

.risk-badge-large {
  background: #b91c1c;
  color: white;
  padding: 12px 20px;
  border-radius: 4px;
  text-align: center;
  min-width: 160px;
}

.risk-badge-large .badge-label {
  display: block;
  font-size: 10px;
  font-weight: 700;
  opacity: 0.9;
  margin-bottom: 4px;
}

.risk-badge-large .badge-value {
  font-size: 16px;
  font-weight: 800;
}

.badge-danger { color: #b91c1c; }
.badge-warning { color: #b45309; }
.badge-success { color: #166534; }
.badge-neutral { color: #475569; }

/* ===== 原型 Section1: 审查执行摘要 ===== */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #f8fafc;
  padding: 20px;
  border-bottom: 4px solid #e2e8f0;
}

.stat-card.danger { border-bottom-color: #b91c1c; }
.stat-card.active { border-bottom-color: #1e40af; }
.stat-card.safe { border-bottom-color: #166534; }

.stat-label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.stat-value {
  display: block;
  font-size: 36px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 8px;
}

.conclusion-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 20px 24px;
  border-radius: 4px;
}

.conclusion-box p {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.85;
  color: #1e293b;
}

/* ===== 原型文档表格 ===== */
.file-code-tag {
  background: #0f172a;
  color: white;
  padding: 3px 10px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 700;
}

/* ===== 原型风险项（带规则编码） ===== */
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

.risk-rank .rank-num {
  font-size: 26px;
  font-weight: 900;
  color: #e2e8f0;
  line-height: 1;
}

.risk-rank .rule-code-small {
  font-size: 10px;
  font-weight: 800;
  color: #475569;
  margin-top: 4px;
}

.risk-content { flex: 1; padding: 22px; }

.risk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.risk-header h3 { font-size: 17px; font-weight: 800; margin: 0; }

.risk-level-tag {
  font-size: 10px;
  font-weight: 800;
  padding: 4px 12px;
  border-radius: 2px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.tag-critical { background: #fee2e2; color: #b91c1c; }
.tag-high { background: #ffedd5; color: #9a3412; }
.tag-medium { background: #fef3c7; color: #92400e; }

.risk-body {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 28px;
}

.risk-field {}

.field-label {
  display: block;
  font-size: 10px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.field-value {
  font-size: 14px;
  color: #0f172a;
  line-height: 1.7;
}

/* ===== 原型证据对比 ===== */
.evidence-box {
  background: #fff;
  border: 1px solid #e2e8f0;
  margin-bottom: 24px;
  border-radius: 4px;
  overflow: hidden;
}

.evidence-header {
  padding: 12px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 13px;
  font-weight: 700;
  color: #475569;
}

.evidence-diff {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.diff-col { padding: 20px; position: relative; }
.diff-col:first-child { border-right: 1px solid #e2e8f0; }

.diff-label {
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 10px;
  color: #475569;
  opacity: 0.8;
}

.diff-text {
  font-size: 13px;
  color: #1e293b;
  background: #fff;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.finding-footer {
  padding: 14px 20px;
  font-size: 13px;
  background: rgba(254, 243, 199, 0.4);
  border-top: 1px solid #e2e8f0;
  line-height: 1.7;
}

/* ===== 原型 Footer ===== */
.doc-footer {
  margin-top: 60px;
  padding-top: 36px;
  border-top: 1px solid #e2e8f0;
  text-align: center;
}

.seal-group {
  display: flex;
  justify-content: center;
  gap: 100px;
  margin-bottom: 28px;
}

.seal-item p { font-size: 12px; color: #475569; font-weight: 500; }
.seal-line { width: 140px; height: 1px; background: #94a3b8; margin: 10px auto; }
.copyright { font-size: 10px; color: #94a3b8; margin-top: 36px; }

@media (max-width: 1080px) {
  .formal-document-layout {
    display: block;
    height: auto;
  }
  .doc-sidebar { display: none; }
  .doc-main { padding: 28px 20px; }
  .hero-panel { flex-direction: column; align-items: flex-start; }
  .summary-grid,
  .risk-body,
  .evidence-diff { grid-template-columns: 1fr; }
  .seal-group { flex-direction: column; gap: 24px; align-items: center; }
}
</style>
