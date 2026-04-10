<template>
  <div class="result-card-wrapper" :class="[`wrapper-risk-${data?.riskLevel || 'unknown'}`]">
    <div class="result-card" @click="handleViewDetail">
      <!-- 顶部装饰条 -->
      <div class="card-top-bar" :class="`bar-risk-${data?.riskLevel || 'unknown'}`"></div>

      <div class="card-inner">
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="header-title-group">
            <div class="scene-icon" :class="`icon-bg-${data?.riskLevel || 'unknown'}`">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
            </div>
            <div>
              <div class="scene-name">标书围标风险审查</div>
              <div class="trace-meta" v-if="data?.traceId">
                <span class="trace-dot"></span>
                <span>TRACE · {{ data.traceId }}</span>
              </div>
            </div>
          </div>
          <div class="risk-badge" :class="`risk-${data?.riskLevel || 'unknown'}`">
            <span class="risk-dot"></span>
            {{ riskLabel }}
          </div>
        </div>

        <!-- 摘要 -->
        <div class="summary-block" v-if="data?.summary">
          <p class="summary-text">{{ data.summary }}</p>
        </div>

        <!-- 文档列表 -->
        <div class="docs-block" v-if="data?.documentNames?.length">
          <div class="docs-label">
            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/>
            </svg>
            审查文件
          </div>
          <div class="docs-chips">
            <span
              v-for="(name, i) in data.documentNames"
              :key="i"
              class="doc-chip"
              :class="i === 0 ? 'chip-a' : 'chip-b'"
            >
              {{ name }}
            </span>
          </div>
        </div>

        <!-- 指标行 -->
        <div class="metrics-row">
          <div class="metric-item">
            <div class="metric-value" :class="`text-risk-${data?.riskLevel || 'unknown'}`">
              {{ data?.score !== undefined ? data.score : '—' }}
            </div>
            <div class="metric-label">相似度评分</div>
          </div>
          <div class="metric-divider"></div>
          <div class="metric-item">
            <div class="metric-value">
              {{ data?.docCount !== undefined ? data.docCount : (data?.documentNames?.length || 2) }}
            </div>
            <div class="metric-label">比对文档</div>
          </div>
          <div class="metric-divider"></div>
          <div class="metric-item">
            <div class="metric-value">
              {{ riskItemCount }}
            </div>
            <div class="metric-label">风险条目</div>
          </div>
        </div>

        <!-- 底部操作 -->
        <div class="card-footer">
          <div class="footer-hint">点击查看完整报告</div>
          <div class="view-btn">
            <span>查看详情</span>
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <polyline points="9 18 15 12 9 6"></polyline>
            </svg>
          </div>
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

const riskItemCount = computed(() => {
  return (props.data as any)?.report?.riskItems?.length ?? '—';
});

function handleViewDetail() {
  if (props.data) {
    store.setCurrentResult(props.data as DrugAgentResp);
  }
}
</script>

<style scoped>
.result-card-wrapper {
  margin-top: 8px;
  width: 100%;
  max-width: 560px;
}

.result-card {
  position: relative;
  background: #ffffff;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #e5e6eb;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.12);
}

.result-card:active {
  transform: translateY(0);
}

/* 顶部装饰条 */
.card-top-bar {
  height: 4px;
  width: 100%;
}
.bar-risk-high { background: linear-gradient(90deg, #f53f3f, #ff8080); }
.bar-risk-medium { background: linear-gradient(90deg, #faad14, #ffc940); }
.bar-risk-low { background: linear-gradient(90deg, #165dff, #5b8df6); }
.bar-risk-safe { background: linear-gradient(90deg, #00b42a, #52c41a); }
.bar-risk-unknown { background: linear-gradient(90deg, #c9cdd4, #e5e6eb); }

.card-inner {
  padding: 20px 24px;
}

/* 卡片头部 */
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-title-group {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.scene-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  margin-top: 2px;
}

.icon-bg-high { background: #fff1f0; color: #f53f3f; }
.icon-bg-medium { background: #fff7e8; color: #faad14; }
.icon-bg-low { background: #f0f5ff; color: #165dff; }
.icon-bg-safe { background: #e8ffea; color: #00b42a; }
.icon-bg-unknown { background: #f2f3f5; color: #86909c; }

.scene-name {
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.4;
}

.trace-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 3px;
  font-size: 11px;
  color: #c2c7d0;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.trace-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #c2c7d0;
}

/* 风险徽章 */
.risk-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 2px;
}

.risk-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.risk-high {
  background: #fff1f0;
  color: #f53f3f;
  border: 1px solid #ffccc7;
}
.risk-high .risk-dot { background: #f53f3f; box-shadow: 0 0 0 3px rgba(245,63,63,0.2); }

.risk-medium {
  background: #fff7e6;
  color: #faad14;
  border: 1px solid #ffe58f;
}
.risk-medium .risk-dot { background: #faad14; box-shadow: 0 0 0 3px rgba(250,173,20,0.2); }

.risk-low {
  background: #e8f0ff;
  color: #165dff;
  border: 1px solid #adc6ff;
}
.risk-low .risk-dot { background: #165dff; box-shadow: 0 0 0 3px rgba(22,93,255,0.2); }

.risk-safe {
  background: #e8ffea;
  color: #00b42a;
  border: 1px solid #b7efc5;
}
.risk-safe .risk-dot { background: #00b42a; box-shadow: 0 0 0 3px rgba(0,180,42,0.2); }

.risk-unknown {
  background: #f2f3f5;
  color: #86909c;
  border: 1px solid #e5e6eb;
}
.risk-unknown .risk-dot { background: #c9cdd4; }

/* 摘要区 */
.summary-block {
  background: #f7f8fa;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
}

.summary-text {
  font-size: 13.5px;
  color: #4e5969;
  line-height: 1.65;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 文档区 */
.docs-block {
  margin-bottom: 16px;
}

.docs-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #86909c;
  margin-bottom: 8px;
}

.docs-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.doc-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-a {
  background: #eff4ff;
  color: #2b5fd9;
  border: 1px solid #c8d9ff;
}

.chip-b {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}

/* 指标行 */
.metrics-row {
  display: flex;
  align-items: center;
  background: #f7f8fa;
  border-radius: 10px;
  padding: 12px 0;
  margin-bottom: 16px;
}

.metric-item {
  flex: 1;
  text-align: center;
}

.metric-value {
  font-size: 22px;
  font-weight: 800;
  color: #1d2129;
  line-height: 1;
  margin-bottom: 4px;
}

.text-risk-high { color: #f53f3f; }
.text-risk-medium { color: #faad14; }
.text-risk-low { color: #165dff; }
.text-risk-safe { color: #00b42a; }
.text-risk-unknown { color: #4e5969; }

.metric-label {
  font-size: 11px;
  color: #86909c;
}

.metric-divider {
  width: 1px;
  height: 36px;
  background: #e5e6eb;
}

/* 底部 */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid #f0f1f5;
}

.footer-hint {
  font-size: 12px;
  color: #c9cdd4;
}

.view-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 600;
  color: #165dff;
  transition: gap 0.2s;
}

.result-card:hover .view-btn {
  gap: 7px;
}
</style>
