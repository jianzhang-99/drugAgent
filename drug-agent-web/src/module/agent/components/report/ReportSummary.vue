<template>
  <div v-if="data" class="report-dashboard">
    <!-- 首层：核心KPI仪表盘 -->
    <div class="dashboard-header">
      <div class="kpi-card gauge-card" :class="`border-${levelCode}`">
        <div class="kpi-title">综合判定与评分</div>
        <div class="gauge-container">
          <v-chart class="chart" :option="gaugeOption" autoresize />
        </div>
        <div class="kpi-conclusion" :class="`text-${levelCode}`">
          {{ data.overallConclusion }}
        </div>
      </div>

      <div class="kpi-card radar-card">
        <div class="kpi-title">多维风险感知画像</div>
        <div class="radar-container">
          <v-chart class="chart" :option="radarOption" autoresize />
        </div>
      </div>

      <div class="kpi-stats-col">
        <div class="stat-card">
          <div class="stat-icon docs-icon"></div>
          <div class="stat-info">
            <div class="stat-label">对比标书总数</div>
            <div class="stat-value">{{ data.metrics?.documentCount || 0 }}<span class="stat-unit">份</span></div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon risk-icon"></div>
          <div class="stat-info">
            <div class="stat-label">命中疑似风险项目</div>
            <div class="stat-value">{{ data.metrics?.deduplicatedRules || 0 }}<span class="stat-unit">项</span></div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon evidence-icon"></div>
          <div class="stat-info">
            <div class="stat-label">提取核心证据链</div>
            <div class="stat-value">{{ data.metrics?.coreEvidenceCount || 0 }}<span class="stat-unit">组</span></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 预警提示区：全局处置建议 -->
    <div v-if="data.recommendedAction" class="global-alert" :class="`alert-${levelCode}`">
      <div class="alert-icon">⚠️</div>
      <div class="alert-content">
        <strong>行动建议：</strong>{{ data.recommendedAction }}
      </div>
    </div>

    <!-- 第二层：Top 3 高危告警卡片 (Alert Cards) -->
    <section v-if="riskOverview?.topRisks?.length" class="alert-cards-container">
      <div class="section-heading">最高危风险提取</div>
      <div class="cards-grid">
        <div
          v-for="risk in riskOverview.topRisks"
          :key="risk.rank"
          class="alert-card"
          :class="`bg-${normalizeLevel(risk.riskLevel)}`"
        >
          <div class="card-left-banner" :class="`ribbon-${normalizeLevel(risk.riskLevel)}`"></div>
          <div class="card-content">
            <div class="card-header">
              <span class="rank-badge">NO.{{ risk.rank }}</span>
              <span class="type-tag">{{ riskTypeLabel(risk.riskType) }}</span>
              <div class="level-indicator" :class="`text-${normalizeLevel(risk.riskLevel)}`">
                <span class="dot" :class="`bg-dot-${normalizeLevel(risk.riskLevel)}`"></span>
                {{ levelLabel(normalizeLevel(risk.riskLevel)) }}
              </div>
            </div>
            <h3 class="card-title">{{ risk.riskName }}</h3>
            <div class="card-snippet">
              <span class="snippet-quote">“</span>
              {{ risk.description }}
              <span class="snippet-quote">”</span>
            </div>
            <div class="card-action">
              <span class="action-icon">💡</span> {{ risk.action }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 第三层：涉及的实体文档 -->
    <section v-if="documents?.length" class="documents-section">
      <div class="section-heading">审查文档索引</div>
      <div class="docs-grid">
        <div v-for="(doc, index) in documents" :key="doc.id" class="doc-item">
          <div class="doc-avatar">{{ doc.docCode || String.fromCharCode(65 + index) }}</div>
          <div class="doc-detail">
            <div class="doc-party">{{ doc.partyName }}</div>
            <div class="doc-filename">{{ doc.fileName }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
  <div v-else class="empty-state">暂无审查数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { use } from 'echarts/core';
import { RadarChart, GaugeChart } from 'echarts/charts';
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import VChart from 'vue-echarts';
import type { ExecutiveSummary, RiskOverview, DocumentIndex } from '../../types/report.types';

use([CanvasRenderer, RadarChart, GaugeChart, TitleComponent, TooltipComponent, LegendComponent]);

interface Props {
  data?: ExecutiveSummary;
  riskOverview?: RiskOverview;
  documents?: DocumentIndex[];
}

const props = defineProps<Props>();
const levelCode = computed(() => normalizeLevel(props.data?.riskLevel));

// 仪表盘图表配置
const gaugeOption = computed(() => {
  const score = props.data?.riskScore || 0;
  const color = getLevelColor(levelCode.value);
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        center: ['50%', '75%'],
        radius: '100%',
        min: 0,
        max: 100,
        splitNumber: 10,
        axisLine: {
          lineStyle: {
            width: 14,
            color: [
              [0.3, '#52c41a'],
              [0.7, '#faad14'],
              [1, '#f5222d']
            ]
          }
        },
        pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '12%', width: 14, offsetCenter: [0, '-40%'], itemStyle: { color: 'auto' } },
        axisTick: { length: 12, lineStyle: { color: 'auto', width: 2 } },
        splitLine: { length: 16, lineStyle: { color: 'auto', width: 3 } },
        axisLabel: { color: '#464646', fontSize: 12, distance: -40, formatter: (value: number) => (value % 20 === 0 ? value + '' : '') },
        title: { offsetCenter: [0, '-15%'], fontSize: 14 },
        detail: {
          fontSize: 36,
          offsetCenter: [0, '5%'],
          valueAnimation: true,
          formatter: '{value}',
          color: color
        },
        data: [{ value: score, name: '风险指数' }]
      }
    ]
  };
});

