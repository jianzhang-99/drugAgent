<template>
  <div class="result-card-wrapper" :class="[`wrapper-risk-${data?.riskLevel || 'unknown'}`]">
    <div class="result-card" @click="handleViewDetail">
      <!-- 顶部装饰条 -->
      <div class="card-top-bar" :class="`bar-risk-${derivedLevel}`"></div>

      <div class="card-inner">
        <!-- 卡片头部：场景名 + 风险等级标签 -->
        <div class="card-header">
          <div class="header-left">
            <div class="scene-icon" :class="`icon-bg-${derivedLevel}`">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
            </div>
            <div>
              <div class="scene-name">标书审查</div>
              <div class="trace-id" v-if="data?.traceId">TRACE · {{ shortTraceId }}</div>
            </div>
          </div>
          <div class="risk-badge" :class="`badge-${derivedLevel}`">
            <span class="badge-dot"></span>
            {{ riskBadgeText }}
          </div>
        </div>

        <!-- 核心指标区：评分 + 结论 -->
        <div class="core-section">
          <div class="score-block">
            <div class="score-number" :class="`score-${derivedLevel}`">{{ displayScore }}</div>
            <div class="score-label">风险评分</div>
          </div>
          <div class="conclusion-block" :class="`conclusion-${derivedLevel}`">
            <div class="conclusion-text">{{ conclusionText }}</div>
          </div>
        </div>

        <!-- 文档信息 -->
        <div class="docs-row" v-if="data?.documentNames?.length">
          <span class="docs-label">比对文件：</span>
          <span class="doc-name doc-a">{{ data.documentNames[0] || '文档A' }}</span>
          <span class="vs-sep">VS</span>
          <span class="doc-name doc-b">{{ data.documentNames[1] || '文档B' }}</span>
        </div>

        <!-- 统计指标 -->
        <div class="stats-row">
          <div class="stat-item">
            <span class="stat-value">{{ docCount }}</span>
            <span class="stat-label">份文档</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ riskItemCount }}</span>
            <span class="stat-label">条规则</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ evidenceCount }}</span>
            <span class="stat-label">处证据</span>
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

// 风险等级
const derivedLevel = computed(() => {
  const level = props.data?.riskLevel?.toLowerCase();
  if (level === 'high') return 'high';
  if (level === 'medium') return 'medium';
  if (level === 'low') return 'low';
  if (level === 'safe') return 'safe';
  return 'unknown';
});

// 风险标签
const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险', medium: '中风险', low: '低风险', safe: '安全', unknown: '未知',
  };
  return map[derivedLevel.value] || '未知';
});

const primaryRiskName = computed(() => {
  return props.data?.reportData?.executiveSummary?.primaryRiskName
    || props.data?.report?.riskItems?.[0]?.title
    || props.data?.report?.riskItems?.[0]?.summary
    || '';
});

const riskBadgeText = computed(() => {
  return primaryRiskName.value ? `${riskLabel.value} · ${primaryRiskName.value}` : riskLabel.value;
});

// 显示分数（优先用后端原始分，否则用计算分）
const displayScore = computed(() => {
  if (props.data?.score !== undefined) return props.data.score;
  if (props.data?.report?.overview?.score !== undefined) return props.data.report.overview.score;
  return 0;
});

// 一句话结论（优先使用后端返回的具体规则命中摘要，无则用通用文本兜底）
const conclusionText = computed(() => {
  // 后端 summary 包含具体规则名称，优先展示
  const backendSummary = props.data?.summary;
  if (backendSummary && backendSummary.trim()) {
    return backendSummary;
  }
  const level = derivedLevel.value;
  if (level === 'high') return `发现明显风险特征，建议立即人工复核`;
  if (level === 'safe') return `未发现明显风险嫌疑`;
  return '风险等级待确认';
});

// 缩短的 traceId
const shortTraceId = computed(() => {
  const tid = props.data?.traceId || '';
  if (tid.length > 8) return tid.substring(0, 8);
  return tid;
});

// 文档数
const docCount = computed(() => {
  return props.data?.docCount ?? props.data?.documentNames?.length ?? 2;
});

// 规则数
const riskItemCount = computed(() => {
  return props.data?.report?.riskItems?.length ?? 0;
});

// 证据数
const evidenceCount = computed(() => {
  const groups = props.data?.evidenceGroups?.length ?? 0;
  const list = props.data?.evidenceList?.length ?? 0;
  return groups > 0 ? groups : list;
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
  transition: transform 0.22s ease, box-shadow 0.22s ease;
}

.result-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 36px rgba(15, 23, 42, 0.13);
}

