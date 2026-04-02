<template>
  <div class="result-card-wrapper" :class="[`wrapper-risk-${data?.riskLevel || 'unknown'}`]">
    <div class="result-card">
      <div class="card-header">
        <div class="header-left">
          <div class="icon-box" :class="`icon-bg-${data?.riskLevel || 'unknown'}`">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
              <polyline points="10 9 9 9 8 9"></polyline>
            </svg>
          </div>
          <span class="scene-title">{{ data?.scene || 'TENDER_REVIEW' }}</span>
          <span class="trace-id" v-if="data?.traceId">ID:{{ data.traceId }}</span>
        </div>
        <div class="risk-badge" :class="`risk-${data?.riskLevel || 'unknown'}`">
          <t-icon name="error-circle" v-if="data?.riskLevel === 'high'" />
          <t-icon name="info-circle" v-else />
          <span>{{ riskLabel }}</span>
        </div>
      </div>

      <div class="card-body" v-if="data?.summary">
        <div class="summary-text">{{ data.summary }}</div>
      </div>

      <div class="card-footer">
        <div class="stats-area">
          <div class="stat-box">
            <div class="stat-label">综合评分</div>
            <div class="stat-value">{{ data?.score !== undefined ? data.score : 0 }} <span class="stat-unit">分</span></div>
          </div>
          <div class="stat-box">
            <div class="stat-label">处理文档</div>
            <div class="stat-value">{{ data?.docCount !== undefined ? data.docCount : (data?.steps?.length || 2) }} <span class="stat-unit">份</span></div>
          </div>
        </div>
        <div class="action-btn" @click="handleViewDetail">
          查看详细报告 <t-icon name="chevron-right" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';
import type { ResultData, DrugAgentResp } from '../types/agent';

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

function handleViewDetail() {
  if (props.data) {
    store.setCurrentResult(props.data as DrugAgentResp);
  }
}
</script>

<style scoped>
.result-card-wrapper {
  position: relative;
  margin-top: 10px; /* Space for the top highlight */
}

/* 顶部的彩色色块效果 */
.result-card-wrapper::before {
  content: '';
  position: absolute;
  top: -6px;
  left: 32px;
  right: 32px;
  height: 20px;
  border-radius: 12px 12px 0 0;
  z-index: 0;
  box-shadow: 0 4px 10px rgba(0,0,0,0.05);
}

.wrapper-risk-high::before { background: #f53f3f; }
.wrapper-risk-medium::before { background: #faad14; }
.wrapper-risk-low::before { background: #165dff; }
.wrapper-risk-safe::before { background: #00b42a; }
.wrapper-risk-unknown::before { background: #e5e6eb; }

.result-card {
  position: relative;
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  min-width: 480px;
  max-width: 620px;
  border: 1px solid #e5e6eb;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
  z-index: 1;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
}

.icon-bg-high { background: #ffece8; color: #f53f3f; }
.icon-bg-medium { background: #fff7e8; color: #faad14; }
.icon-bg-low { background: #f0f5ff; color: #165dff; }
.icon-bg-safe { background: #e8ffea; color: #00b42a; }
.icon-bg-unknown { background: #f2f3f5; color: #86909c; }

.scene-title {
  font-size: 14px;
  font-weight: 700;
  color: #4e5969;
  letter-spacing: 1px;
}

.trace-id {
  font-size: 12px;
  color: #a3a6ad;
  margin-left: 2px;
}

.risk-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  border: 1px solid transparent;
}

.risk-high { background: #fff1f0; color: #f53f3f; border-color: #ffccc7; }
.risk-medium { background: #fff7e8; color: #faad14; border-color: #ffe4ba; }
.risk-low { background: #f0f5ff; color: #165dff; border-color: #bbf; }
.risk-safe { background: #e8ffea; color: #00b42a; border-color: #b7efc5; }
.risk-unknown { background: #f2f3f5; color: #86909c; border-color: #e5e6eb; }

.card-body {
  margin-bottom: 36px;
}

.summary-text {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
  line-height: 1.6;
}

.card-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.stats-area {
  display: flex;
  gap: 12px;
}

.stat-box {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px 16px;
  min-width: 80px;
}

.stat-label {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1;
}

.stat-unit {
  font-size: 12px;
  font-weight: normal;
  color: #86909c;
  margin-left: 2px;
}

.action-btn {
  font-size: 13px;
  color: #f53f3f;
  display: flex;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  font-weight: 500;
  transition: opacity 0.2s;
}

.action-btn:hover {
  opacity: 0.8;
}
</style>
