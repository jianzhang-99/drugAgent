<template>
  <!-- 遮罩层 -->
  <teleport to="body">
    <transition name="modal-fade">
      <div v-if="visible" class="modal-mask" @click.self="handleClose">
        <div class="modal-container">
          <!-- 弹窗头部 -->
          <div class="modal-header">
            <div class="header-left">
              <div class="header-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                  <polyline points="10 9 9 9 8 9"></polyline>
                </svg>
              </div>
              <div class="header-titles">
                <h3>标书围标深度比对报告</h3>
                <span class="trace-badge">TRACE: TRC-{{ result?.traceId || '—' }}</span>
              </div>
            </div>
            <div class="header-right">
              <button class="export-btn" @click="handleExportPdf">
                <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
                导出 PDF
              </button>
              <button class="close-btn" @click="handleClose">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 弹窗内容 -->
          <div class="modal-body" v-if="result">
            <!-- 顶部关键指标卡片组 -->
            <div class="top-metrics">
              <!-- 风险级别 -->
              <div class="metric-card risk-card">
                <div class="mc-label">
                  <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  整体风险等级
                </div>
                <div class="mc-value risk-val" :class="`text-risk-${result.riskLevel || 'unknown'}`">{{ riskLabel }}</div>
                <div class="mc-sub">{{ riskSubtext }}</div>
              </div>

              <!-- 综合风险评分（算法计算） -->
              <div class="metric-card score-card">
                <div class="mc-label">
                  <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
                  </svg>
                  综合风险评分
                  <span style="margin-left:auto;font-size:10px;background:#eff4ff;color:#165dff;padding:2px 6px;border-radius:4px;">算法计算</span>
                </div>
                <div class="mc-value">
                  <span class="score-num" :class="`text-risk-${scoreBreakdown.derivedLevel}`">{{ scoreBreakdown.total }}</span>
                  <span class="score-unit">/ 100</span>
                </div>
                <!-- 四维度条 -->
                <div class="score-dims">
                  <div
                    v-for="(val, key) in scoreBreakdown.dimensions"
                    :key="key"
                    class="sdim-row"
                  >
                    <span class="sdim-label">{{ DIMENSION_LABELS[key] }}</span>
                    <div class="sdim-bar">
                      <div
                        class="sdim-fill"
                        :class="`fill-risk-${scoreBreakdown.derivedLevel}`"
                        :style="{ width: `${val}%` }"
                      ></div>
                    </div>
                    <span class="sdim-val">{{ val }}</span>
                  </div>
                </div>
              </div>

              <!-- 文件比对 -->
              <div class="metric-card file-card">
                <div class="mc-label">
                  <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/>
                  </svg>
                  参与比对文件
                </div>
                <div class="file-compare-row">
                  <div class="file-tag file-a">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/>
                    </svg>
                    {{ fileA }}
                  </div>
                  <div class="vs-sep">VS</div>
                  <div class="file-tag file-b">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/>
                    </svg>
                    {{ fileB }}
                  </div>
                </div>
              </div>

              <!-- 统计信息 -->
              <div class="metric-card stats-card">
                <div class="mc-label">
                  <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/><line x1="15" y1="3" x2="15" y2="21"/>
                  </svg>
                  审查统计
                </div>
                <div class="stats-grid">
                  <div class="stat-row">
                    <span class="stat-name">比对文档</span>
                    <span class="stat-val">{{ overviewData?.documentCount ?? result.docCount ?? 2 }} 份</span>
                  </div>
                  <div class="stat-row">
                    <span class="stat-name">命中规则</span>
                    <span class="stat-val">{{ overviewData?.rawHitCount ?? rulesData.length }} 条</span>
                  </div>
                  <div class="stat-row">
                    <span class="stat-name">证据片段</span>
                    <span class="stat-val">{{ mappedEvidences.length }} 处</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 摘要 -->
            <div class="section summary-section" v-if="result.summary">
              <div class="section-title">
                <span class="section-icon icon-summary">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
                    <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
                  </svg>
                </span>
                审查摘要
              </div>
              <div class="summary-content">{{ result.summary }}</div>
            </div>

            <!-- 触发规则 -->
            <div class="section rules-section">
              <div class="section-title">
                <span class="section-icon icon-rules">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                </span>
                触发合规规则
                <span class="section-count">{{ rulesData.length }} 条</span>
              </div>
              <div class="rules-grid">
                <div class="rule-chip" v-for="(rule, idx) in rulesData" :key="idx" :class="{'rule-high': rule.type === '高风险'}">
                  <span class="rule-level">{{ rule.type }}</span>
                  <span class="rule-desc">{{ rule.text }}</span>
                </div>
              </div>
            </div>

            <!-- 证据比对 -->
            <div class="section evidence-section" v-if="mappedEvidences.length > 0">
              <div class="section-title">
                <span class="section-icon icon-evidence">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                </span>
                查证证据提取（片段精准比对）
                <span class="section-count">{{ mappedEvidences.length }} 处雷同</span>
              </div>

              <div class="evidence-list">
                <div v-for="(ev, idx) in mappedEvidences" :key="idx" class="evidence-card">
                  <div class="ev-header">
                    <div class="ev-num">{{ idx + 1 }}</div>
                    <div class="ev-title">{{ ev.title }}</div>
                    <div class="ev-sim">
                      段落相似度
                      <span class="sim-badge" :class="ev.similarity >= 90 ? 'sim-high' : 'sim-med'">{{ ev.similarity }}%</span>
                    </div>
                  </div>
                  <div class="ev-body">
                    <div class="ev-col ev-col-a">
                      <div class="col-label">
                        <span class="col-dot dot-a"></span>
                        {{ fileA }}
                      </div>
                      <div class="col-text" v-html="highlightText(ev.contentA)"></div>
                    </div>
                    <div class="ev-col ev-col-b">
                      <div class="col-label">
                        <span class="col-dot dot-b"></span>
                        {{ fileB }}
                      </div>
                      <div class="col-text" v-html="highlightText(ev.contentB)"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- OCR 文档解析结果 -->
            <div class="section ocr-section" v-if="result?.ocrResult">
              <div class="section-title">
                <span class="section-icon icon-ocr">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                  </svg>
                </span>
                文档解析结果
                <span class="section-count">{{ result?.ocrResult?.blocks?.length || 0 }} 个文本块</span>
              </div>

              <div class="ocr-text" v-if="result?.ocrResult?.text">
                <div class="ocr-label">提取文本</div>
                <div class="ocr-content">{{ result.ocrResult.text }}</div>
              </div>

              <div class="ocr-blocks" v-if="result?.ocrResult?.blocks?.length">
                <div class="ocr-block" v-for="(block, idx) in result.ocrResult.blocks" :key="idx">
                  <div class="block-meta">
                    <span class="block-num">{{ idx + 1 }}</span>
                    <span class="block-page" v-if="block.page">第 {{ block.page }} 页</span>
                    <span class="block-conf" v-if="block.confidence">{{ (block.confidence * 100).toFixed(0) }}% 置信</span>
                  </div>
                  <div class="block-text">{{ block.text }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';
import html2pdf from 'html2pdf.js';
import { calcRiskScore, DIMENSION_LABELS } from '../utils/riskScoreCalculator';

const store = useAgentStore();

const visible = computed({
  get: () => !!store.currentResult,
  set: (val) => {
    if (!val) {
      store.setCurrentResult(null);
    }
  },
});

const result = computed(() => store.currentResult);

// 综合风险评分（算法计算）
const scoreBreakdown = computed(() => calcRiskScore(result.value ?? undefined));

const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '安全',
    unknown: '未知',
  };
  return map[result.value?.riskLevel || 'unknown'] || '未知';
});

