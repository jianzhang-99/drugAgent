<template>
  <div class="benchmark-page">
    <!-- 顶部栏 -->
    <header class="benchmark-topbar">
      <div class="topbar-left">
        <span class="topbar-title-text">模型评测</span>
      </div>
      <div class="topbar-right">
        <div class="topbar-pill">
          <span class="pill-wave">&#8767;</span>
          <span>{{ availableModels.length }} 个已接入模型</span>
        </div>
        <div class="topbar-avatar">HD</div>
      </div>
    </header>

    <!-- 主内容 -->
    <div class="benchmark-body">
      <!-- 标题与说明 -->
      <div class="benchmark-headline">
        <div class="headline-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <h1 class="headline-title">模型评测</h1>
      </div>
      <p class="benchmark-desc">
        在此输入评测 Prompt，系统将对所有已接入的模型并发发起评测，并展示各模型的响应时间、Token 消耗及评测结果排行榜。
      </p>

      <!-- ===== 模型选择评测区 ===== -->
      <div class="model-list-section">
        <div class="section-header">
          <div class="section-title">选择评测模型</div>
          <div class="select-actions">
            <button class="select-btn" @click="handleSelectAll">全选</button>
            <button class="select-btn" @click="handleDeselectAll">取消全选</button>
            <span class="select-count">已选 {{ selectedModels.length }} / {{ availableModels.length }} 个模型</span>
          </div>
        </div>
        <div class="model-cards">
          <div
            v-for="model in availableModels"
            :key="model.modelName"
            class="model-card"
            :class="{ 'model-unavailable': !model.available, 'model-selected': isModelSelected(model.modelName) }"
            @click="toggleModelSelect(model)"
          >
            <div class="model-checkbox">
              <input
                type="checkbox"
                :checked="isModelSelected(model.modelName)"
                @click.stop
                @change="toggleModelSelect(model)"
              >
            </div>
            <div class="model-card-icon">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
                <line x1="8" y1="21" x2="16" y2="21"/>
                <line x1="12" y1="17" x2="12" y2="21"/>
              </svg>
            </div>
            <div class="model-card-info">
              <div class="model-card-name">{{ model.modelName }}</div>
              <div class="model-card-provider">{{ model.provider }}</div>
            </div>
            <div class="model-card-status" :class="model.available ? 'status-ok' : 'status-fail'">
              {{ model.available ? '可用' : '不可用' }}
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 评测执行区 ===== -->
      <div class="benchmark-execute-section">
        <div class="section-header">
          <div class="section-title">评测执行</div>
        </div>
        <div class="execute-form">
          <!-- Prompt 模板选择 -->
          <div class="template-section">
            <div class="template-label">预设模板</div>
            <div class="template-buttons">
              <button
                v-for="template in promptTemplates"
                :key="template.name"
                class="template-btn"
                :class="{ 'template-btn-active': selectedTemplate === template.name }"
                @click="handleSelectTemplate(template)"
              >
                {{ template.name }}
              </button>
            </div>
          </div>
          <textarea
            v-model="benchmarkPrompt"
            class="prompt-input"
            :placeholder="promptPlaceholder"
            rows="3"
          ></textarea>
          <div class="execute-actions">
            <button
              class="start-btn"
              type="button"
              :disabled="isRunning || !benchmarkPrompt.trim() || selectedModels.length === 0"
              @click="handleStartBenchmark"
            >
              <svg v-if="!isRunning" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              <div v-else class="btn-spinner"></div>
              {{ isRunning ? '评测中...' : '开始评测' }}
            </button>
          </div>
        </div>

        <!-- 进度显示 -->
        <div v-if="isRunning" class="progress-area">
          <div class="progress-text">
            正在评测: <strong>{{ runningModelName }}</strong>
            <span class="progress-count">已完成 {{ completedCount }} / {{ selectedModels.length }}</span>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
          </div>
        </div>
      </div>

      <!-- ===== 评测结果区 ===== -->
      <div v-if="benchmarkResults.length > 0" class="result-section">
        <div class="section-header">
          <div class="section-title">评测结果</div>
          <span class="result-count">共 {{ benchmarkResults.length }} 个模型</span>
        </div>

        <!-- 响应时间排行榜 -->
        <div class="result-table">
          <div class="table-head">
            <div class="col-rank">排名</div>
            <div class="col-model">模型</div>
            <div class="col-provider">Provider</div>
            <div class="col-time">响应时间</div>
            <div class="col-tokens">Token消耗</div>
            <div class="col-status">状态</div>
            <div class="col-error">错误信息</div>
          </div>
          <div
            v-for="(result, index) in sortedResults"
            :key="result.modelName"
            class="table-row"
            :class="{ 'row-success': result.success, 'row-fail': !result.success }"
          >
            <div class="col-rank">
              <span v-if="result.success" class="rank-badge" :class="getRankClass(index)">{{ index + 1 }}</span>
              <span v-else class="rank-dash">-</span>
            </div>
            <div class="col-model">{{ result.modelName }}</div>
            <div class="col-provider">{{ result.provider }}</div>
            <div class="col-time">
              <span v-if="result.success" class="time-value">{{ result.responseTimeMs }} ms</span>
              <span v-else class="time-dash">-</span>
            </div>
            <div class="col-tokens">
              <span v-if="result.success" class="tokens-value">{{ result.tokensUsed }}</span>
              <span v-else class="tokens-dash">-</span>
            </div>
            <div class="col-status">
              <span class="status-badge" :class="result.success ? 'badge-success' : 'badge-fail'">
                {{ result.success ? '成功' : '失败' }}
              </span>
            </div>
            <div class="col-error">
              <span v-if="!result.success && result.errorMessage" class="error-msg">{{ result.errorMessage }}</span>
              <span v-else class="error-none">-</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 历史记录区 ===== -->
      <div class="history-section">
        <div class="section-header">
          <div class="section-title">历史记录</div>
        </div>

        <div v-if="historyRecords.length > 0" class="history-table">
          <div class="table-head">
            <div class="col-hist-expand"></div>
            <div class="col-hist-time">评测时间</div>
            <div class="col-hist-prompt">Prompt</div>
            <div class="col-hist-model">模型</div>
            <div class="col-hist-provider">Provider</div>
            <div class="col-hist-time2">响应时间</div>
            <div class="col-hist-tokens">Token</div>
            <div class="col-hist-status">状态</div>
          </div>
          <template v-for="record in historyRecords" :key="record.id">
            <div
              class="table-row"
              :class="{ 'row-success': record.success, 'row-fail': !record.success, 'row-expanded': expandedHistoryId === record.id }"
              @click="toggleHistoryExpand(record.id)"
            >
              <div class="col-hist-expand">
                <span class="expand-icon" :class="{ 'expand-icon-rotated': expandedHistoryId === record.id }">
                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="9 18 15 12 9 6"/>
                  </svg>
                </span>
              </div>
              <div class="col-hist-time">{{ formatDateTime(record.benchmarkTime) }}</div>
              <div class="col-hist-prompt">
                <span class="prompt-text" :title="record.prompt">{{ record.prompt }}</span>
              </div>
              <div class="col-hist-model">{{ record.modelName }}</div>
              <div class="col-hist-provider">{{ record.provider }}</div>
              <div class="col-hist-time2">
                <span v-if="record.success" class="time-value">{{ record.responseTimeMs }} ms</span>
                <span v-else class="time-dash">-</span>
              </div>
              <div class="col-hist-tokens">
                <span v-if="record.success" class="tokens-value">{{ record.tokensUsed }}</span>
                <span v-else class="tokens-dash">-</span>
              </div>
              <div class="col-hist-status">
                <span class="status-badge" :class="record.success ? 'badge-success' : 'badge-fail'">
                  {{ record.success ? '成功' : '失败' }}
                </span>
              </div>
            </div>
            <!-- 展开的详情行 -->
            <div v-if="expandedHistoryId === record.id" class="history-detail-row">
              <div class="history-detail-content">
                <div class="detail-label">模型回复：</div>
                <div class="detail-response">{{ record.response || '无响应内容' }}</div>
              </div>
            </div>
          </template>
        </div>

        <!-- 空状态 -->
        <div v-else class="history-empty">
          <div class="empty-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
          </div>
          <div class="empty-text">暂无评测历史记录</div>
        </div>

        <!-- 分页 -->
        <div v-if="historyTotal > pageSize" class="pagination">
          <button
            class="page-btn"
            :disabled="historyPage <= 1"
            @click="handlePageChange(historyPage - 1)"
          >
            上一页
          </button>
          <span class="page-info">{{ historyPage }} / {{ totalPages }}</span>
          <button
            class="page-btn"
            :disabled="historyPage >= totalPages"
            @click="handlePageChange(historyPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { MessagePlugin } from 'tdesign-vue-next';
