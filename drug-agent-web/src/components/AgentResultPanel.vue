<template>
  <section v-if="result" class="result-panel">
    <header class="result-header">
      <div>
        <p class="eyebrow">场景化结果</p>
        <h2>{{ panelTitle }}</h2>
        <p class="summary">{{ result.summary || result.answer || '已生成结果。' }}</p>
      </div>
      <div class="header-side">
        <span class="scene-chip">{{ result.scene }}</span>
        <span class="risk-chip" :class="riskClass">{{ result.riskLevel || 'UNKNOWN' }}</span>
      </div>
    </header>

    <section v-if="isTenderReview" class="report-layout">
      <article class="report-card emphasis">
        <div class="card-head">
          <h3>审核概览</h3>
          <span v-if="overview.generatedAt">{{ overview.generatedAt }}</span>
        </div>
        <div class="metrics-grid">
          <div class="metric-item">
            <span>文档数</span>
            <strong>{{ overview.documentCount || 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>有效命中</span>
            <strong>{{ overview.effectiveHitCount || 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>免责数量</span>
            <strong>{{ overview.exemptionCount || 0 }}</strong>
          </div>
          <div class="metric-item">
            <span>融合分值</span>
            <strong>{{ overview.score || 0 }}</strong>
          </div>
        </div>
      </article>

      <article class="report-card">
        <div class="card-head">
          <h3>管理摘要</h3>
        </div>
        <ul class="flat-list">
          <li v-for="item in managementSummary" :key="item">{{ item }}</li>
        </ul>
      </article>

      <article class="report-card">
        <div class="card-head">
          <h3>建议动作</h3>
        </div>
        <ul class="flat-list">
          <li v-for="item in recommendedActions" :key="item">{{ item }}</li>
        </ul>
      </article>

      <article class="report-card wide">
        <div class="card-head">
          <h3>重点风险主题</h3>
        </div>
        <div v-if="riskItems.length" class="risk-list">
          <div v-for="item in riskItems" :key="`${item.title}-${item.riskType}`" class="risk-item">
            <div class="risk-top">
              <strong>{{ item.title }}</strong>
              <span class="risk-mini" :class="levelToClass(item.riskLevel)">{{ item.riskLevel }}</span>
            </div>
            <p>{{ item.summary }}</p>
            <p v-if="item.reasonCodes?.length" class="mini-meta">规则：{{ item.reasonCodes.join('、') }}</p>
            <p v-if="item.evidenceTitles?.length" class="mini-meta">证据：{{ item.evidenceTitles.join('；') }}</p>
          </div>
        </div>
        <p v-else class="empty-text">当前没有重点风险主题。</p>
      </article>

      <article class="report-card wide">
        <div class="card-head">
          <h3>证据摘要</h3>
        </div>
        <div v-if="evidenceList.length" class="evidence-list">
          <div v-for="(item, index) in evidenceList" :key="`${item.title}-${index}`" class="evidence-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.content }}</p>
            <span>{{ item.source }}</span>
          </div>
        </div>
        <p v-else class="empty-text">当前没有可展示的证据摘要。</p>
      </article>
    </section>

    <section v-else class="report-layout">
      <article class="report-card emphasis">
        <div class="card-head">
          <h3>处理结论</h3>
        </div>
        <p class="primary-answer">{{ result.answer || '已完成结果生成。' }}</p>
      </article>

      <article class="report-card">
        <div class="card-head">
          <h3>执行步骤</h3>
        </div>
        <ul class="flat-list">
          <li v-for="step in steps" :key="step">{{ step }}</li>
        </ul>
      </article>

      <article class="report-card">
        <div class="card-head">
          <h3>证据摘要</h3>
        </div>
        <ul class="flat-list">
          <li v-for="(item, index) in evidenceList" :key="`${item.title}-${index}`">
            <strong>{{ item.title }}：</strong>{{ item.content }}
          </li>
        </ul>
      </article>
    </section>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: { type: Object, default: null }
})

const isTenderReview = computed(() => props.result?.scene === 'TENDER_REVIEW')
const report = computed(() => props.result?.report || props.result?.structuredData?.report || {})
const overview = computed(() => ({
  ...(report.value?.overview || {}),
  generatedAt: report.value?.generatedAt || ''
}))
const managementSummary = computed(() => report.value?.managementSummary || [])
const recommendedActions = computed(() => report.value?.recommendedActions || [])
const riskItems = computed(() => report.value?.riskItems || [])
const evidenceList = computed(() => props.result?.evidenceList || [])
const steps = computed(() => props.result?.steps || [])
const panelTitle = computed(() => {
  if (props.result?.scene === 'TENDER_REVIEW') return '标书审核报告'
  if (props.result?.scene === 'CONTRACT_PRECHECK') return '合同预审结果'
  if (props.result?.scene === 'RISK_ALERT') return '风险预警结果'
  return 'Agent 处理结果'
})

const riskClass = computed(() => levelToClass(props.result?.riskLevel))

function levelToClass(level) {
  if (level === 'HIGH') return 'risk-high'
  if (level === 'MEDIUM' || level === 'PENDING') return 'risk-medium'
  if (level === 'LOW' || level === 'NONE') return 'risk-low'
  return 'risk-neutral'
}
</script>

<style scoped>
.result-panel {
  background:
    radial-gradient(circle at top right, rgba(37, 99, 235, 0.08), transparent 28%),
    linear-gradient(180deg, #ffffff, #f9fbff);
  border: 1px solid #dbe6fb;
  border-radius: 28px;
  padding: 28px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
  margin-bottom: 36px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 1.6px;
  text-transform: uppercase;
  color: #5d6f93;
  font-weight: 800;
}

.result-header h2 {
  margin: 0 0 8px;
  font-size: 30px;
  line-height: 1.1;
}

.summary {
  margin: 0;
  color: #53627d;
  max-width: 760px;
  line-height: 1.7;
}

.header-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}

.scene-chip,
.risk-chip,
.risk-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.scene-chip {
  background: #e9f0ff;
  color: #1d4ed8;
}

.risk-high {
  background: #fee2e2;
  color: #b91c1c;
}

.risk-medium {
  background: #fef3c7;
  color: #b45309;
}

.risk-low {
  background: #dcfce7;
  color: #15803d;
}

.risk-neutral {
  background: #e5e7eb;
  color: #475569;
}

.report-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.report-card {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid #e6eefc;
  border-radius: 22px;
  padding: 20px;
}

.report-card.emphasis {
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.report-card.wide {
  grid-column: 1 / -1;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.card-head h3 {
  margin: 0;
  font-size: 18px;
}

.card-head span {
  color: #74819b;
  font-size: 12px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-item {
  background: white;
  border-radius: 16px;
  padding: 14px;
  border: 1px solid #e7eefb;
}

.metric-item span,
.mini-meta,
.evidence-item span {
  color: #6b7892;
  font-size: 12px;
}

.metric-item strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.flat-list {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  line-height: 1.7;
}

.risk-list,
.evidence-list {
  display: grid;
  gap: 12px;
}

.risk-item,
.evidence-item {
  background: #fff;
  border: 1px solid #e9effa;
  border-radius: 16px;
  padding: 16px;
}

.risk-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.risk-item p,
.evidence-item p,
.primary-answer {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.primary-answer {
  font-size: 16px;
}

.mini-meta {
  margin-top: 8px;
}

.empty-text {
  margin: 0;
  color: #74819b;
}

@media (max-width: 768px) {
  .result-header {
    flex-direction: column;
  }

  .header-side {
    align-items: flex-start;
  }

  .report-layout,
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