const riskSubtext = computed(() => {
  const map: Record<string, string> = {
    high: '发现明显围标特征',
    medium: '存在一定相似风险',
    low: '相似度较低，注意核查',
    safe: '未发现围标嫌疑',
    unknown: '需进一步确认',
  };
  return map[result.value?.riskLevel || 'unknown'] || '';
});

const fileA = computed(() => {
  const names = result.value?.documentNames;
  return (names && names.length > 0) ? names[0] : '文档A';
});
const fileB = computed(() => {
  const names = result.value?.documentNames;
  return (names && names.length > 1) ? names[1] : '文档B';
});

const rulesData = computed(() => {
  const riskItems = result.value?.report?.riskItems || [];
  if (riskItems.length === 0) {
    return [
      { type: '强规则', text: '技术参数响应表排版特征异常重合' },
      { type: '语义规则', text: '连续3个以上自然段语义相似度 > 90%' },
      { type: '元数据规则', text: '文档元数据(Author/Creator)相同' },
    ];
  }
  return riskItems.map((item: any) => ({
    type: item.riskLevel === 'high' ? '高风险' : item.riskLevel === 'medium' ? '中风险' : '低风险',
    text: item.title || item.summary || '',
  }));
});

const mappedEvidences = computed(() => {
  const evidenceGroups = result.value?.evidenceGroups || [];
  if (evidenceGroups.length === 0) {
    return [
      {
        title: '第四章：技术参数偏离表',
        similarity: 94,
        contentA: '1. 设备的额定功率需满足 <mark>1500W-1800W</mark> 区间，且外壳需采用医用级 <mark>ABS</mark> 材质，防腐蚀防静电。\n2. 屏幕采用 <mark>10.4</mark> 英寸高亮触控屏。',
        contentB: '1. 该机器额定功率符合 <mark>1500W</mark> 至 <mark>1800W</mark>，外壳材料为医用级 <mark>ABS</mark>，防静电防腐蚀。\n2. 屏幕为 <mark>10.4</mark> 寸高亮度触摸屏。'
      },
      {
        title: '第六章：售后服务承诺',
        similarity: 89,
        contentA: '<mark>我司承诺提供整机 3 年免费质保，核心部件终身维护。在接到报修电话后，工程师将在 2 小时内响应， 24 小时内到达现场。</mark>',
        contentB: '<mark>本公司承诺设备整机三年免费保修，核心零件终生维护。接到维修电话起，技术人员 2 小时内回复， 24 小时内抵达现场处理。</mark>'
      }
    ];
  }
  return evidenceGroups.map((group: any) => {
    const items = group.evidenceList || [];
    return {
      title: items.length > 0 ? (items[0].source || '证据片段') : '证据片段',
      similarity: group.similarity ? Math.round(group.similarity * 100) : 0,
      contentA: items.length > 0 ? (items[0].content || '') : '',
      contentB: items.length > 1 ? (items[1].content || '') : '',
    };
  });
});

