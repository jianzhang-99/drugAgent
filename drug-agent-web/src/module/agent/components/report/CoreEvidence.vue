<template>
  <div v-if="data" class="core-evidence">
    <div class="intro-card">
      核心证据页只保留最能支撑结论的 3-5 条证据，帮助评审人员快速确认“为什么系统认为有风险”。
    </div>

    <article
      v-for="evidence in data.evidenceList"
      :key="evidence.id"
      class="evidence-card"
      :class="`card-${normalizeLevel(evidence.level)}`"
    >
      <div class="card-head">
        <div class="head-left">
          <span class="evidence-id">{{ evidence.id }}</span>
          <div>
            <div class="evidence-type">{{ evidence.type }}</div>
            <div class="evidence-meta">
              <span>{{ levelLabel(evidence.level) }}</span>
              <span>置信度：{{ evidence.confidence }}</span>
            </div>
          </div>
        </div>
        <span class="evidence-level">{{ levelLabel(evidence.level) }}</span>
      </div>

      <h3 class="evidence-title">{{ evidence.title }}</h3>
      <p class="evidence-explanation">{{ evidence.explanation }}</p>

      <div v-if="hasFindings(evidence.keyFindings)" class="findings-grid">
        <div
          v-for="(value, key) in evidence.keyFindings"
          :key="key"
          class="finding-item"
        >
          <span class="finding-key">{{ key }}</span>
          <span class="finding-value">{{ formatValue(value) }}</span>
        </div>
      </div>

      <div class="detail-grid">
        <div class="detail-card">
          <div class="detail-label">判定依据</div>
          <p>{{ evidence.basis }}</p>
        </div>
        <div class="detail-card action-card">
          <div class="detail-label">建议动作</div>
          <p>{{ evidence.action }}</p>
        </div>
      </div>
    </article>
  </div>

  <div v-else class="empty-state">暂无核心证据数据</div>
</template>

<script setup lang="ts">
import type { Page3CoreEvidence } from '../../types/report.types';

interface Props {
  data?: Page3CoreEvidence;
}

defineProps<Props>();

function normalizeLevel(level?: string): string {
  if (!level) return 'safe';
  const value = level.toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  return 'safe';
}

function levelLabel(level?: string): string {
  return {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '提示',
  }[normalizeLevel(level)] || '提示';
}

function hasFindings(findings?: Record<string, unknown>) {
  return !!findings && Object.keys(findings).length > 0;
}

function formatValue(value: unknown) {
  if (Array.isArray(value)) return value.join('、');
  if (value && typeof value === 'object') return JSON.stringify(value);
  return String(value ?? '-');
}
</script>

<style scoped>
.core-evidence {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro-card {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fffaf0, #ffffff);
  border: 1px solid #fde68a;
  color: #334155;
  line-height: 1.7;
}

.evidence-card {
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.card-high {
  border-color: #fecaca;
}

.card-medium {
  border-color: #fde68a;
}

.card-low,
.card-safe {
  border-color: #bfdbfe;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.head-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.evidence-id {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-weight: 800;
}

.evidence-type {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.evidence-meta {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.evidence-level {
  display: inline-flex;
  align-items: center;
  height: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fee2e2;
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.evidence-title {
  margin: 0 0 10px;
  font-size: 22px;
  color: #0f172a;
}

.evidence-explanation {
  margin: 0 0 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fafc;
  color: #334155;
  line-height: 1.7;
}

.findings-grid,
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.findings-grid {
  margin-bottom: 14px;
}

.finding-item,
.detail-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.finding-key,
.detail-label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.finding-value {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.5;
}

.detail-card p {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.action-card {
  background: linear-gradient(180deg, #fffaf0, #ffffff);
}

.empty-state {
  padding: 32px;
  text-align: center;
  color: #94a3b8;
}

@media (max-width: 860px) {
  .card-head,
  .head-left {
    flex-direction: column;
    align-items: flex-start;
  }

  .findings-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