// 雷达图配置
const radarOption = computed(() => {
  const distributions = props.riskOverview?.distributions || [];
  const dimensions = [
    { name: '报价风险', max: 3 },
    { name: '团队风险', max: 3 },
    { name: '文本雷同', max: 3 },
    { name: '模板同源', max: 3 },
    { name: '其他线索', max: 3 }
  ];
  
  const values = dimensions.map(d => {
    let key = d.name;
    if (key === '文本雷同') key = '文本相似风险';
    if (key === '模板同源') key = '模板同源风险';
    if (key === '其他线索') key = '其他辅助风险';
    const dist = distributions.find(x => x.riskType === key);
    return severity(dist?.level || 'safe');
  });

  return {
    tooltip: {
      trigger: 'item' // 开启 hover 提示
    },
    radar: {
      indicator: dimensions,
      shape: 'polygon',
      splitArea: { areaStyle: { color: ['#f8fafc', '#ecf2f8', '#e2e8f0', '#cbd5e1'] } },
      axisLine: { lineStyle: { color: '#94a3b8' } },
      splitLine: { lineStyle: { color: '#94a3b8' } },
      name: { textStyle: { color: '#334155', fontWeight: 'bold' } }
    },
    series: [
      {
        name: '风险分布',
        type: 'radar',
        data: [
          {
            value: values,
            name: '当前提取风险',
            areaStyle: { color: 'rgba(239, 68, 68, 0.2)' },
            lineStyle: { color: '#ef4444', width: 2 },
            itemStyle: { color: '#ef4444' }
          }
        ]
      }
    ]
  };
});

// 工具函数
function normalizeLevel(level?: string): string {
  if (!level) return 'safe';
  const v = level.toLowerCase();
  if (v.includes('high') || v.includes('高')) return 'high';
  if (v.includes('medium') || v.includes('中')) return 'medium';
  if (v.includes('low') || v.includes('低')) return 'low';
  return 'safe';
}

function getLevelColor(code: string) {
  if (code === 'high') return '#f5222d';
  if (code === 'medium') return '#faad14';
  if (code === 'low') return '#52c41a';
  return '#1890ff';
}

function severity(level?: string) {
  const code = normalizeLevel(level);
  if (code === 'high') return 3;
  if (code === 'medium') return 2;
  if (code === 'low') return 1;
  return 0;
}

function levelLabel(level?: string): string {
  const code = normalizeLevel(level);
  return { high: '极高风险', medium: '中度核查', low: '异常提示', safe: '未见异常' }[code] || '未见异常';
}