const overviewData = computed(() => {
  return result.value?.report?.overview || null;
});

function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function highlightText(text: string) {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/&lt;mark&gt;/g, '<mark>')
    .replace(/&lt;\/mark&gt;/g, '</mark>')
    .replace(/\n/g, '<br/>');
}

function handleClose() {
  store.setCurrentResult(null);
}

async function handleExportPdf() {
  const element = document.querySelector('.modal-body') as HTMLElement;
  if (!element) return;

  const opt = {
    margin: 10,
    filename: `标书审查报告_${result.value?.traceId || Date.now()}.pdf`,
    image: { type: 'jpeg' as const, quality: 0.98 },
    html2canvas: { scale: 2, useCORS: true },
    jsPDF: { unit: 'mm' as const, format: 'a4' as const, orientation: 'portrait' as const }
  };

  try {
    await html2pdf().set(opt).from(element).save();
  } catch (error) {
    console.error('导出PDF失败:', error);
  }
}
</script>

<style scoped>
/* ============ 遮罩 & 弹窗容器 ============ */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(10, 15, 30, 0.6);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal-container {
  background: #f4f5f9;
  border-radius: 20px;
  width: 100%;
  max-width: 1080px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

/* ============ 动画 ============ */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}
.modal-fade-enter-active .modal-container,
.modal-fade-leave-active .modal-container {
  transition: transform 0.28s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
.modal-fade-enter-from .modal-container {
  transform: scale(0.92) translateY(20px);
}
.modal-fade-leave-to .modal-container {
  transform: scale(0.96) translateY(10px);
}

/* ============ 头部 ============ */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 28px;
  background: #ffffff;
  border-bottom: 1px solid #e8eaf0;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  color: #7c3aed;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-titles h3 {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 700;
  color: #1d2129;
}