import * as benchmarkApi from '../api/benchmarkApi';
import type { ModelBenchmarkResp, BenchmarkRecord } from '../api/benchmarkApi';

// ==================== 状态 ====================

/** 可用模型列表 */
const availableModels = ref<any[]>([]);

/** 选中的模型列表 */
const selectedModels = ref<string[]>([]);

/** 评测 Prompt */
const benchmarkPrompt = ref('你好，请介绍一下你自己');

/** 选中的模板名称 */
const selectedTemplate = ref('');

/** Prompt 占位符提示 */
const promptPlaceholder = ref('输入评测 Prompt...');

/** 预设 Prompt 模板列表 */
const promptTemplates = [
  { name: '你好/自我介绍', prompt: '你好，请介绍一下你自己' },
  { name: '药品知识问答', prompt: '请介绍一下常见的降压药物分类及其代表药物？' },
  { name: '法规咨询', prompt: '请介绍一下《药品生产质量管理规范》中关于厂房设施的要求？' },
  { name: '风险识别', prompt: '请分析以下情况是否存在风险：两家投标公司的投标文件存在多处雷同' }
];

/** 展开的历史记录 ID */
const expandedHistoryId = ref<string | null>(null);

/** 评测是否运行中 */
const isRunning = ref(false);

/** 正在评测的模型名 */
const runningModelName = ref('');

