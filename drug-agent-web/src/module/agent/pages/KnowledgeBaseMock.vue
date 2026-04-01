<template>
  <div class="kb-page">
    <!-- 顶部栏 -->
    <header class="kb-topbar">
      <div class="topbar-left">
        <span class="topbar-title-text">合规知识大脑</span>
      </div>
      <div class="topbar-right">
        <div class="topbar-pill">
          <span class="pill-wave">∿</span>
          <span>0 活跃任务</span>
        </div>
        <div class="topbar-avatar">HD</div>
      </div>
    </header>

    <!-- 主内容 -->
    <div class="kb-body">
      <!-- 标题与说明 -->
      <div class="kb-headline">
        <div class="headline-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
        </div>
        <h1 class="headline-title">合规知识大脑</h1>
      </div>
      <p class="kb-desc">
        在这里上传法规文件、管理制度或标准模板。横渡智能体会自动阅读并记忆这些文件。在后续的标书审查与合同预审中，它将严格按照此处的标准进行比对。
      </p>

      <!-- 上传区域 -->
      <div
        class="upload-zone"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave.prevent="isDragOver = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInputRef"
          type="file"
          multiple
          accept=".pdf,.doc,.docx,.xls,.xlsx,.md,.txt"
          style="display: none"
          @change="handleFileChange"
        />
        <div class="upload-icon-wrapper">
          <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
        </div>
        <div class="upload-text-main">点击此处，或拖拽文件至此区域</div>
        <div class="upload-text-sub">支持上传 PDF, Word, Excel, Markdown 或 Txt 格式文件。文件最大不超过 50MB。</div>
        <div class="upload-tags">
          <span class="upload-tag">国家法规</span>
          <span class="upload-tag">采购制度</span>
          <span class="upload-tag">范本模板</span>
        </div>
      </div>

      <!-- 文件列表 -->
      <div class="file-list-section">
        <div class="file-list-header">
          <div class="file-list-title">
            已沉淀的合规依据 <span class="file-count">({{ knowledgeFiles.length }})</span>
          </div>
          <div class="file-list-controls">
            <div class="search-box">
              <svg class="search-icon" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
              <input
                v-model="searchKeyword"
                class="search-input"
                placeholder="搜索文件名或内容..."
              />
            </div>
            <button class="filter-btn" type="button" @click="handleFilter">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/></svg>
              <span>筛选分类</span>
            </button>
          </div>
        </div>

        <!-- 表头 -->
        <div class="file-table-head">
          <div class="col-name">文件名称</div>
          <div class="col-category">业务分类</div>
          <div class="col-status">智能体学习状态</div>
          <div class="col-action">操作</div>
        </div>

        <!-- 文件行 -->
        <div
          v-for="file in filteredFiles"
          :key="file.id"
          class="file-row"
        >
          <div class="col-name">
            <div class="file-icon-wrapper" :class="file.iconColor">
              <component :is="getFileIcon(file.type)" />
            </div>
            <div class="file-info">
              <div class="file-name">{{ file.name }}</div>
              <div class="file-meta">
                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                导入于 {{ file.importDate }}
              </div>
            </div>
          </div>
          <div class="col-category">
            <span class="category-badge" :class="file.categoryColor">{{ file.category }}</span>
          </div>
          <div class="col-status">
            <div v-if="file.status === 'done'" class="status-done">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
              <span>Agent 已掌握</span>
            </div>
            <div v-else class="status-reading">
              <div class="reading-spinner"></div>
              <span>智能体正在阅读…</span>
            </div>
          </div>
          <div class="col-action">
            <button class="delete-file-btn" type="button" @click="handleDeleteFile(file.id)">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="filteredFiles.length === 0" class="file-empty">
          <div class="empty-icon">📄</div>
          <div class="empty-text">{{ searchKeyword ? '没有找到匹配的文件' : '暂无合规依据文件' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h, onMounted } from 'vue';
import { uploadKnowledgeFile, getKnowledgeFiles, deleteKnowledgeFile, type KnowledgeFile } from '../api/agentApi';
import { MessagePlugin } from 'tdesign-vue-next';

const isDragOver = ref(false);
const fileInputRef = ref<HTMLInputElement>();
const searchKeyword = ref('');
const loading = ref(false);

// 后端返回的数据结构
interface OssFile {
  id: string;
  fileName: string;
  fileSuffix: string;
  fileSize: number;
  objectKey: string;
  bizType: string;
  createdAt: string;
  updatedAt: string;
}

const knowledgeFiles = ref<OssFile[]>([]);

// 业务分类映射（根据后缀）
function getCategoryBySuffix(suffix: string): { category: string; categoryColor: string } {
  const map: Record<string, { category: string; categoryColor: string }> = {
    pdf: { category: '国家法规', categoryColor: 'badge-blue' },
    docx: { category: '院内制度', categoryColor: 'badge-purple' },
    doc: { category: '院内制度', categoryColor: 'badge-purple' },
    xlsx: { category: '标准数据字典', categoryColor: 'badge-green' },
    xls: { category: '标准数据字典', categoryColor: 'badge-green' },
    md: { category: '案例黑库', categoryColor: 'badge-orange' },
    txt: { category: '待分类', categoryColor: 'badge-gray' },
  };
  return map[suffix.toLowerCase()] || { category: '待分类', categoryColor: 'badge-gray' };
}

// 图标颜色映射
function getIconColorBySuffix(suffix: string): string {
  const map: Record<string, string> = {
    pdf: 'icon-red',
    docx: 'icon-blue',
    doc: 'icon-blue',
    xlsx: 'icon-green',
    xls: 'icon-green',
    md: 'icon-gray',
    txt: 'icon-gray',
  };
  return map[suffix.toLowerCase()] || 'icon-gray';
}

// 前端展示用的文件对象
interface DisplayFile {
  id: string;
  name: string;
  type: string;
  importDate: string;
  category: string;
  categoryColor: string;
  iconColor: string;
  status: 'done' | 'reading';
  _source: OssFile;
}

const displayFiles = computed<DisplayFile[]>(() => {
  return knowledgeFiles.value.map((f) => {
    const cat = getCategoryBySuffix(f.fileSuffix);
    return {
      id: f.id,
      name: f.fileName,
      type: f.fileSuffix.toLowerCase(),
      importDate: f.createdAt ? f.createdAt.slice(0, 10) : new Date().toISOString().slice(0, 10),
      category: cat.category,
      categoryColor: cat.categoryColor,
      iconColor: getIconColorBySuffix(f.fileSuffix),
      status: 'done' as const,
      _source: f,
    };
  });
});

const filteredFiles = computed(() => {
  if (!searchKeyword.value.trim()) return displayFiles.value;
  const kw = searchKeyword.value.toLowerCase();
  return displayFiles.value.filter(
    (f) => f.name.toLowerCase().includes(kw) || f.category.toLowerCase().includes(kw)
  );
});

// 加载文件列表
async function loadFiles() {
  loading.value = true;
  try {
    const res = await getKnowledgeFiles('KNOWLEDGE', 1, 100);
    if (res.data?.success && res.data?.data?.list) {
      knowledgeFiles.value = res.data.data.list;
    }
  } catch (e: any) {
    MessagePlugin.error('加载文件列表失败');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadFiles();
});

function getFileIcon(type: string) {
  const iconMap: Record<string, any> = {
    pdf: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
        h('line', { x1: '16', y1: '13', x2: '8', y2: '13' }),
        h('line', { x1: '16', y1: '17', x2: '8', y2: '17' }),
      ]),
    docx: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
        h('path', { d: 'M10 12h4' }),
        h('path', { d: 'M10 16h4' }),
      ]),
    doc: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
        h('path', { d: 'M10 12h4' }),
        h('path', { d: 'M10 16h4' }),
      ]),
    xlsx: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
        h('rect', { x: '8', y: '12', width: '8', height: '6', rx: '1' }),
      ]),
    xls: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
        h('rect', { x: '8', y: '12', width: '8', height: '6', rx: '1' }),
      ]),
    md: () =>
      h('svg', { xmlns: 'http://www.w3.org/2000/svg', width: '20', height: '20', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }, [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z' }),
        h('polyline', { points: '14 2 14 8 20 8' }),
      ]),
  };
  return iconMap[type] || iconMap.md;
}