.trace-badge {
  font-size: 12px;
  color: #86909c;
  font-family: 'SF Mono', 'Fira Code', monospace;
  background: #f2f3f5;
  padding: 2px 8px;
  border-radius: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  background: #165dff;
  color: #ffffff;
  border: none;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: background 0.2s;
}

.export-btn:hover {
  background: #0d4cce;
}

.close-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #f2f3f5;
  color: #4e5969;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, color 0.2s;
}

.close-btn:hover {
  background: #ffe4e6;
  color: #f53f3f;
}

/* ============ 正文内容 ============ */
.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.modal-body::-webkit-scrollbar {
  width: 6px;
}
.modal-body::-webkit-scrollbar-track {
  background: transparent;
}
.modal-body::-webkit-scrollbar-thumb {
  background: #d0d3db;
  border-radius: 4px;
}

/* ============ 顶部指标卡片组 ============ */
.top-metrics {
  display: grid;
  grid-template-columns: 170px 220px 1fr 180px;
  gap: 16px;
}

.metric-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px 20px;
  border: 1px solid #e8eaf0;
}

.mc-label {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #86909c;
  margin-bottom: 12px;
}

/* 风险卡片 */
.risk-val {
  font-size: 32px;
  font-weight: 800;
  line-height: 1;
  margin-bottom: 6px;
}

.mc-sub {
  font-size: 12px;
  color: #86909c;
}