/** 已完成数量 */
const completedCount = ref(0);

/** 当前评测结果 */
const benchmarkResults = ref<ModelBenchmarkResp[]>([]);

/** 历史记录 */
const historyRecords = ref<BenchmarkRecord[]>([]);

/** 历史记录分页 */
const historyPage = ref(1);
const pageSize = ref(10);
const historyTotal = ref(0);

// ==================== 计算属性 ====================

/** 进度百分比 */
const progressPercent = computed(() => {
  if (selectedModels.value.length === 0) return 0;
  return Math.round((completedCount.value / selectedModels.value.length) * 100);
});

/** 总页数 */
const totalPages = computed(() => {
  return Math.ceil(historyTotal.value / pageSize.value);
});

/** 按响应时间排序的结果（仅成功的） */
const sortedResults = computed(() => {
  return benchmarkResults.value
    .filter((r) => r.success)
    .sort((a, b) => a.responseTimeMs - b.responseTimeMs);
});

// ==================== 方法 ====================

/**
 * 判断模型是否被选中
 */
function isModelSelected(modelName: string) {
  return selectedModels.value.includes(modelName);
}

/**
 * 切换模型选中状态
 */
function toggleModelSelect(model: any) {
  if (!model.available) return;
  const index = selectedModels.value.indexOf(model.modelName);
  if (index === -1) {
    selectedModels.value.push(model.modelName);
  } else {
    selectedModels.value.splice(index, 1);
  }
}

/**
 * 全选所有可用模型
 */
function handleSelectAll() {
  selectedModels.value = availableModels.value
    .filter(m => m.available)
    .map(m => m.modelName);
}

/**
 * 取消全选
 */
function handleDeselectAll() {
  selectedModels.value = [];
}

/**
 * 选择预设模板
 */
function handleSelectTemplate(template: { name: string; prompt: string }) {
  selectedTemplate.value = template.name;
  benchmarkPrompt.value = template.prompt;
  promptPlaceholder.value = template.prompt;
}

/**
 * 切换历史记录展开/收起
 */
function toggleHistoryExpand(recordId: string) {
  if (expandedHistoryId.value === recordId) {
    expandedHistoryId.value = null;
  } else {
    expandedHistoryId.value = recordId;
  }
}

/**
 * 加载可用模型列表
 */
async function loadModels() {
  try {
    const res = await benchmarkApi.getAvailableModels();
    if (res.data.code === 200 || res.data.code === 0) {
      availableModels.value = res.data.data || [];
      // 初始化默认选中所有可用模型
      selectedModels.value = availableModels.value
        .filter(m => m.available)
        .map(m => m.modelName);
    }
  } catch (err) {
    console.error('加载模型列表失败:', err);
  }
}

/**
 * 加载历史记录
 */
async function loadHistory() {
  try {
    const res = await benchmarkApi.getBenchmarkResults(historyPage.value, pageSize.value);
    if (res.data.code === 200 || res.data.code === 0) {
      historyRecords.value = res.data.data?.records || [];
      historyTotal.value = res.data.data?.total || 0;
    }
  } catch (err) {
    console.error('加载历史记录失败:', err);
  }
}

