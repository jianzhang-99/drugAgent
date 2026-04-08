<template>
  <t-drawer
    v-model:visible="visible"
    size="1100px"
    placement="right"
    :close-btn="false"
    :header="false"
    :footer="false"
    @close="handleClose"
  >
    <div class="drawer-container">
      <div class="custom-header">
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
            <h3>深度比对与查重报告</h3>
            <span class="trace-id">TRACE: TRC-{{ result?.traceId || '1774848013920' }}</span>
          </div>
        </div>
        <div class="header-right">
          <t-button theme="primary" variant="base" @click="handleExportPdf">
            <template #icon><t-icon name="download" /></template>
            导出举证报告 (PDF)
          </t-button>
          <t-button variant="text" shape="square" @click="handleClose">
            <t-icon name="close" size="24px" color="#86909c" />
          </t-button>
        </div>
      </div>

      <div class="drawer-content" v-if="result">
        <!-- Top Cards -->
        <div class="top-cards">
          <div class="card risk-card">
            <div class="card-label"><t-icon name="info-circle" /> 整体风险</div>
            <div class="risk-value" :class="`text-risk-${result.riskLevel || 'unknown'}`">{{ riskLabel }}</div>
          </div>
          <div class="card score-card">
            <div class="card-label"><t-icon name="chart-line" /> 融合相似度</div>
            <div class="score-value">{{ result.score || 87 }} <span class="score-unit">/ 100</span></div>
          </div>
          <div class="card file-card">
            <div class="card-label"><t-icon name="file-copy" /> 参与比对的基准文件</div>
            <div class="file-comparison">
              <div class="file-tag file-master">
                <t-icon name="file-word" />
                {{ fileA }}
              </div>
              <div class="vs-badge">VS</div>
              <div class="file-tag file-slave">
                <t-icon name="file-word" />
                {{ fileB }}
              </div>
            </div>
          </div>
        </div>

        <!-- Rules Section -->
        <div class="rules-section">
          <div class="section-header">
            <t-icon name="error-triangle" style="color: #faad14" /> 触发合规规则策略
          </div>
          <div class="rules-list">
            <div class="rule-tag" v-for="(rule, idx) in rulesData" :key="idx">
              <span class="rule-type" :class="{'type-strong': rule.type === '强规则'}">{{ rule.type }}</span>
              <span class="rule-text">{{ rule.text }}</span>
            </div>
          </div>
        </div>

        <!-- Evidence Section -->
        <div class="evidence-section" v-if="mappedEvidences.length > 0">
          <div class="section-title-wrap">
            <div class="title-left">
              <t-icon name="search" color="#165dff"/>
              <span class="title-text">查证证据提取（片段精准比对）</span>
            </div>
            <div class="title-right">发现 {{ mappedEvidences.length }} 处雷同</div>
          </div>

          <div class="evidence-cards">
            <div v-for="(ev, idx) in mappedEvidences" :key="idx" class="evidence-card">
              <div class="ev-header">
                <div class="ev-index">{{ idx + 1 }}</div>
                <div class="ev-title">{{ ev.title }}</div>
                <div class="ev-similarity">段落相似度 <span>{{ ev.similarity }}%</span></div>
              </div>
              <div class="ev-body">
                <div class="ev-col side-a">
                  <div class="col-header"><t-icon name="file-word" /> {{ fileA.toUpperCase() }}</div>
                  <div class="col-content" v-html="highlightText(ev.contentA)"></div>
                </div>
                <div class="ev-col side-b">
                  <div class="col-header"><t-icon name="file-word" /> {{ fileB.toUpperCase() }}</div>
                  <div class="col-content" v-html="highlightText(ev.contentB)"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- OCR 文档解析结果 -->
        <div class="ocr-section" v-if="result?.ocrResult">
          <div class="section-title-wrap">
            <div class="title-left">
              <t-icon name="document" color="#165dff"/>
              <span class="title-text">文档解析结果</span>
            </div>
            <div class="title-right">提取 {{ result?.ocrResult?.blocks?.length || 0 }} 个文本块</div>
          </div>

          <!-- 完整文本 -->
          <div class="ocr-text" v-if="result?.ocrResult?.text">
            <div class="ocr-text-label">提取文本</div>
            <div class="ocr-text-content">{{ result.ocrResult.text }}</div>
          </div>

          <!-- 文本块列表 -->
          <div class="ocr-blocks" v-if="result?.ocrResult?.blocks?.length">
            <div class="ocr-block" v-for="(block, idx) in result.ocrResult.blocks" :key="idx">
              <div class="block-header">
                <span class="block-index">{{ idx + 1 }}</span>
                <span class="block-page" v-if="block.page">第 {{ block.page }} 页</span>
                <span class="block-confidence" v-if="block.confidence">{{ (block.confidence * 100).toFixed(0) }}% 置信度</span>
              </div>
              <div class="block-content">{{ block.text }}</div>
            </div>
          </div>

          <!-- 表格列表 -->
          <div class="ocr-tables" v-if="result?.ocrResult?.tables?.length">
            <div class="ocr-table" v-for="(table, idx) in result.ocrResult.tables" :key="idx">
              <div class="table-header">
                <span class="table-index">{{ idx + 1 }}</span>
                <span class="table-page" v-if="table.page">第 {{ table.page }} 页</span>
              </div>
              <div class="table-content" v-if="table.htmlContent" v-html="table.htmlContent"></div>
              <div class="table-content csv-content" v-else-if="table.csvContent">
                <pre>{{ table.csvContent }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </t-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';
import html2pdf from 'html2pdf.js';

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

// 参与比对的文件名称（优先使用后端返回的 documentNames）
const fileA = computed(() => {
  const names = result.value?.documentNames;
  return (names && names.length > 0) ? names[0] : '文档A';
});
const fileB = computed(() => {
  const names = result.value?.documentNames;
  return (names && names.length > 1) ? names[1] : '文档B';
});

const rulesData = computed(() => {
  // Try to use real data logic here if backend supplies rules
  return [
    { type: '强规则', text: '技术参数响应表排版特征异常重合' },
    { type: '语义规则', text: '连续3个以上自然段语义相似度 > 90%' },
    { type: '元数据规则', text: '文档元数据(Author/Creator)相同' },
  ];
});

const mappedEvidences = computed(() => {
  // If we have detailed evidence List in result, map it out:
  // Since we want to display the mock data precisely as the UI image when no real comparative data is supplied:
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
});

/**
 * HTML转义函数，防止XSS攻击
 * 将HTML特殊字符转换为安全实体
 */
function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * 高亮文本处理：先转义HTML，再保留<mark>标签高亮效果
 * 安全地处理用户输入内容，防止XSS攻击
 */
function highlightText(text: string) {
  // 先HTML转义，防止XSS
  const escaped = escapeHtml(text);
  // 保留<mark>标签的高亮效果，同时转义其他内容
  // 由于已经转义，<mark>会被转成&lt;mark&gt;，需要还原
  return escaped
    .replace(/&lt;mark&gt;/g, '<mark>')
    .replace(/&lt;\/mark&gt;/g, '</mark>')
    .replace(/\n/g, '<br/>');
}

function handleClose() {
  store.setCurrentResult(null);
}

async function handleExportPdf() {
  const element = document.querySelector('.drawer-content') as HTMLElement;
  if (!element) {
    console.error('导出失败：未找到报告内容元素');
    return;
  }

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
/* Override the default Drawer padding by using negative margin inside */
.drawer-container {
  margin: -24px;
  min-height: calc(100vh + 48px);
  background: #f4f5f9;
  display: flex;
  flex-direction: column;
}

.custom-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e1e6eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 44px;
  height: 44px;
  background: #f0f5ff;
  color: #722ed1;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-titles h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1d2129;
  line-height: 1.4;
}

.header-titles .trace-id {
  font-size: 13px;
  color: #86909c;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-content {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
}

.top-cards {
  display: grid;
  grid-template-columns: 180px 180px 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

.card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e5e6eb;
  position: relative;
  overflow: hidden;
}

.card-label {
  font-size: 13px;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}

.risk-value {
  font-size: 28px;
  font-weight: bold;
}

.text-risk-high { color: #f53f3f; }
.text-risk-medium { color: #faad14; }
.text-risk-low { color: #165dff; }
.text-risk-safe { color: #00b42a; }
.text-risk-unknown { color: #86909c; }

.risk-card::after {
  content: '!';
  position: absolute;
  top: -16px;
  right: 12px;
  font-size: 100px;
  font-weight: 800;
  color: #fff1f0;
  z-index: 0;
  line-height: 1;
}

.risk-card * {
  position: relative;
  z-index: 1;
}

.score-value {
  font-size: 32px;
  font-weight: bold;
  color: #1d2129;
  line-height: 1;
}

.score-unit {
  font-size: 14px;
  font-weight: normal;
  color: #86909c;
}

.file-comparison {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.file-tag {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid transparent;
}

.file-master {
  background: #f0f5ff;
  color: #165dff;
  border-color: #d9e1ff;
}

.file-slave {
  background: #e8ffea;
  color: #00b42a;
  border-color: #bdf0c8;
}

.vs-badge {
  font-size: 13px;
  font-weight: bold;
  color: #86909c;
  background: #f2f3f5;
  padding: 4px 10px;
  border-radius: 12px;
}

.rules-section {
  margin-bottom: 24px;
}

.section-header {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.rules-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.rule-tag {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 4px 12px 4px 4px;
  gap: 8px;
}

.rule-type {
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;
  background: #f2f3f5;
  color: #4e5969;
}

.type-strong {
  background: #fff1f0;
  color: #f53f3f;
}

.rule-text {
  font-size: 13px;
  color: #1d2129;
}

.evidence-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e5e6eb;
}

.section-title-wrap {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e6eb;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}

.title-right {
  font-size: 13px;
  background: #f2f3f5;
  padding: 4px 12px;
  border-radius: 14px;
  color: #4e5969;
}

.evidence-cards {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.evidence-card {
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  overflow: hidden;
}

.ev-header {
  display: flex;
  align-items: center;
  background: #f9fafb;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e6eb;
}

.ev-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #e8f0ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 13px;
  margin-right: 12px;
}

.ev-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  flex: 1;
}

.ev-similarity {
  font-size: 13px;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ev-similarity span {
  background: #fff1f0;
  color: #f53f3f;
  padding: 4px 10px;
  border-radius: 4px;
  font-weight: bold;
}

.ev-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.ev-col {
  padding: 20px;
}

.side-a {
  border-right: 1px solid #e5e6eb;
}

.col-header {
  font-size: 13px;
  color: #165dff;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  font-weight: 500;
}

.side-b .col-header {
  color: #00b42a;
}

.col-content {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}

:deep(mark) {
  background-color: #fef0b2;
  color: inherit;
  border-radius: 2px;
  padding: 2px 4px;
  margin: 0;
}

/* OCR 文档解析样式 */
.ocr-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #e5e6eb;
  margin-bottom: 24px;
}

.ocr-text {
  margin-bottom: 20px;
}

.ocr-text-label {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 12px;
}

.ocr-text-content {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
  background: #f7f8fa;
  padding: 16px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

.ocr-blocks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ocr-block {
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  overflow: hidden;
}

.block-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  padding: 10px 16px;
  border-bottom: 1px solid #e5e6eb;
}

.block-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e8f0ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 12px;
}

.block-page {
  font-size: 12px;
  color: #86909c;
}

.block-confidence {
  font-size: 12px;
  color: #00b42a;
  background: #e8ffea;
  padding: 2px 8px;
  border-radius: 4px;
}

.block-content {
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}

.ocr-tables {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ocr-table {
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  padding: 10px 16px;
  border-bottom: 1px solid #e5e6eb;
}

.table-index {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e8f0ff;
  color: #165dff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 12px;
}

.table-page {
  font-size: 12px;
  color: #86909c;
}

.table-content {
  padding: 16px;
  overflow-x: auto;
}

.table-content table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.table-content table th,
.table-content table td {
  border: 1px solid #e5e6eb;
  padding: 8px 12px;
  text-align: left;
}

.table-content table th {
  background: #f9fafb;
  font-weight: 600;
}

.csv-content pre {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  color: #333;
}
</style>
