<template>
  <div v-if="data" class="report-summary">
    <section class="hero-card" :class="`hero-${levelCode}`">
      <div class="hero-main">
        <div class="hero-kicker">审查结论</div>
        <div class="hero-level">
          <span class="level-dot"></span>
          <span>{{ levelLabel(levelCode) }}</span>
        </div>
        <h2 class="hero-title">{{ data.conclusion }}</h2>
        <p v-if="data.recommendedAction" class="hero-action">{{ data.recommendedAction }}</p>
      </div>

      <div class="hero-metrics">
        <div class="metric-card emphasis">
          <span class="metric-label">风险分</span>
          <span class="metric-value">{{ data.riskScore }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">涉及文档</span>
          <span class="metric-value">{{ data.documents?.length || 0 }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">核心证据</span>
          <span class="metric-value">{{ data.coreEvidenceCount }}</span>
        </div>
        <div class="metric-card">
          <span class="metric-label">命中规则</span>
          <span class="metric-value">{{ data.ruleHitCount }}</span>
        </div>
      </div>
    </section>

    <section v-if="data.coreRiskTop3?.length" class="panel">
      <div class="panel-header">
        <span class="panel-title">核心风险 Top 3</span>
        <span class="panel-tip">先看最能支撑结论的风险项</span>
      </div>
      <div class="risk-grid">
        <article
          v-for="risk in data.coreRiskTop3"
          :key="risk.rank"
          class="risk-card"
          :class="`risk-${normalizeLevel(risk.level)}`"
        >
          <div class="risk-top">
            <span class="risk-rank">风险 {{ risk.rank }}</span>
            <span class="risk-badge">{{ levelLabel(normalizeLevel(risk.level)) }}</span>
          </div>
          <h3 class="risk-title">{{ risk.title }}</h3>
          <div class="risk-type">{{ riskTypeLabel(risk.riskType) }}</div>
          <p class="risk-summary">{{ risk.summary }}</p>
          <div class="risk-action">
            <span class="action-label">建议动作</span>
            <span class="action-value">{{ risk.action }}</span>
          </div>
        </article>
      </div>
    </section>

    <section class="panel">
      <div class="panel-header">
        <span class="panel-title">风险分布概览</span>
        <span class="panel-tip">帮助判断风险由哪些维度构成</span>
      </div>
      <div class="distribution-grid">
        <div
          v-for="(level, label) in data.riskDistribution"
          :key="label"
          class="distribution-item"
          :class="`dist-${normalizeLevel(level)}`"
        >
          <span class="distribution-label">{{ label }}</span>
          <span class="distribution-value">{{ levelLabel(normalizeLevel(level)) }}</span>
        </div>
      </div>
    </section>

    <section v-if="data.documents?.length" class="panel">
      <div class="panel-header">
        <span class="panel-title">涉及文档</span>
        <span class="panel-tip">主视图优先展示业务信息，内部编号保留在次级层</span>
      </div>
      <div class="document-grid">
        <article v-for="(doc, index) in data.documents" :key="doc.docId" class="doc-card">
          <div class="doc-tag">{{ String.fromCharCode(65 + index) }}</div>
          <div class="doc-body">
            <div class="doc-party">{{ doc.party }}</div>
            <div class="doc-name">{{ doc.docName }}</div>
            <div class="doc-role">{{ doc.role }}</div>
            <div class="doc-id">内部编号：{{ doc.internalId }}</div>
          </div>
        </article>
      </div>
    </section>
  </div>

  <div v-else class="empty-state">暂无审查结论数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Page1Summary } from '../../types/report.types';

interface Props {
  data?: Page1Summary;
}

const props = defineProps<Props>();

const levelCode = computed(() => normalizeLevel(props.data?.riskLevel));

function normalizeLevel(level?: string): string {
  if (!level) return 'safe';
  const value = level.toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  if (value.includes('safe') || value.includes('正常') || value.includes('未见')) return 'safe';
  return 'safe';
}

function levelLabel(level?: string): string {
  return {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '未见明显异常',
  }[normalizeLevel(level)] || '未见明显异常';
}

function riskTypeLabel(type?: string): string {
  const normalized = (type || '').toLowerCase();
  if (normalized === 'pricing') return '报价风险';
  if (normalized === 'team') return '团队风险';
  if (normalized === 'text_similarity' || normalized === 'plagiarism') return '文本相似风险';
  if (normalized === 'template') return '模板同源风险';
  if (normalized === 'auxiliary') return '其他辅助风险';
  return '综合风险';
}
</script>

<style scoped>
.report-summary {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-card,
.panel {
  border-radius: 20px;
  border: 1px solid #dde4ee;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.95), transparent 38%),
    linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.06);
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.9fr);
  gap: 20px;
  padding: 24px;
}