/**
 * 开始评测
 */
async function handleStartBenchmark() {
  if (!benchmarkPrompt.value.trim()) return;
  if (selectedModels.value.length === 0) {
    MessagePlugin.warning('请选择至少一个模型进行评测');
    return;
  }

  isRunning.value = true;
  completedCount.value = 0;
  benchmarkResults.value = [];
  runningModelName.value = '';

  try {
    // 只传递选中的模型名称列表
    const res = await benchmarkApi.runBenchmark({
      prompt: benchmarkPrompt.value.trim(),
      models: selectedModels.value
    });
    if (res.data.code === 200 || res.data.code === 0) {
      benchmarkResults.value = res.data.data || [];
      completedCount.value = selectedModels.value.length;
      // 刷新历史记录
      await loadHistory();
      MessagePlugin.success('评测完成');
    } else {
      MessagePlugin.error('评测失败: ' + res.data.message);
    }
  } catch (err) {
    console.error('评测失败:', err);
    MessagePlugin.error('评测请求失败，请稍后重试');
  } finally {
    isRunning.value = false;
    runningModelName.value = '';
  }
}

/**
 * 切换历史记录页码
 */
async function handlePageChange(page: number) {
  historyPage.value = page;
  await loadHistory();
}

/**
 * 格式化日期时间
 */
function formatDateTime(isoStr: string) {
  if (!isoStr) return '';
  try {
    const date = new Date(isoStr);
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoStr;
  }
}

/**
 * 获取排名样式类
 */
function getRankClass(index: number) {
  if (index === 0) return 'rank-gold';
  if (index === 1) return 'rank-silver';
  if (index === 2) return 'rank-bronze';
  return 'rank-default';
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await loadModels();
  await loadHistory();
});
</script>

<style scoped>
.benchmark-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100vh;
  background: #fff;
}

/* ========== 顶部栏 ========== */
.benchmark-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 74px;
  padding: 0 32px;
  border-bottom: 1px solid #e6edf5;
  background: rgba(255, 255, 255, 0.9);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topbar-title-text {
  color: #7d90aa;
  font-size: 14px;
  font-weight: 700;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.topbar-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  background: #f6f8fc;
  color: #344863;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 700;
}

.pill-wave {
  color: #4f6df5;
}

