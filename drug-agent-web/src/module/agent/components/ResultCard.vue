<template>
  <div class="result-card">
    <!-- 风险等级标签 -->
    <div class="risk-badge" :class="`risk-${data?.riskLevel || 'unknown'}`">
      {{ riskLabel }}
    </div>

    <!-- 摘要信息 -->
    <div v-if="data?.summary" class="summary">
      {{ data.summary }}
    </div>

    <!-- 分数 -->
    <div v-if="data?.score !== undefined" class="score-info">
      <span class="score-label">风险评分：</span>
      <span class="score-value" :class="scoreClass">{{ data.score }}</span>
    </div>

    <!-- 查看详情按钮 -->
    <div class="action-area">
      <t-button theme="primary" variant="outline" size="small" @click="handleViewDetail">
        查看详情
      </t-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';
import type { ResultData } from '../types/agent';

const props = defineProps<{
  data?: ResultData;
}>();

const store = useAgentStore();

const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '安全',
    unknown: '未知'
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
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  min-width: 300px;
  max-width: 500px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.risk-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 12px;
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

.summary {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  margin-bottom: 12px;
}

.score-info {
  font-size: 14px;
  margin-bottom: 12px;
}

.score-label {
  color: #666;
}

.score-value {
  font-weight: 600;
}

.score-high {
  color: #cf1322;
}

.score-medium {
  color: #d46b08;
}

.score-low {
  color: #389e0d;
}

.action-area {
  display: flex;
  justify-content: flex-end;
}
</style>
