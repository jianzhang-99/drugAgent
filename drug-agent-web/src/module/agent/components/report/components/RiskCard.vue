<template>
  <div class="risk-card" :class="`card-${level?.toLowerCase() || 'unknown'}`">
    <div class="card-header">
      <span class="card-rank" v-if="rank">{{ rank }}</span>
      <span class="card-type">{{ typeName }}</span>
      <span class="card-level" :class="`badge-${level?.toLowerCase()}`">
        {{ levelLabel(level) }}
      </span>
    </div>
    <div class="card-title">{{ title }}</div>
    <div class="card-summary" v-if="summary">{{ summary }}</div>
    <div class="card-action" v-if="action">
      <span class="action-label">建议动作：</span>
      <span class="action-text">{{ action }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  rank?: number;
  type?: string;
  typeName?: string;
  title: string;
  level?: string;
  summary?: string;
  action?: string;
}

withDefaults(defineProps<Props>(), {
  typeName: '',
  title: '',
});

// 风险等级标签
function levelLabel(level?: string): string {
  if (!level) return '未知';
  const map: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    info: '提示',
  };
  return map[level.toLowerCase()] || '未知';
}
</script>

<style scoped>
.risk-card {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid;
}

.card-high {
  background: #fff9f9;
  border-color: #ffccc7;
}

.card-medium {
  background: #fffbf0;
  border-color: #ffe58f;
}

.card-low {
  background: #f0f5ff;
  border-color: #adc6ff;
}

.card-unknown {
  background: #f4f5f7;
  border-color: #e2e4e9;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.card-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  color: #4e5969;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e8eaf0;
}

.card-type {
  font-size: 13px;
  color: #4e5969;
}

.card-level {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
  margin-left: auto;
}

.badge-high {
  background: #fff1f0;
  color: #f53f3f;
}

.badge-medium {
  background: #fff7e6;
  color: #faad14;
}

.badge-low {
  background: #e8f0ff;
  color: #165dff;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 6px;
}

.card-summary {
  font-size: 13px;
  color: #4e5969;
  line-height: 1.5;
  margin-bottom: 8px;
}

.card-action {
  font-size: 12px;
  color: #86909c;
}

.action-text {
  color: #165dff;
}
</style>