function triggerFileInput() {
  fileInputRef.value?.click();
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) {
    addFiles(Array.from(input.files));
    input.value = '';
  }
}

function handleDrop(e: DragEvent) {
  isDragOver.value = false;
  if (e.dataTransfer?.files) {
    addFiles(Array.from(e.dataTransfer.files));
  }
}

async function addFiles(files: File[]) {
  for (const file of files) {
    try {
      const res = await uploadKnowledgeFile(file, 'KNOWLEDGE');
      if (res.data?.success && res.data?.data) {
        // 重新加载列表
        await loadFiles();
        MessagePlugin.success(`文件 "${file.name}" 上传成功`);
      } else {
        MessagePlugin.error(`文件 "${file.name}" 上传失败`);
      }
    } catch (e: any) {
      MessagePlugin.error(`文件 "${file.name}" 上传失败: ${e.message}`);
    }
  }
}

async function handleDeleteFile(id: string) {
  try {
    const res = await deleteKnowledgeFile(id);
    if (res.data?.success) {
      knowledgeFiles.value = knowledgeFiles.value.filter((f) => f.id !== id);
      MessagePlugin.success('删除成功');
    } else {
      MessagePlugin.error('删除失败');
    }
  } catch (e: any) {
    MessagePlugin.error('删除失败');
  }
}

function handleFilter() {
  // 筛选功能 placeholder
  console.log('筛选分类');
}
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100vh;
  background: #fff;
}

