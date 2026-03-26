<template>
  <div class="result-card">
    <div class="card-top">
      <div class="risk-badge" :class="`risk-${data?.riskLevel || 'unknown'}`">
        {{ riskLabel }}
      </div>
      <div v-if="data?.score !== undefined" class="score-ring" :class="scoreClass">
        <span>{{ data.score }}</span>
      </div>
    </div>

    <div v-if="data?.summary" class="summary">
      {{ data.summary }}
    </div>

    <div v-if="data?.steps?.length" class="step-grid">
      <div v-for="step in data.steps.slice(0, 4)" :key="step" class="step-chip">
        {{ step }}
      </div>
    </div>

    <div class="action-area">
      <div class="action-copy">可查看证据链、报告结论和建议动作</div>
      <t-button theme="primary" size="small" @click="handleViewDetail">
        打开详情
      </t-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';
import type { ResultData } from '../types/agent';

const props = defineProps<{ data?: ResultData }>();
const store = useAgentStore();

const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '安全',
    unknown: '未知',
  };
  return map[props.data?.riskLevel || 'unknown'] || '未知';
});

const scoreClass = computed(() => {
  const score = props.data?.score || 0;
  if (score >= 80) return 'score-high';
  if (score >= 60) return 'score-medium';
  return 'score-low';
});

function handleViewDetail() {
  if (props.data) {
    store.setCurrentResult(props.data as any);
  }
}
</script>

<style scoped>
.result-card {
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 250, 0.96));
  border-radius: 24px;
  padding: 18px;
  min-width: 320px;
  max-width: 620px;
  border: 1px solid rgba(19, 49, 59, 0.08);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.risk-badge {
  display: inline-block;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.risk-high {
  background: #fff2f0;
  color: #cf1322;
}

.risk-medium {
  background: #fff7e6;
  color: #d46b08;
}

.risk-low {
  background: #f9f0ff;
  color: #722ed1;
}

.risk-safe {
  background: #f6ffed;
  color: #389e0d;
}

.risk-unknown {
  background: #f5f5f5;
  color: #666;
}

.score-ring {
  width: 58px;
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  border: 4px solid rgba(19, 49, 59, 0.08);
  font-weight: 700;
  color: #0f2f39;
}

.score-ring.score-high {
  border-color: rgba(207, 19, 34, 0.24);
}

.score-ring.score-medium {
  border-color: rgba(212, 107, 8, 0.24);
}

.score-ring.score-low {
  border-color: rgba(56, 158, 13, 0.24);
}

.summary {
  font-size: 14px;
  line-height: 1.75;
  color: #24414c;
  margin-bottom: 14px;
}

.step-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.step-chip {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(19, 49, 59, 0.06);
  color: #49626d;
  font-size: 12px;
}

.action-area {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.action-copy {
  font-size: 12px;
  color: #6b8793;
}
</style>
