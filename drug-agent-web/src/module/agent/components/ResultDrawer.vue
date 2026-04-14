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
                <span class="trace-badge">TRACE: {{ result?.traceId || '—' }}</span>
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
            <!-- ====== 一、审查结论（最醒目） ====== -->
            <div class="conclusion-banner" :class="`banner-${derivedLevel}`">
              <div class="banner-left">
                <div class="banner-level" :class="`level-${derivedLevel}`">
                  <span class="level-dot"></span>
                  {{ riskLabel }}
                </div>
                <div class="banner-score">
                  <span class="score-num">{{ displayScore }}</span>
                  <span class="score-max">/100</span>
                </div>
              </div>
              <div class="banner-right">
                <div class="banner-conclusion">{{ conclusionText }}</div>
              </div>
            </div>

            <!-- ====== 二、比对文件 + 统计概览 ====== -->
            <div class="overview-row">
              <div class="overview-files" v-if="documentNames.length > 0">
                <div class="file-tag file-a">{{ documentNames[0] }}</div>
                <span class="vs-sep">VS</span>
                <div class="file-tag file-b">{{ documentNames[1] }}</div>
              </div>
              <div class="overview-stats">
                <div class="stat-item">
                  <span class="stat-value">{{ overviewData?.documentCount ?? 2 }}</span>
                  <span class="stat-label">比对文档</span>
                </div>
                <div class="stat-item">
                  <span class="stat-value">{{ overviewData?.rawHitCount ?? riskItems.length }}</span>
                  <span class="stat-label">命中规则</span>
                </div>
                <div class="stat-item">
                  <span class="stat-value">{{ evidenceGroups.length }}</span>
                  <span class="stat-label">证据片段</span>
                </div>
              </div>
            </div>

            <!-- ====== 三、重点风险（Top 3） ====== -->
            <div class="section" v-if="topRiskItems.length > 0">
              <div class="section-title">
                <span class="section-icon icon-highrisk">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
                  </svg>
                </span>
                重点风险
                <span class="section-count">{{ topRiskItems.length }} 条</span>
              </div>
              <div class="risk-list">
                <div
                  v-for="(item, idx) in topRiskItems"
                  :key="idx"
                  class="risk-item"
                  :class="`risk-${(item.riskLevel || 'unknown').toLowerCase()}`"
                >
                  <div class="risk-item-header">
                    <span class="risk-num">{{ idx + 1 }}</span>
                    <span class="risk-type">{{ translateRiskType(item.riskType) }}</span>
                    <span class="risk-level-badge" :class="`badge-${(item.riskLevel || 'unknown').toLowerCase()}`">{{ levelLabel(item.riskLevel) }}</span>
                  </div>
                  <div class="risk-title">{{ item.summary || item.title || '未命名风险项' }}</div>
                  <div class="risk-evidence" v-if="item.evidenceTitles?.length || item.reasonCodes?.length">
                    <span class="evidence-label">命中规则与特征：</span>
                    <span class="evidence-codes">{{ (item.evidenceTitles?.length ? item.evidenceTitles : item.reasonCodes ?? []).join('、') }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- ====== 四、证据链 ====== -->
            <div class="section" v-if="evidenceGroups.length > 0">
              <div class="section-title" @click="toggleEvidence" style="cursor: pointer;">
                <span class="section-icon icon-evidence">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                </span>
                查证证据提取
                <span class="section-count">{{ evidenceGroups.length }} 处证据</span>
                <span class="toggle-icon" :class="{ collapsed: evidenceCollapsed }">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </span>
              </div>
              <div class="evidence-list" v-show="!evidenceCollapsed">
                <div v-for="(ev, idx) in evidenceGroups" :key="idx" class="evidence-card">
                  <div class="ev-header">
                    <div class="ev-num">{{ idx + 1 }}</div>
                    <div class="ev-title">{{ ev.title || '特征片段说明' }}<span v-if="ev.summary" style="margin-left: 8px; color: #86909c; font-size: 12px; font-weight: normal;">{{ ev.summary }}</span></div>
                    <div class="ev-sim" v-if="ev.similarity !== undefined">
                      共性度：{{ Math.round((ev.similarity || 0) * 100) }}%
                    </div>
                  </div>
                  <div class="ev-body" v-if="ev.items && ev.items.length > 0">
                    <div class="ev-col" v-for="(item, i) in ev.items" :key="i" :class="i % 2 === 0 ? 'ev-col-a' : 'ev-col-b'">
                      <div class="col-label">
                        <span class="col-dot" :class="i % 2 === 0 ? 'dot-a' : 'dot-b'"></span>
                        {{ documentNames[i] || item.source || ('来源文档 ' + (i + 1)) }}
                      </div>
                      <div class="col-text" v-html="formatContent(item.content)"></div>
                    </div>
                  </div>
                  <!-- 兼容旧格式 -->
                  <div class="ev-body" v-else-if="ev.contentA || ev.contentB">
                    <div class="ev-col ev-col-a" v-if="ev.contentA">
                      <div class="col-label">
                        <span class="col-dot dot-a"></span>
                        {{ documentNames[0] || '文档A' }}
                      </div>
                      <div class="col-text" v-html="formatContent(ev.contentA)"></div>
                    </div>
                    <div class="ev-col ev-col-b" v-if="ev.contentB">
                      <div class="col-label">
                        <span class="col-dot dot-b"></span>
                        {{ documentNames[1] || '文档B' }}
                      </div>
                      <div class="col-text" v-html="formatContent(ev.contentB)"></div>
                    </div>
                  </div>
                  <div class="ev-body empty-evidence" v-else>
                     未提取文本详情
                  </div>
                </div>
              </div>
            </div>

            <!-- ====== 五、全部触发规则 ====== -->
            <div class="section" v-if="riskItems.length > 0">
              <div class="section-title">
                <span class="section-icon icon-rules">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                  </svg>
                </span>
                触发合规规则
                <span class="section-count">{{ riskItems.length }} 条</span>
              </div>
              <div class="rules-grid">
                <div
                  v-for="(item, idx) in riskItems"
                  :key="idx"
                  class="rule-chip"
                  :class="`rule-${(item.riskLevel || 'unknown').toLowerCase()}`"
                >
                  <span class="rule-level-badge">{{ levelLabel(item.riskLevel) }}</span>
                  <span class="rule-text">{{ item.summary || item.title || '未知规则' }}</span>
                </div>
              </div>
            </div>

            <!-- ====== 六、处置建议 ====== -->
            <div class="section" v-if="suggestedActions.length > 0">
              <div class="section-title">
                <span class="section-icon icon-actions">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
                  </svg>
                </span>
                后续处置建议
              </div>
              <div class="actions-list">
                <div v-for="(action, idx) in suggestedActions" :key="idx" class="action-item">
                  <span class="action-num">{{ idx + 1 }}</span>
                  <span class="action-text">{{ action }}</span>
                </div>
              </div>
            </div>

            <!-- ====== 七、原始报告（可折叠） ====== -->
            <div class="section collapsible" v-if="result.summary">
              <div class="section-title" @click="toggleRawReport" style="cursor: pointer;">
                <span class="section-icon icon-raw">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
                  </svg>
                </span>
                审查摘要原文
                <span class="toggle-icon" :class="{ collapsed: rawReportCollapsed }">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </span>
              </div>
              <div class="raw-content" v-show="!rawReportCollapsed">
                {{ result.summary }}
              </div>
            </div>

            <!-- ====== 八、OCR文档解析（按需） ====== -->
            <div class="section" v-if="result?.ocrResult">
              <div class="section-title" @click="toggleOcr" style="cursor: pointer;">
                <span class="section-icon icon-ocr">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/>
                  </svg>
                </span>
                文档解析结果
                <span class="section-count">{{ result.ocrResult.blocks?.length || 0 }} 个文本块</span>
                <span class="toggle-icon" :class="{ collapsed: ocrCollapsed }">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="6 9 12 15 18 9"></polyline>
                  </svg>
                </span>
              </div>
              <div class="ocr-content" v-show="!ocrCollapsed">
                <div class="ocr-text" v-if="result.ocrResult.text">{{ result.ocrResult.text }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAgentStore } from '../store/agentStore';

const store = useAgentStore();

// 展开/收起状态
const evidenceCollapsed = ref(false);
const rawReportCollapsed = ref(false);
const ocrCollapsed = ref(true); // OCR默认收起

const visible = computed({
  get: () => !!store.currentResult,
  set: (val) => {
    if (!val) store.setCurrentResult(null);
  },
});

const result = computed(() => store.currentResult);

// 风险等级
const derivedLevel = computed(() => {
  const level = result.value?.riskLevel?.toLowerCase();
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

// 显示分数
const displayScore = computed(() => {
  if (result.value?.score !== undefined) return result.value.score;
  if (result.value?.report?.overview?.score !== undefined) return result.value.report.overview.score;
  return 0;
});

// 结论文本
const conclusionText = computed(() => {
  const level = derivedLevel.value;
  if (level === 'high') return '发现明显围标特征，建议立即启动人工复核';
  if (level === 'medium') return '存在一定相似风险，建议进行人工核查';
  if (level === 'low') return '相似度较低，可进一步观察';
  if (level === 'safe') return '未发现明显围标嫌疑';
  return '风险等级待确认，请参考详情信息';
});

// 文档名称
const documentNames = computed(() => result.value?.documentNames || []);

// 概览数据
const overviewData = computed(() => result.value?.report?.overview || null);

// 风险项列表
const riskItems = computed(() => result.value?.report?.riskItems || []);

// Top 3 风险项
const topRiskItems = computed(() => riskItems.value.slice(0, 3));

// 证据组
const evidenceGroups = computed(() => result.value?.evidenceGroups || []);

// 处置建议
const suggestedActions = computed(() => {
  const actions = result.value?.suggestedActions || [];
  if (actions.length > 0) return actions;
  // 兜底建议
  if (derivedLevel.value === 'high') {
    return ['对异常相似内容启动人工复核', '核查投标主体关联关系', '必要时调取历史投标记录'];
  }
  if (derivedLevel.value === 'medium') {
    return ['对重点异常项进行人工核查', '视情况补充外部佐证材料'];
  }
  return ['保持常规关注'];
});

// 风险等级标签
function levelLabel(level?: string): string {
  if (!level) return '未知';
  const map: Record<string, string> = {
    high: '高风险', medium: '中风险', low: '低风险', info: '提示',
  };
  const normalizedLevel = level.toLowerCase();
  return map[normalizedLevel] || '高风险'; // 默认当高风险兜底让用户引起重视
}

// 翻译风险类型
function translateRiskType(type?: string): string {
  if (!type) return '审查项目';
  const map: Record<string, string> = {
    collusion: '协同作弊(围标)',
    plagiarism: '雷同与抄袭',
    qualification: '资质异常',
    pricing: '报价特征异常',
    network: '网络/设备同源',
    unknown: '综合风险',
  };
  return map[type.toLowerCase()] || type;
}

// 格式化证据内容
function formatContent(content?: string): string {
  if (!content) return '';
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>');
}

// 切换展开/收起
function toggleEvidence() { evidenceCollapsed.value = !evidenceCollapsed.value; }
function toggleRawReport() { rawReportCollapsed.value = !rawReportCollapsed.value; }
function toggleOcr() { ocrCollapsed.value = !ocrCollapsed.value; }

function handleClose() {
  store.setCurrentResult(null);
}

async function handleExportPdf() {
  // 构建专用打印 HTML，避免 flex/grid DOM 截图变形问题
  const r = result.value;
  if (!r) return;

  const levelMap: Record<string, string> = { high: '高风险', medium: '中风险', low: '低风险', safe: '安全', unknown: '未知' };
  const riskLevelText = levelMap[(r.riskLevel || 'unknown').toLowerCase()] || '未知';
  const scoreVal = r.score ?? r.report?.overview?.score ?? 0;
  const docA = documentNames.value[0] || '文档A';
  const docB = documentNames.value[1] || '文档B';

  const escHtml = (s?: string) => (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  // 重点风险 HTML
  const topRisksHtml = topRiskItems.value.map((item, i) => `
    <div style="border:1px solid #e5e6eb;border-radius:8px;padding:12px 16px;margin-bottom:10px;">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
        <span style="font-weight:700;color:#444;">${i + 1}.</span>
        <span style="font-size:12px;color:#555;">${escHtml(translateRiskType(item.riskType))}</span>
        <span style="font-size:11px;padding:1px 6px;border-radius:4px;background:#fff1f0;color:#f53f3f;">${levelLabel(item.riskLevel)}</span>
      </div>
      <div style="font-weight:600;color:#1d2129;margin-bottom:4px;">${escHtml(item.summary || item.title)}</div>
      ${(item.evidenceTitles?.length || item.reasonCodes?.length) ? `<div style="font-size:12px;color:#888;">命中规则与特征：${escHtml((item.evidenceTitles?.length ? item.evidenceTitles : item.reasonCodes || []).join('、'))}</div>` : ''}
    </div>`).join('');

  // 证据 HTML
  const evidenceHtml = evidenceGroups.value.map((ev, i) => {
    const items = ev.items && ev.items.length > 0 ? ev.items : [];
    const itemsHtml = items.length > 0
      ? items.map((item, j) => `
          <div style="flex:1;padding:10px 12px;background:#fafafa;border:1px solid #eee;border-radius:6px;">
            <div style="font-size:11px;font-weight:600;color:#555;margin-bottom:6px;">${escHtml(documentNames.value[j] || item.source || `文档${j + 1}`)}</div>
            <div style="font-size:12.5px;line-height:1.8;color:#333;">${escHtml(item.content)}</div>
          </div>`).join('<div style="width:12px;flex-shrink:0;"></div>')
      : (ev.contentA || ev.contentB ? `
          <div style="flex:1;padding:10px 12px;background:#fafafa;border:1px solid #eee;border-radius:6px;">
            <div style="font-size:11px;font-weight:600;color:#165dff;margin-bottom:6px;">${escHtml(docA)}</div>
            <div style="font-size:12.5px;line-height:1.8;color:#333;">${escHtml(ev.contentA)}</div>
          </div>
          <div style="width:12px;flex-shrink:0;"></div>
          <div style="flex:1;padding:10px 12px;background:#fafafa;border:1px solid #eee;border-radius:6px;">
            <div style="font-size:11px;font-weight:600;color:#00b42a;margin-bottom:6px;">${escHtml(docB)}</div>
            <div style="font-size:12.5px;line-height:1.8;color:#333;">${escHtml(ev.contentB)}</div>
          </div>` : '<div style="color:#aaa;font-size:12px;">未提取文本详情</div>');
    return `
      <div style="border:1px solid #e5e6eb;border-radius:8px;margin-bottom:12px;overflow:hidden;">
        <div style="background:#f7f8fa;padding:10px 14px;border-bottom:1px solid #e5e6eb;display:flex;align-items:center;gap:10px;">
          <span style="background:#eff4ff;color:#165dff;font-size:11px;font-weight:700;width:22px;height:22px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;">${i + 1}</span>
          <span style="font-weight:600;color:#1d2129;font-size:13px;">${escHtml(ev.title || '特征片段说明')}</span>
          ${ev.summary ? `<span style="font-size:12px;color:#86909c;">${escHtml(ev.summary)}</span>` : ''}
          ${ev.similarity !== undefined ? `<span style="margin-left:auto;font-size:12px;font-weight:700;color:#f53f3f;">共性度：${Math.round((ev.similarity || 0) * 100)}%</span>` : ''}
        </div>
        <div style="padding:12px;display:flex;gap:0;">${itemsHtml}</div>
      </div>`;
  }).join('');

  // 触发规则 HTML
  const allRulesHtml = riskItems.value.map(item => `
    <span style="display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:6px;border:1px solid #e5e6eb;background:#f9fafb;font-size:12px;margin:4px;">
      <span style="font-size:10px;padding:1px 5px;border-radius:3px;background:#fff1f0;color:#f53f3f;">${levelLabel(item.riskLevel)}</span>
      ${escHtml(item.summary || item.title || '未知规则')}
    </span>`).join('');

  const html = `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<title>标书围标深度比对报告</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: 'PingFang SC', 'Microsoft YaHei', 'Heiti SC', sans-serif; font-size: 14px; color: #1d2129; background: #fff; padding: 32px 40px; }
  h1 { font-size: 20px; font-weight: 700; margin-bottom: 4px; }
  .sub { font-size: 12px; color: #86909c; margin-bottom: 24px; }
  .section { margin-bottom: 20px; }
  .section-title { font-size: 14px; font-weight: 700; color: #1d2129; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 2px solid #f0f0f0; }
  .banner { background: #fff9f9; border: 1px solid #ffccc7; border-radius: 10px; padding: 16px 20px; margin-bottom: 20px; display: flex; align-items: center; gap: 20px; }
  .banner-label { font-size: 15px; font-weight: 700; color: #f53f3f; }
  .banner-score { font-size: 40px; font-weight: 900; color: #1d2129; }
  .banner-score small { font-size: 14px; color: #86909c; }
  .banner-conclusion { font-size: 14px; color: #4e5969; flex: 1; }
  .files-row { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
  .file-tag { padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 500; }
  .file-a { background: #eff4ff; color: #2b5fd9; border: 1px solid #c8d9ff; }
  .file-b { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
  .vs { font-size: 11px; color: #c2c7d0; background: #f2f3f5; padding: 2px 8px; border-radius: 4px; }
  .stats { display: flex; gap: 24px; margin-bottom: 20px; }
  .stat-item span:first-child { font-size: 20px; font-weight: 700; color: #1d2129; margin-right: 4px; }
  .stat-item span:last-child { font-size: 12px; color: #86909c; }
  @media print {
    body { padding: 16px 20px; }
    .no-break { page-break-inside: avoid; }
  }
</style>
</head>
<body>
  <h1>标书围标深度比对报告</h1>
  <div class="sub">TRACE: ${escHtml(r.traceId)} &nbsp;|&nbsp; 生成时间：${new Date().toLocaleString('zh-CN')}</div>

  <div class="banner">
    <span class="banner-label">● ${riskLevelText}</span>
    <span class="banner-score">${scoreVal}<small>/100</small></span>
    <span class="banner-conclusion">${escHtml(conclusionText.value)}</span>
  </div>

  <div class="files-row">
    <span class="file-tag file-a">${escHtml(docA)}</span>
    <span class="vs">VS</span>
    <span class="file-tag file-b">${escHtml(docB)}</span>
  </div>

  <div class="stats">
    <div class="stat-item"><span>${r.report?.overview?.documentCount ?? 2}</span><span>比对文档</span></div>
    <div class="stat-item"><span>${riskItems.value.length}</span><span>命中规则</span></div>
    <div class="stat-item"><span>${evidenceGroups.value.length}</span><span>证据片段</span></div>
  </div>

  ${topRiskItems.value.length > 0 ? `
  <div class="section no-break">
    <div class="section-title">▲ 重点风险（Top ${topRiskItems.value.length}）</div>
    ${topRisksHtml}
  </div>` : ''}

  ${evidenceGroups.value.length > 0 ? `
  <div class="section">
    <div class="section-title">🔍 查证证据提取（${evidenceGroups.value.length} 处）</div>
    ${evidenceHtml}
  </div>` : ''}

  ${riskItems.value.length > 0 ? `
  <div class="section no-break">
    <div class="section-title">✓ 触发合规规则（${riskItems.value.length} 条）</div>
    <div>${allRulesHtml}</div>
  </div>` : ''}

  ${r.summary ? `
  <div class="section no-break">
    <div class="section-title">📄 审查摘要原文</div>
    <div style="font-size:13px;line-height:1.8;color:#4e5969;background:#f7f8fa;padding:12px 14px;border-radius:8px;white-space:pre-wrap;">${escHtml(r.summary)}</div>
  </div>` : ''}
</body>
</html>`;

  const printWin = window.open('', '_blank', 'width=900,height=700');
  if (!printWin) {
    alert('请允许浏览器弹窗权限后重试');
    return;
  }
  printWin.document.write(html);
  printWin.document.close();
  // 等待字体和图片加载完毕再触发打印
  printWin.onload = () => {
    setTimeout(() => {
      printWin.focus();
      printWin.print();
    }, 600);
  };
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
  max-width: 1000px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

/* ============ 动画 ============ */
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.25s ease; }
.modal-fade-enter-active .modal-container, .modal-fade-leave-active .modal-container { transition: transform 0.28s cubic-bezier(0.34, 1.56, 0.64, 1); }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .modal-container { transform: scale(0.92) translateY(20px); }
.modal-fade-leave-to .modal-container { transform: scale(0.96) translateY(10px); }

/* ============ 头部 ============ */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e8eaf0;
  flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 14px; }
.header-icon {
  width: 40px; height: 40px;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
  color: #7c3aed;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.header-titles h3 { margin: 0 0 2px; font-size: 16px; font-weight: 700; color: #1d2129; }
.trace-badge { font-size: 11px; color: #86909c; font-family: 'SF Mono', 'Fira Code', monospace; background: #f2f3f5; padding: 2px 8px; border-radius: 4px; }
.header-right { display: flex; align-items: center; gap: 10px; }
.export-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 8px;
  background: #165dff; color: #ffffff; border: none; cursor: pointer;
  font-size: 13px; font-weight: 600; transition: background 0.2s;
}
.export-btn:hover { background: #0d4cce; }
.close-btn {
  width: 34px; height: 34px; border: none; background: #f2f3f5; color: #4e5969;
  border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background 0.2s, color 0.2s;
}
.close-btn:hover { background: #ffe4e6; color: #f53f3f; }

/* ============ 正文内容 ============ */
.modal-body { flex: 1; overflow-y: auto; padding: 20px 24px; display: flex; flex-direction: column; gap: 16px; }
.modal-body::-webkit-scrollbar { width: 5px; }
.modal-body::-webkit-scrollbar-track { background: transparent; }
.modal-body::-webkit-scrollbar-thumb { background: #d0d3db; border-radius: 3px; }

/* ============ 结论Banner ============ */
.conclusion-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 22px;
  border-radius: 14px;
}
.banner-high   { background: linear-gradient(135deg, #fff1f0, #fff7f7); border: 1px solid #ffccc7; }
.banner-medium { background: linear-gradient(135deg, #fffbf0, #fffef5); border: 1px solid #ffe58f; }
.banner-low    { background: linear-gradient(135deg, #f0f5ff, #f5f8ff); border: 1px solid #adc6ff; }
.banner-safe   { background: linear-gradient(135deg, #f0fdf4, #f5fffa); border: 1px solid #b7efc5; }
.banner-unknown { background: linear-gradient(135deg, #f4f5f7, #f8f9fa); border: 1px solid #e2e4e9; }

.banner-left { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }
.banner-level {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 14px; border-radius: 20px; font-size: 15px; font-weight: 700;
}
.level-dot { width: 8px; height: 8px; border-radius: 50%; }
.level-high   { background: #fff1f0; color: #f53f3f; } .level-high   .level-dot { background: #f53f3f; }
.level-medium { background: #fff7e6; color: #faad14; } .level-medium .level-dot { background: #faad14; }
.level-low    { background: #e8f0ff; color: #165dff; } .level-low    .level-dot { background: #165dff; }
.level-safe   { background: #e8ffea; color: #00b42a; } .level-safe   .level-dot { background: #00b42a; }
.level-unknown { background: #f4f5f7; color: #86909c; } .level-unknown .level-dot { background: #86909c; }

.banner-score { display: flex; align-items: baseline; gap: 2px; }
.score-num { font-size: 36px; font-weight: 900; color: #1d2129; line-height: 1; }
.score-max { font-size: 14px; color: #86909c; }
.banner-right { flex: 1; }
.banner-conclusion { font-size: 14px; line-height: 1.6; color: #4e5969; }

/* ============ 概览区 ============ */
.overview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e8eaf0;
}
.overview-files { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.file-tag {
  padding: 5px 12px; border-radius: 6px; font-size: 12px; font-weight: 500;
  max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.file-a { background: #eff4ff; color: #2b5fd9; border: 1px solid #c8d9ff; }
.file-b { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
.vs-sep { font-size: 11px; font-weight: 700; color: #c2c7d0; background: #f2f3f5; padding: 2px 8px; border-radius: 4px; }
.overview-stats { display: flex; align-items: center; gap: 20px; }
.stat-item { display: flex; align-items: baseline; gap: 5px; }
.stat-value { font-size: 20px; font-weight: 700; color: #1d2129; }
.stat-label { font-size: 12px; color: #86909c; }

/* ============ 通用Section ============ */
.section { background: #ffffff; border-radius: 12px; border: 1px solid #e8eaf0; padding: 16px 20px; }
.section-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 700; color: #1d2129; margin-bottom: 12px;
}
.section-icon { width: 26px; height: 26px; border-radius: 7px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.icon-highrisk { background: #fff1f0; color: #f53f3f; }
.icon-evidence { background: #f3f0ff; color: #7c3aed; }
.icon-rules { background: #fff7e6; color: #faad14; }
.icon-actions { background: #e8ffea; color: #00b42a; }
.icon-raw { background: #f0f5ff; color: #165dff; }
.icon-ocr { background: #f0faf8; color: #0ea5a5; }
.section-count { margin-left: auto; font-size: 11px; font-weight: 500; background: #f2f3f5; color: #4e5969; padding: 2px 8px; border-radius: 10px; }
.toggle-icon { margin-left: auto; transition: transform 0.2s; }
.toggle-icon.collapsed { transform: rotate(-90deg); }

/* ============ 重点风险 ============ */
.risk-list { display: flex; flex-direction: column; gap: 10px; }
.risk-item {
  padding: 12px 14px; border-radius: 10px; border: 1px solid;
}
.risk-high { background: #fff9f9; border-color: #ffccc7; }
.risk-medium { background: #fffbf0; border-color: #ffe58f; }
.risk-low { background: #f0f5ff; border-color: #adc6ff; }
.risk-info { background: #f0fdf4; border-color: #b7efc5; }
.risk-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.risk-num { width: 20px; height: 20px; border-radius: 50%; background: #ffffff; color: #4e5969; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.risk-type { font-size: 12px; color: #4e5969; }
.risk-level-badge { font-size: 10px; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.badge-high { background: #fff1f0; color: #f53f3f; }
.badge-medium { background: #fff7e6; color: #faad14; }
.badge-low { background: #e8f0ff; color: #165dff; }
.badge-info { background: #e8ffea; color: #00b42a; }
.risk-title { font-size: 13px; font-weight: 600; color: #1d2129; margin-bottom: 4px; }
.risk-evidence { font-size: 11px; color: #86909c; }
.evidence-codes { font-family: 'SF Mono', 'Fira Code', monospace; }

/* ============ 证据 ============ */
.evidence-list { display: flex; flex-direction: column; gap: 12px; }
.evidence-card { border: 1px solid #e8eaf0; border-radius: 10px; overflow: hidden; }
.ev-header { display: flex; align-items: center; gap: 10px; background: #f9fafb; padding: 10px 14px; border-bottom: 1px solid #e8eaf0; }
.ev-num { width: 22px; height: 22px; border-radius: 50%; background: #eff4ff; color: #165dff; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.ev-title { flex: 1; font-size: 13px; font-weight: 600; color: #1d2129; }
.ev-sim { font-size: 12px; font-weight: 700; color: #f53f3f; background: #fff1f0; padding: 2px 8px; border-radius: 4px; }
.ev-body { display: grid; grid-template-columns: 1fr 1fr; }
.ev-col { padding: 12px 14px; }
.ev-col-a { border-right: 1px solid #e8eaf0; }
.col-label { display: flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 600; color: #4e5969; margin-bottom: 8px; }
.col-dot { width: 7px; height: 7px; border-radius: 50%; }
.dot-a { background: #165dff; }
.dot-b { background: #00b42a; }
.col-text { font-size: 13px; line-height: 1.8; color: #333; background: #fff; padding: 10px; border-radius: 6px; border: 1px solid #f0f0f0; }
.empty-evidence { padding: 16px; color: #86909c; font-size: 13px; text-align: center; grid-column: span 2; }
:deep(mark) { background: #fef0b2; color: inherit; border-radius: 2px; padding: 0 2px; }

/* ============ 规则 ============ */
.rules-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.rule-chip { display: flex; align-items: center; gap: 7px; padding: 6px 12px; border-radius: 8px; font-size: 12px; }
.rule-high { background: #fff9f5; border: 1px solid #ffccc7; }
.rule-medium { background: #fffbf0; border: 1px solid #ffe58f; }
.rule-low { background: #f0f5ff; border: 1px solid #adc6ff; }
.rule-level-badge { font-size: 10px; padding: 1px 5px; border-radius: 3px; font-weight: 600; background: #ffffff; }
.rule-high .rule-level-badge { color: #f53f3f; }
.rule-medium .rule-level-badge { color: #faad14; }
.rule-low .rule-level-badge { color: #165dff; }
.rule-text { color: #1d2129; font-weight: 500; }

/* ============ 处置建议 ============ */
.actions-list { display: flex; flex-direction: column; gap: 8px; }
.action-item { display: flex; align-items: flex-start; gap: 10px; padding: 8px 12px; background: #f7f8fa; border-radius: 8px; }
.action-num { width: 20px; height: 20px; border-radius: 50%; background: #00b42a; color: #ffffff; font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.action-text { font-size: 13px; color: #1d2129; line-height: 1.5; }

/* ============ 原始报告 ============ */
.raw-content { font-size: 13px; line-height: 1.8; color: #4e5969; background: #f7f8fa; padding: 12px 14px; border-radius: 8px; white-space: pre-wrap; word-break: break-word; }

/* ============ OCR ============ */
.ocr-content { }
.ocr-text { font-size: 13px; line-height: 1.8; color: #333; background: #f7f8fa; padding: 12px 14px; border-radius: 8px; white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow-y: auto; }
</style>