.result-card:active {
  transform: translateY(0);
}

/* 顶部彩色条 */
.card-top-bar {
  height: 4px;
  width: 100%;
}
.bar-risk-high   { background: linear-gradient(90deg, #f53f3f, #ff7875); }
.bar-risk-medium { background: linear-gradient(90deg, #faad14, #ffc940); }
.bar-risk-low    { background: linear-gradient(90deg, #165dff, #5b8df6); }
.bar-risk-safe   { background: linear-gradient(90deg, #00b42a, #52c41a); }
.bar-risk-unknown { background: linear-gradient(90deg, #86909c, #c9cdd4); }

.card-inner {
  padding: 18px 20px 14px;
}

/* ── 头部 ── */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.scene-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.icon-bg-high   { background: #fff1f0; color: #f53f3f; }
.icon-bg-medium { background: #fff7e8; color: #faad14; }
.icon-bg-low    { background: #f0f5ff; color: #165dff; }
.icon-bg-safe   { background: #e8ffea; color: #00b42a; }
.icon-bg-unknown { background: #f4f5f7; color: #86909c; }

.scene-name {
  font-size: 14px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.3;
}

.trace-id {
  font-size: 11px;
  color: #86909c;
  font-family: 'SF Mono', 'Fira Code', monospace;
  margin-top: 2px;
}

/* 风险徽章 */
.risk-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.badge-high   { background: #fff1f0; color: #f53f3f; border: 1px solid #ffccc7; }
.badge-high   .badge-dot { background: #f53f3f; }
.badge-medium { background: #fff7e6; color: #faad14; border: 1px solid #ffe58f; }
.badge-medium .badge-dot { background: #faad14; }
.badge-low    { background: #e8f0ff; color: #165dff; border: 1px solid #adc6ff; }
.badge-low    .badge-dot { background: #165dff; }
.badge-safe   { background: #e8ffea; color: #00b42a; border: 1px solid #b7efc5; }
.badge-safe   .badge-dot { background: #00b42a; }
.badge-unknown { background: #f4f5f7; color: #86909c; border: 1px solid #e2e4e9; }
.badge-unknown .badge-dot { background: #86909c; }

/* ── 核心指标区 ── */
.core-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}

.score-block {
  text-align: center;
  flex-shrink: 0;
}

.score-number {
  font-size: 42px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -2px;
}
.score-label {
  font-size: 11px;
  color: #86909c;
  margin-top: 4px;
}
.score-high   { color: #f53f3f; }
.score-medium { color: #faad14; }
.score-low    { color: #165dff; }
.score-safe   { color: #00b42a; }
.score-unknown { color: #86909c; }

.conclusion-block {
  flex: 1;
  padding: 10px 14px;
  border-radius: 10px;
  min-height: 60px;
  display: flex;
  align-items: center;
}
.conclusion-text {
  font-size: 13px;
  line-height: 1.5;
  font-weight: 500;
}
.conclusion-high   { background: #fff9f9; color: #c1000a; border: 1px solid #ffccc7; }
.conclusion-medium { background: #fffbf0; color: #875400; border: 1px solid #ffe58f; }
.conclusion-low    { background: #f0f5ff; color: #1d39c4; border: 1px solid #adc6ff; }
.conclusion-safe   { background: #f0fdf4; color: #15803d; border: 1px solid #b7efc5; }
.conclusion-unknown { background: #f4f5f7; color: #4e5969; border: 1px solid #e2e4e9; }

/* ── 文档信息 ── */
.docs-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.docs-label {
  font-size: 12px;
  color: #86909c;
}
.doc-name {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.doc-a { background: #eff4ff; color: #2b5fd9; }
.doc-b { background: #f0fdf4; color: #15803d; }
.vs-sep {
  font-size: 10px;
  font-weight: 700;
  color: #c2c7d0;
  background: #f2f3f5;
  padding: 2px 6px;
  border-radius: 4px;
}

/* ── 统计指标 ── */
.stats-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 16px;
  margin-bottom: 12px;
}
.stat-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #1d2129;
}
.stat-label {
  font-size: 12px;
  color: #86909c;
}
.stat-divider {
  width: 1px;
  height: 20px;
  background: #e2e4e9;
  margin: 0 20px;
}

/* ── 底部 ── */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f0f1f5;
}
.footer-hint {
  font-size: 12px;
  color: #86909c;
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