function riskTypeLabel(type?: string): string {
  const n = (type || '').toLowerCase();
  if (n === 'pricing') return '报价响应';
  if (n === 'team') return '人员团队';
  if (n === 'text_similarity' || n === 'plagiarism') return '文本内容';
  if (n === 'template') return '模板格式';
  return '辅助线索';
}
</script>

<style scoped>
/* 全局排版 */
.report-dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 顶部 Dashboard 网格布局 */
.dashboard-header {
  display: grid;
  grid-template-columns: 1.1fr 1.3fr 0.6fr;
  gap: 20px;
}

.kpi-card {
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  padding: 24px;
  box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.05);
  display: flex;
  flex-direction: column;
}

.kpi-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 8px;
}

/* 仪表盘 */
.gauge-card.border-high { border-top: 4px solid #f5222d; }
.gauge-card.border-medium { border-top: 4px solid #faad14; }
.gauge-card.border-safe { border-top: 4px solid #52c41a; }

.gauge-container {
  height: 180px;
  width: 100%;
}
.chart {
  width: 100%;
  height: 100%;
}

.kpi-conclusion {
  text-align: center;
  font-weight: bold;
  font-size: 18px;
  margin-top: -10px;
}
.text-high { color: #f5222d; }
.text-medium { color: #faad14; }
.text-safe, .text-low { color: #52c41a; }

/* 雷达图 */
.radar-container {
  height: 220px;
  width: 100%;
  margin-top: -20px;
}

/* 右侧关键数据指标列列排版 */
.kpi-stats-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stat-card {
  flex: 1;
  background: #f8fafc;
  border-radius: 16px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid #f1f5f9;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: #e2e8f0;
}
.docs-icon { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.risk-icon { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.evidence-icon { background: linear-gradient(135deg, #fef3c7, #fde68a); }

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 2px;
}
.stat-value {
  font-size: 24px;
  font-weight: 900;
  color: #0f172a;
}
.stat-unit {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 4px;
}

/* 预警提示区 */
.global-alert {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 12px;
  background: #fffbfa;
  border: 1px solid #ffd8bf;
}
.alert-high { background: #fff1f0; border-color: #ffa39e; }
.alert-medium { background: #fffbe6; border-color: #ffe58f; }

.alert-content {
  color: #1f2937;
  font-size: 15px;
}
.alert-content strong {
  color: #b91c1c;
}

/* 告警卡片区 */
.section-heading {
  font-size: 18px;
  font-weight: 800;
  color: #1e293b;
  margin-bottom: 16px;
  padding-left: 12px;
  border-left: 4px solid #3b82f6;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.alert-card {
  position: relative;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  border: 1px solid #e2e8f0;
  display: flex;
}

.card-left-banner {
  width: 6px;
  flex-shrink: 0;
}
.ribbon-high { background: #ef4444; }
.ribbon-medium { background: #f59e0b; }
.ribbon-safe, .ribbon-low { background: #3b82f6; }
.bg-high { background: linear-gradient(180deg, rgba(254, 226, 226, 0.3), #fff); }

.card-content {
  padding: 20px;
  flex: 1;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
}
.rank-badge {
  background: #1e293b;
  color: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  font-weight: bold;
}
.type-tag {
  color: #64748b;
  border: 1px solid #cbd5e1;
  padding: 3px 8px;
  border-radius: 6px;
}
.level-indicator {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.bg-dot-high { background: #ef4444; }
.bg-dot-medium { background: #f59e0b; }
.bg-dot-safe { background: #10b981; }

.card-title {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 12px;
  line-height: 1.4;
}

.card-snippet {
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 16px;
  position: relative;
}
.snippet-quote {
  color: #cbd5e1;
  font-weight: bold;
  font-family: serif;
}

.card-action {
  font-size: 13px;
  color: #334155;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.action-icon {
  font-size: 16px;
}

/* 文档索引网格 */
.docs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
}
.doc-avatar {
  background: #3b82f6;
  color: white;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: bold;
}
.doc-party {
  font-size: 14px;
  color: #0f172a;
  font-weight: bold;
}
.doc-filename {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}

@media (max-width: 1024px) {
  .dashboard-header, .cards-grid {
    grid-template-columns: 1fr;
  }
}
</style>
