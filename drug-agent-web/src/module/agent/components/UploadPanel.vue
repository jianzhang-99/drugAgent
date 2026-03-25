<template>
  <div class="upload-panel">
    <div class="panel-header">
      <span class="panel-title">上传标书文件</span>
      <t-button theme="default" variant="text" @click="handleClose">
        <t-icon name="close" />
      </t-button>
    </div>

    <div class="panel-body">
      <!-- 文件选择区 -->
      <div
        class="upload-zone"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave="isDragOver = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <div class="upload-hint">
          <t-icon name="upload" size="32px" />
          <p>点击或拖拽文件到此处上传</p>
          <p class="upload-formats">支持 PDF、Word、Markdown、TXT 格式</p>
        </div>
      </div>

      <!-- 文件列表 -->
      <div v-if="fileList.length > 0" class="file-list">
        <div v-for="(file, index) in fileList" :key="index" class="file-item">
          <t-icon name="file-pdf" />
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">{{ formatFileSize(file.size) }}</span>
          <t-button
            theme="default"
            variant="text"
            size="small"
            @click="removeFile(index)"
          >
            <t-icon name="close" />
          </t-button>
        </div>
      </div>

      <!-- 查询条件 -->
      <div class="query-input">
        <t-input
          v-model="queryText"
          placeholder="添加查询说明（可选）"
        />
      </div>
    </div>

    <div class="panel-footer">
      <t-button theme="default" @click="handleClose">取消</t-button>
      <t-button
        theme="primary"
        :disabled="fileList.length === 0"
        :loading="store.uploading"
        @click="handleSubmit"
      >
        开始审查
      </t-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useAgentStore } from '../store/agentStore';

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const store = useAgentStore();
const fileList = ref<File[]>([]);
const queryText = ref('');
const isDragOver = ref(false);

function triggerFileInput() {
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.accept = '.pdf,.doc,.docx,.md,.txt';
  input.onchange = (e) => {
    const target = e.target as HTMLInputElement;
    if (target.files) {
      addFiles(Array.from(target.files));
    }
  };
  input.click();
}

function handleDrop(e: DragEvent) {
  isDragOver.value = false;
  const droppedFiles = Array.from(e.dataTransfer?.files || []);
  addFiles(droppedFiles);
}

function addFiles(newFiles: File[]) {
  newFiles.forEach(file => {
    if (!fileList.value.some(f => f.name === file.name)) {
      fileList.value.push(file);
    }
  });
}

function removeFile(index: number) {
  fileList.value.splice(index, 1);
}

function handleClose() {
  emit('close');
}

async function handleSubmit() {
  if (fileList.value.length === 0) return;

  await store.uploadFiles(fileList.value, queryText.value || undefined);
  fileList.value = [];
  queryText.value = '';
  emit('close');
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}
</script>

<style scoped>
.upload-panel {
  background: #fff;
  border-radius: 8px;
  margin: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e7e7e7;
}

.panel-title {
  font-size: 16px;
  font-weight: 500;
}

.panel-body {
  padding: 16px;
}

.upload-zone {
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 32px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s;
}

.upload-zone:hover,
.upload-zone.drag-over {
  border-color: #1890ff;
}

.upload-hint {
  color: #666;
}

.upload-hint p {
  margin: 8px 0 0;
}

.upload-formats {
  font-size: 12px;
  color: #999;
}

.file-list {
  margin-top: 16px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: #999;
  font-size: 12px;
}

.query-input {
  margin-top: 16px;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #e7e7e7;
}
</style>
