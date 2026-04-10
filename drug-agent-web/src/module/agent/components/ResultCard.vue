<template>
  <div class="result-card-wrapper" :class="[`wrapper-risk-${data?.riskLevel || 'unknown'}`]">
    <div class="result-card" @click="handleViewDetail">
      <!-- 顶部装饰条 -->
      <div class="card-top-bar" :class="`bar-risk-${scoreBreakdown.derivedLevel}`"></div>

      <div class="card-inner">
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="header-title-group">
            <div class="scene-icon" :class="`icon-bg-${scoreBreakdown.derivedLevel}`">
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
          <div class="risk-badge" :class="`risk-${scoreBreakdown.derivedLevel}`">
            <span class="risk-dot"></span>
            {{ riskLabel }}
          </div>
        </div>

        <!-- 主体：评分仪表盘 + 维度条 -->
        <div class="score-section">
          <!-- 左侧：圆形仪表盘 -->
          <div class="gauge-wrap">
            <svg class="gauge-svg" viewBox="0 0 120 120" width="120" height="120">
              <!-- 背景轨道（半圆弧）-->
              <path
                :d="arcPath(0)"
                fill="none"
                stroke="#f0f1f5"
                stroke-width="10"
                stroke-linecap="round"
              />
              <!-- 进度弧（按评分填充）-->
              <path
                :d="arcPath(scoreBreakdown.total)"
                fill="none"
                :stroke="gaugeColor"
                stroke-width="10"
                stroke-linecap="round"
                class="gauge-progress"
              />
            </svg>
            <div class="gauge-center">
              <div class="gauge-score" :class="`score-color-${scoreBreakdown.derivedLevel}`">
                {{ scoreBreakdown.total }}
              </div>
              <div class="gauge-unit">风险分</div>
            </div>
          </div>

          <!-- 右侧：维度明细 -->
          <div class="dimensions-wrap">
            <div
              v-for="(dim, key) in scoreBreakdown.dimensions"
              :key="key"
              class="dim-row"
            >
              <div class="dim-label">{{ DIMENSION_LABELS[key as keyof typeof DIMENSION_LABELS] }}</div>
              <div class="dim-bar-wrap">
                <div class="dim-bar">
                  <div
                    class="dim-bar-fill"
                    :class="`fill-${dimLevelClass(dim)}`"
                    :style="{ width: `${dim}%` }"
                  ></div>
                </div>
                <span class="dim-val">{{ dim }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 解释文本 -->
        <div class="interpretation-block" :class="`interp-${scoreBreakdown.derivedLevel}`">
          <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="interp-icon">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>{{ scoreBreakdown.interpretation }}</span>
        </div>

        <!-- 文档列表 -->
        <div class="docs-block" v-if="data?.documentNames?.length">
          <div class="docs-label">
            <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
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

        <!-- 底部操作栏 -->
        <div class="card-footer">
          <div class="footer-stats">
            <span class="footer-stat">
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>
              {{ docCount }} 份文档
            </span>
            <span class="stat-dot">·</span>
            <span class="footer-stat">
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/></svg>
              {{ riskItemCount }} 条规则
            </span>
          </div>
          <div class="view-btn">
            <span>查看完整报告</span>
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
import { calcRiskScore, DIMENSION_LABELS } from '../utils/riskScoreCalculator';

const props = defineProps<{ data?: ResultData }>();
const store = useAgentStore();

// ── 评分计算 ──
const scoreBreakdown = computed(() => calcRiskScore(props.data));

// ── 仪表盘颜色 ──
const gaugeColor = computed(() => {
  const map: Record<string, string> = {
    high: '#f53f3f',
    medium: '#faad14',
    low: '#165dff',
    safe: '#00b42a',
  };
  return map[scoreBreakdown.value.derivedLevel] ?? '#86909c';
});

// ── 辅助计算 ──
const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险', medium: '中风险', low: '低风险', safe: '安全', unknown: '未知',
  };
  return map[scoreBreakdown.value.derivedLevel] || map[props.data?.riskLevel || 'unknown'] || '未知';
});

const riskItemCount = computed(() => {
  return (props.data as any)?.report?.riskItems?.length ?? 0;
});

const docCount = computed(() => {
  return props.data?.docCount ?? props.data?.documentNames?.length ?? 2;
});

// ── 维度条颜色 ──
function dimLevelClass(score: number): string {
  if (score >= 80) return 'high';
  if (score >= 55) return 'medium';
  if (score >= 25) return 'low';
  return 'safe';
}