.hero-high {
  border-color: #fecaca;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.92), transparent 38%),
    linear-gradient(135deg, #fff5f5, #fffaf7 55%, #ffffff);
}

.hero-medium {
  border-color: #fde68a;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.92), transparent 38%),
    linear-gradient(135deg, #fffaf0, #fffef8 55%, #ffffff);
}

.hero-low,
.hero-safe {
  border-color: #bfdbfe;
  background:
    radial-gradient(circle at top right, rgba(255, 255, 255, 0.92), transparent 38%),
    linear-gradient(135deg, #f3f7ff, #fbfdff 55%, #ffffff);
}

.hero-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #64748b;
}

.hero-level {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.level-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ef4444;
}

.hero-medium .level-dot {
  background: #f59e0b;
}

.hero-low .level-dot,
.hero-safe .level-dot {
  background: #2563eb;
}

.hero-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.45;
  color: #0f172a;
}

.hero-action {
  margin: 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  color: #334155;
  line-height: 1.7;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 112px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.metric-card.emphasis {
  background: linear-gradient(180deg, #0f172a, #1e293b);
}

.metric-card.emphasis .metric-label,
.metric-card.emphasis .metric-value {
  color: #f8fafc;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
}

.metric-value {
  font-size: 34px;
  font-weight: 800;
  color: #0f172a;
}

.panel {
  padding: 20px;
}

.panel-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
}

.panel-tip {
  font-size: 12px;
  color: #64748b;
}

.risk-grid,
.document-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.risk-card,
.doc-card {
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  padding: 16px;
}

.risk-high {
  border-color: #fecaca;
}

.risk-medium {
  border-color: #fde68a;
}

.risk-low,
.risk-safe {
  border-color: #bfdbfe;
}

.risk-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.risk-rank,
.risk-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.risk-rank {
  background: #e2e8f0;
  color: #334155;
}

.risk-badge {
  background: #fee2e2;
  color: #b91c1c;
}

.risk-title {
  margin: 0 0 6px;
  font-size: 18px;
  color: #0f172a;
}

.risk-type {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 10px;
}

.risk-summary {
  margin: 0 0 14px;
  color: #334155;
  line-height: 1.7;
  min-height: 68px;
}

.risk-action {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px dashed #dbe3ee;
}

.action-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.action-value {
  color: #0f172a;
  line-height: 1.6;
}

.distribution-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.distribution-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.dist-high {
  background: linear-gradient(180deg, #fff5f5, #ffffff);
}

.dist-medium {
  background: linear-gradient(180deg, #fffaf0, #ffffff);
}

.dist-low,
.dist-safe {
  background: linear-gradient(180deg, #f3f7ff, #ffffff);
}

.distribution-label {
  font-size: 13px;
  color: #475569;
}

.distribution-value {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.doc-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.doc-tag {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-size: 15px;
  font-weight: 800;
  flex-shrink: 0;
}

.doc-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-party {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.doc-name,
.doc-role {
  color: #334155;
}

.doc-id {
  font-size: 12px;
  color: #94a3b8;
}

.empty-state {
  padding: 32px;
  text-align: center;
  color: #94a3b8;
}

@media (max-width: 1100px) {
  .hero-card {
    grid-template-columns: 1fr;
  }

  .risk-grid,
  .document-grid,
  .distribution-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .hero-card,
  .panel {
    padding: 16px;
  }

  .hero-metrics,
  .risk-grid,
  .document-grid,
  .distribution-grid {
    grid-template-columns: 1fr;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