.topbar-avatar {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: linear-gradient(135deg, #506bff, #4550dd);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

/* ========== 主体 ========== */
.benchmark-body {
  flex: 1;
  padding: 40px 48px 60px;
  overflow-y: auto;
  width: 100%;
}

.benchmark-headline {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.headline-icon {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #10b981, #0ea5e9);
  color: #fff;
  flex-shrink: 0;
}

.headline-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
}

.benchmark-desc {
  margin: 0 0 32px;
  color: #64748b;
  font-size: 15px;
  line-height: 1.7;
}

/* ========== 通用区块 ========== */
.model-list-section,
.benchmark-execute-section,
.result-section,
.history-section {
  border: 1px solid #e6edf5;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 28px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid #f1f5f9;
  background: #f8fafc;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.result-count {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

/* ========== 模型卡片 ========== */
.model-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  padding: 20px 24px;
}

.model-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid #e6edf5;
  border-radius: 12px;
  background: #fff;
  transition: all 0.2s;
}

.model-card:hover {
  border-color: #93b4f5;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.08);
}

.model-card.model-unavailable {
  opacity: 0.6;
}

.model-card-icon {
  display: flex;
  width: 40px;
  height: 40px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: #3b82f6;
  flex-shrink: 0;
}

.model-card-info {
  flex: 1;
  min-width: 0;
}

.model-card-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.model-card-provider {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.model-card-status {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
}

.model-card-status.status-ok {
  background: #dcfce7;
  color: #16a34a;
}

.model-card-status.status-fail {
  background: #fef2f2;
  color: #ef4444;
}

/* ========== 模型选择功能 ========== */
.select-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.select-btn {
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.select-btn:hover {
  border-color: #93b4f5;
  color: #3b82f6;
}

.select-count {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}

.model-checkbox {
  display: flex;
  align-items: center;
}

.model-checkbox input[type="checkbox"] {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #10b981;
}

.model-card.model-selected {
  border-color: #10b981;
  background: #f0fdf4;
}

.model-card.model-selected:hover {
  border-color: #059669;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.15);
}

/* ========== 模板选择 ========== */
.template-section {
  margin-bottom: 14px;
}

.template-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
  font-weight: 500;
}

.template-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.template-btn {
  padding: 8px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.template-btn:hover {
  border-color: #93b4f5;
  color: #3b82f6;
}

.template-btn.template-btn-active {
  border-color: #10b981;
  background: #f0fdf4;
  color: #059669;
}

/* ========== 评测执行区 ========== */
.execute-form {
  padding: 20px 24px;
}

.prompt-input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #334155;
  background: #f8fafc;
  outline: none;
  resize: vertical;
  font-family: inherit;
  transition: all 0.2s;
  box-sizing: border-box;
}

.prompt-input:focus {
  border-color: #93b4f5;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
}

.prompt-input::placeholder {
  color: #b0bec5;
}

.execute-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.start-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #10b981, #0ea5e9);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.start-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.start-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ========== 进度区 ========== */
.progress-area {
  padding: 0 24px 20px;
}

.progress-text {
  font-size: 14px;
  color: #475569;
  margin-bottom: 8px;
}

.progress-count {
  margin-left: 16px;
  color: #94a3b8;
}

.progress-bar {
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #10b981, #0ea5e9);
  border-radius: 4px;
  transition: width 0.3s ease;
}

/* ========== 结果表格 ========== */
.result-table,
.history-table {
  width: 100%;
  overflow-x: auto;
}

.table-head {
  display: grid;
  padding: 12px 24px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  gap: 12px;
}

.result-table .table-head {
  grid-template-columns: 60px 1fr 100px 120px 100px 80px 1fr;
}

.history-table .table-head {
  grid-template-columns: 40px 160px 1fr 120px 100px 120px 80px 80px;
}

.table-row {
  display: grid;
  padding: 14px 24px;
  border-bottom: 1px solid #f6f8fb;
  align-items: center;
  gap: 12px;
  transition: background 0.15s;
}

.result-table .table-row {
  grid-template-columns: 60px 1fr 100px 120px 100px 80px 1fr;
}

.history-table .table-row {
  grid-template-columns: 40px 160px 1fr 120px 100px 120px 80px 80px;
  cursor: pointer;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: #fafbfd;
}

.table-row.row-fail {
  background: #fff9f9;
}

/* 排名 */
.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.rank-gold { background: linear-gradient(135deg, #f59e0b, #d97706); }
.rank-silver { background: linear-gradient(135deg, #9ca3af, #6b7280); }
.rank-bronze { background: linear-gradient(135deg, #d97706, #b45309); }
.rank-default { background: #e2e8f0; color: #64748b; }

.rank-dash {
  color: #cbd5e1;
  font-size: 16px;
}

/* 时间和 Token */
.time-value,
.tokens-value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.time-dash,
.tokens-dash {
  color: #cbd5e1;
}

.col-model {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.col-provider {
  font-size: 13px;
  color: #64748b;
}

.col-status {
  display: flex;
  align-items: center;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.badge-success {
  background: #dcfce7;
  color: #16a34a;
}

.badge-fail {
  background: #fef2f2;
  color: #ef4444;
}

.col-error {
  min-width: 0;
}

.error-msg {
  font-size: 12px;
  color: #ef4444;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-none {
  color: #cbd5e1;
  font-size: 13px;
}

/* ========== 历史记录 ========== */
.col-hist-expand {
  display: flex;
  align-items: center;
  justify-content: center;
}

.expand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  transition: transform 0.2s;
}

.expand-icon-rotated {
  transform: rotate(90deg);
}

.table-row.row-expanded {
  background: #f0fdf4;
}

.history-detail-row {
  border-bottom: 1px solid #f6f8fb;
  background: #fafbfd;
}

.history-detail-content {
  padding: 16px 24px 16px 64px;
}

.detail-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 8px;
}

.detail-response {
  font-size: 13px;
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
  background: #fff;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e6edf5;
}

.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 36px;
  margin-bottom: 12px;
  color: #cbd5e1;
}

.empty-text {
  font-size: 14px;
}

.prompt-text {
  font-size: 13px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  display: inline-block;
  vertical-align: middle;
}

/* ========== 分页 ========== */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px 24px;
  border-top: 1px solid #f1f5f9;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  border-color: #93b4f5;
  color: #3b82f6;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: #64748b;
}

/* ========== 动画 ========== */
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .benchmark-body {
    padding: 28px 20px 40px;
  }

  .model-cards {
    grid-template-columns: 1fr;
  }
}
</style>