// ── SVG 半圆弧路径 ──
// 半圆从 -π 到 0（底部），映射 0~100 分
function arcPath(score: number): string {
  const cx = 60, cy = 66, r = 46;
  // 弧从左（225°）到右（-45°），总弧度 270°
  const startAngle = (225 * Math.PI) / 180;
  const endAngle = startAngle - (270 * Math.PI / 180) * (score / 100);

  const x1 = cx + r * Math.cos(startAngle);
  const y1 = cy + r * Math.sin(startAngle);
  const x2 = cx + r * Math.cos(endAngle);
  const y2 = cy + r * Math.sin(endAngle);

  // largeArcFlag: 超过 180° 时为 1
  const angleDiff = (270 * score) / 100;
  const largeArc = angleDiff > 180 ? 1 : 0;

  if (score === 0) {
    // 背景轨道：始终画完整的 270° 弧
    const bgEndAngle = startAngle - (270 * Math.PI) / 180;
    const bx2 = cx + r * Math.cos(bgEndAngle);
    const by2 = cy + r * Math.sin(bgEndAngle);
    return `M ${x1.toFixed(2)} ${y1.toFixed(2)} A ${r} ${r} 0 1 0 ${bx2.toFixed(2)} ${by2.toFixed(2)}`;
  }

  return `M ${x1.toFixed(2)} ${y1.toFixed(2)} A ${r} ${r} 0 ${largeArc} 0 ${x2.toFixed(2)} ${y2.toFixed(2)}`;
}

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
  max-width: 580px;
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

.card-inner {
  padding: 18px 22px 16px;
}

/* ── 头部 ── */
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
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
  width: 38px;
  height: 38px;
  border-radius: 10px;
  margin-top: 2px;
}

.icon-bg-high   { background: #fff1f0; color: #f53f3f; }
.icon-bg-medium { background: #fff7e8; color: #faad14; }
.icon-bg-low    { background: #f0f5ff; color: #165dff; }
.icon-bg-safe   { background: #e8ffea; color: #00b42a; }

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
  flex-shrink: 0;
}

/* ── 风险徽章 ── */
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

.risk-high   { background: #fff1f0; color: #f53f3f; border: 1px solid #ffccc7; }
.risk-medium { background: #fff7e6; color: #faad14; border: 1px solid #ffe58f; }
.risk-low    { background: #e8f0ff; color: #165dff; border: 1px solid #adc6ff; }
.risk-safe   { background: #e8ffea; color: #00b42a; border: 1px solid #b7efc5; }

.risk-high   .risk-dot { background: #f53f3f; box-shadow: 0 0 0 3px rgba(245,63,63,0.2); }
.risk-medium .risk-dot { background: #faad14; box-shadow: 0 0 0 3px rgba(250,173,20,0.2); }
.risk-low    .risk-dot { background: #165dff; box-shadow: 0 0 0 3px rgba(22,93,255,0.2); }
.risk-safe   .risk-dot { background: #00b42a; box-shadow: 0 0 0 3px rgba(0,180,42,0.2); }

/* ── 评分区域 ── */
.score-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
}

/* 仪表盘 */
.gauge-wrap {
  position: relative;
  flex-shrink: 0;
  width: 120px;
  height: 120px;
}

.gauge-svg {
  display: block;
}

.gauge-progress {
  transition: stroke-dashoffset 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

.gauge-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 10px;
}

.gauge-score {
  font-size: 28px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -1px;
}

.score-color-high   { color: #f53f3f; }
.score-color-medium { color: #faad14; }
.score-color-low    { color: #165dff; }
.score-color-safe   { color: #00b42a; }

.gauge-unit {
  font-size: 11px;
  color: #86909c;
  margin-top: 3px;
}

/* 维度条 */
.dimensions-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dim-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.dim-label {
  font-size: 11px;
  color: #86909c;
}

.dim-bar-wrap {
  display: flex;
  align-items: center;
  gap: 7px;
}

.dim-bar {
  flex: 1;
  height: 5px;
  background: #f0f1f5;
  border-radius: 999px;
  overflow: hidden;
}

.dim-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.7s cubic-bezier(0.4, 0, 0.2, 1);
}

.fill-high   { background: linear-gradient(90deg, #f53f3f, #ff7875); }
.fill-medium { background: linear-gradient(90deg, #faad14, #ffc940); }
.fill-low    { background: linear-gradient(90deg, #165dff, #5b8df6); }
.fill-safe   { background: linear-gradient(90deg, #00b42a, #52c41a); }

.dim-val {
  font-size: 11px;
  font-weight: 600;
  color: #4e5969;
  width: 24px;
  text-align: right;
  flex-shrink: 0;
}

/* ── 解释文本 ── */
.interpretation-block {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.55;
  margin-bottom: 14px;
}

.interp-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.interp-high   { background: #fff9f9; color: #c1000a; border: 1px solid #ffccc7; }
.interp-medium { background: #fffbf0; color: #875400; border: 1px solid #ffe58f; }
.interp-low    { background: #f0f5ff; color: #1d39c4; border: 1px solid #adc6ff; }
.interp-safe   { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }

/* ── 文档区 ── */
.docs-block {
  margin-bottom: 14px;
}

.docs-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: #86909c;
  margin-bottom: 7px;
}

.docs-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.doc-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 500;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-a { background: #eff4ff; color: #2b5fd9; border: 1px solid #c8d9ff; }
.chip-b { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }

/* ── 底部 ── */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 13px;
  border-top: 1px solid #f0f1f5;
}

.footer-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #86909c;
}

.footer-stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.stat-dot {
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