/* ========== 顶部栏 ========== */
.kb-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 74px;
  padding: 0 32px;
  border-bottom: 1px solid #e6edf5;
  background: rgba(255, 255, 255, 0.9);
  flex-shrink: 0;
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
.kb-body {
  flex: 1;
  padding: 40px 48px 60px;
  overflow-y: auto;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.kb-headline {
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
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
  flex-shrink: 0;
}

.headline-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
}

.kb-desc {
  margin: 0 0 32px;
  color: #64748b;
  font-size: 15px;
  line-height: 1.7;
}

/* ========== 上传区域 ========== */
.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 32px 36px;
  border: 2px dashed #d0d8e4;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fafd 0%, #ffffff 100%);
  cursor: pointer;
  transition: all 0.25s ease;
  margin-bottom: 40px;
}

.upload-zone:hover {
  border-color: #93b4f5;
  background: linear-gradient(180deg, #f0f5ff 0%, #f8faff 100%);
}

.upload-zone.drag-over {
  border-color: #3b82f6;
  background: linear-gradient(180deg, #ebf1ff 0%, #f0f5ff 100%);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.08);
}

.upload-icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #dbeafe, #e0e7ff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
  margin-bottom: 20px;
  transition: transform 0.3s;
}

.upload-zone:hover .upload-icon-wrapper {
  transform: translateY(-4px);
}

.upload-text-main {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.upload-text-sub {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 20px;
  text-align: center;
  max-width: 420px;
  line-height: 1.6;
}

.upload-tags {
  display: flex;
  gap: 10px;
}

.upload-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  transition: all 0.15s;
}

.upload-tag:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

/* ========== 文件列表 ========== */
.file-list-section {
  border: 1px solid #e6edf5;
  border-radius: 16px;
  overflow: hidden;
}

.file-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
  gap: 16px;
  flex-wrap: wrap;
}

.file-list-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
}

.file-count {
  color: #94a3b8;
  font-weight: 500;
}

.file-list-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 12px;
  color: #94a3b8;
  pointer-events: none;
}

.search-input {
  width: 220px;
  padding: 8px 12px 8px 34px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #334155;
  background: #f8fafc;
  outline: none;
  transition: all 0.2s;
}

.search-input:focus {
  border-color: #93b4f5;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.08);
}

.search-input::placeholder {
  color: #b0bec5;
}

.filter-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-btn:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #475569;
}

/* 表头 */
.file-table-head {
  display: grid;
  grid-template-columns: 1fr 140px 180px 60px;
  padding: 12px 24px;
  background: #f8fafc;
  border-bottom: 1px solid #f1f5f9;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

/* 文件行 */
.file-row {
  display: grid;
  grid-template-columns: 1fr 140px 180px 60px;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #f6f8fb;
  transition: background 0.15s;
}

.file-row:last-child {
  border-bottom: none;
}

.file-row:hover {
  background: #fafbfd;
}

.col-name {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.file-icon-wrapper {
  display: flex;
  width: 38px;
  height: 38px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.file-icon-wrapper.icon-red {
  background: #fef2f2;
  color: #ef4444;
}

.file-icon-wrapper.icon-blue {
  background: #eff6ff;
  color: #3b82f6;
}

.file-icon-wrapper.icon-green {
  background: #f0fdf4;
  color: #22c55e;
}

.file-icon-wrapper.icon-gray {
  background: #f8fafc;
  color: #94a3b8;
}

.file-info {
  min-width: 0;
}

.file-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}

.col-category {
  display: flex;
  align-items: center;
}

.category-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.badge-blue {
  background: #dbeafe;
  color: #2563eb;
}

.badge-purple {
  background: #f3e8ff;
  color: #7c3aed;
}

.badge-green {
  background: #dcfce7;
  color: #16a34a;
}

.badge-orange {
  background: #fff7ed;
  color: #ea580c;
}

.badge-gray {
  background: #f1f5f9;
  color: #64748b;
}

.col-status {
  display: flex;
  align-items: center;
}

.status-done {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #16a34a;
  font-size: 13px;
  font-weight: 500;
}

.status-reading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #f59e0b;
  font-size: 13px;
  font-weight: 500;
}

.reading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #fde68a;
  border-top-color: #f59e0b;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.col-action {
  display: flex;
  align-items: center;
  justify-content: center;
}

.delete-file-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.15s;
}

.delete-file-btn:hover {
  background: #fef2f2;
  color: #ef4444;
}

/* 空状态 */
.file-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
}

/* ========== 响应式 ========== */
@media (max-width: 900px) {
  .kb-body {
    padding: 28px 20px 40px;
  }

  .file-table-head,
  .file-row {
    grid-template-columns: 1fr 100px 150px 48px;
  }

  .search-input {
    width: 160px;
  }
}

@media (max-width: 640px) {
  .file-table-head {
    display: none;
  }

  .file-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .col-category,
  .col-status {
    padding-left: 52px;
  }

  .col-action {
    justify-content: flex-end;
  }
}
</style>