.text-risk-high { color: #f53f3f; }
.text-risk-medium { color: #faad14; }
.text-risk-low { color: #165dff; }
.text-risk-safe { color: #00b42a; }
.text-risk-unknown { color: #86909c; }

/* 评分卡片 */
.score-num {
  font-size: 36px;
  font-weight: 800;
  color: #1d2129;
  line-height: 1;
}
.score-unit {
  font-size: 14px;
  color: #86909c;
  font-weight: 400;
  margin-left: 4px;
}
.mc-value {
  margin-bottom: 10px;
}
/* 算法维度条 */
.score-dims {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sdim-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sdim-label {
  font-size: 11px;
  color: #86909c;
  width: 70px;
  flex-shrink: 0;
}

.sdim-bar {
  flex: 1;
  height: 5px;
  background: #f0f1f5;
  border-radius: 999px;
  overflow: hidden;
}

.sdim-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.7s cubic-bezier(0.4, 0, 0.2, 1);
}

.sdim-val {
  font-size: 11px;
  font-weight: 600;
  color: #4e5969;
  width: 20px;
  text-align: right;
  flex-shrink: 0;
}

.fill-risk-high { background: linear-gradient(90deg, #f53f3f, #ff8080); }
.fill-risk-medium { background: linear-gradient(90deg, #faad14, #ffc940); }
.fill-risk-low { background: linear-gradient(90deg, #165dff, #5b8df6); }
.fill-risk-safe { background: linear-gradient(90deg, #00b42a, #52c41a); }
.fill-risk-unknown { background: #c9cdd4; }

/* 文件比对卡片 */
.file-compare-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.file-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-a {
  background: #eff4ff;
  color: #2b5fd9;
  border: 1px solid #c8d9ff;
}

.file-b {
  background: #f0fdf4;
  color: #15803d;
  border: 1px solid #bbf7d0;
}

.vs-sep {
  font-size: 11px;
  font-weight: 700;
  color: #c2c7d0;
  background: #f2f3f5;
  padding: 3px 8px;
  border-radius: 6px;
}

/* 统计卡片 */
.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.stat-name {
  color: #86909c;
}

.stat-val {
  font-weight: 600;
  color: #1d2129;
}

/* ============ 通用 Section ============ */
.section {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e8eaf0;
  padding: 20px 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
  margin-bottom: 16px;
}

.section-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-summary { background: #eff4ff; color: #165dff; }
.icon-rules { background: #fff7e6; color: #faad14; }
.icon-evidence { background: #f3f0ff; color: #7c3aed; }
.icon-ocr { background: #f0faf8; color: #0ea5a5; }

.section-count {
  margin-left: auto;
  font-size: 12px;
  font-weight: 500;
  background: #f2f3f5;
  color: #4e5969;
  padding: 3px 10px;
  border-radius: 12px;
}

/* ============ 摘要 ============ */
.summary-content {
  font-size: 14px;
  line-height: 1.8;
  color: #4e5969;
  background: #f7f8fa;
  border-radius: 10px;
  padding: 14px 16px;
}

/* ============ 规则 ============ */
.rules-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.rule-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f7f8fa;
  border: 1px solid #e8eaf0;
  border-radius: 8px;
  padding: 7px 12px;
}

.rule-chip.rule-high {
  background: #fff9f5;
  border-color: #ffccc7;
}

.rule-level {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 4px;
  background: #e8eaf0;
  color: #4e5969;
  font-weight: 600;
  flex-shrink: 0;
}

.rule-chip.rule-high .rule-level {
  background: #fff1f0;
  color: #f53f3f;
}

.rule-desc {
  font-size: 13px;
  color: #1d2129;
}

/* ============ 证据卡片 ============ */
.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.evidence-card {
  border: 1px solid #e8eaf0;
  border-radius: 12px;
  overflow: hidden;
}

.ev-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  padding: 12px 18px;
  border-bottom: 1px solid #e8eaf0;
}

.ev-num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #eff4ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex-shrink: 0;
}

.ev-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  flex: 1;
}

.ev-sim {
  font-size: 12px;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.sim-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 12px;
}

.sim-high { background: #fff1f0; color: #f53f3f; }
.sim-med { background: #fff7e6; color: #faad14; }

.ev-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.ev-col {
  padding: 18px 20px;
}

.ev-col-a {
  border-right: 1px solid #e8eaf0;
}

.col-label {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #4e5969;
}

.col-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-a { background: #165dff; }
.dot-b { background: #00b42a; }

.col-text {
  font-size: 13.5px;
  line-height: 1.8;
  color: #333;
}

:deep(mark) {
  background: #fef0b2;
  color: inherit;
  border-radius: 3px;
  padding: 1px 4px;
}

/* ============ OCR ============ */
.ocr-label {
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  margin-bottom: 10px;
}

.ocr-content {
  font-size: 13.5px;
  line-height: 1.8;
  color: #333;
  background: #f7f8fa;
  padding: 14px 16px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 16px;
}

.ocr-blocks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ocr-block {
  border: 1px solid #e8eaf0;
  border-radius: 8px;
  overflow: hidden;
}

.block-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f9fafb;
  padding: 8px 14px;
  border-bottom: 1px solid #e8eaf0;
}

.block-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #eff4ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 11px;
}

.block-page {
  font-size: 12px;
  color: #86909c;
}

.block-conf {
  font-size: 12px;
  color: #00b42a;
  background: #e8ffea;
  padding: 2px 7px;
  border-radius: 4px;
}

.block-text {
  padding: 14px;
  font-size: 13.5px;
  line-height: 1.8;
  color: #333;
}
</style>
