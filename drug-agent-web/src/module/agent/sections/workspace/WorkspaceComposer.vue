<template>
  <div class="composer-shell">
    <input 
      type="file" 
      ref="fileInputRef" 
      style="display: none" 
      multiple 
      accept=".pdf,.doc,.docx,.docx,.md,.txt" 
      @change="handleFileSelect" 
    />

    <!-- 拖拽覆盖层 -->
    <div v-if="isDragOver" class="drop-overlay">
      <div class="drop-hint">
        <t-icon name="upload" size="40px" />
        <p>松开上传文件</p>
      </div>
    </div>

    <div
      class="composer-panel"
      :class="{ 'drag-active': isDragOver }"
      @dragover.prevent="isDragOver = true"
      @dragleave="handleDragLeave"
      @drop.prevent="handleDrop"
    >
      <!-- 文件 chip 列表 -->
      <div v-if="pendingFiles.length > 0" class="pending-files">
        <div v-for="(file, index) in pendingFiles" :key="index" class="file-chip-v2">
          <div class="file-icon-box">
             <t-icon v-if="isPdf(file.name)" name="file-pdf" size="24px" />
             <t-icon v-else-if="isWord(file.name)" name="file-word" size="24px" />
             <t-icon v-else name="file-code" size="24px" />
          </div>
          <div class="file-info-box">
            <div class="file-name">{{ file.name }}</div>
            <div class="file-type">{{ getFileDesc(file.name) }}</div>
          </div>
          <button class="chip-remove-btn" type="button" @click.stop="removePendingFile(index)">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z"/></svg>
          </button>
        </div>
      </div>

      <!-- 上半部分：多行文本输入区 -->
      <div class="composer-input-wrapper">
        <t-textarea
          v-model="inputText"
          class="composer-input"
          :disabled="store.sending || store.uploading"
          placeholder="描述您的监管需求，例如：帮我审查这几份标书的风险..."
          :autosize="{ minRows: 2, maxRows: 8 }"
          @keydown="handleKeydown"
        />
      </div>

      <!-- 分割线 -->
      <div class="composer-divider"></div>

      <!-- 下半部分：操作栏 -->
      <div class="composer-footer">
        <div class="composer-tools">
          <button class="tool-icon-btn" type="button" title="上传附件" @click="triggerFileInput">
            <t-icon name="add" size="22px" />
          </button>

          <button class="tool-icon-btn" type="button" title="引用知识" @click="handleKnowledgeClick">
            <t-icon name="internet" size="20px" />
          </button>
          
          <t-select
            v-model="store.currentModel"
            :options="modelOptions"
            size="small"
            class="model-select"
            placeholder="选择模型"
          >
            <template #valueDisplay="{ value }">
              <span style="font-size: 13px; font-weight: 500;">
                <t-icon name="logo-chrome-filled" style="margin-right:4px;" />
                {{ value === 'minimax' ? 'MiniMax' : (value === 'dashscope' ? '横渡大模型' : value) }}
              </span>
            </template>
          </t-select>
        </div>

        <div class="composer-actions">
          <button
            class="tool-icon-btn"
            type="button"
            :disabled="store.speechRecognizing"
            @click="handleSpeechClick"
            style="margin-right: 8px;"
          >
            <t-icon v-if="!store.speechRecognizing" name="microphone" size="22px" />
            <t-loading v-else size="small" />
          </button>

          <button
            class="send-btn-circle"
            :class="{ active: canSend && !store.sending && !store.uploading }"
            :disabled="!canSend || store.sending || store.uploading"
            @click="handleSend"
          >
            <t-loading v-if="store.sending || store.uploading" size="small" inherit-color />
            <t-icon v-else name="arrow-up" size="24px" />
          </button>
        </div>
      </div>
    </div>

    <div class="composer-note">
      AI 生成内容仅供参考，重大决策请人工复核（横渡智能体 Core v0.3）
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import { MessagePlugin } from 'tdesign-vue-next';
import { speechRecognize } from '../../api/agentApi';

const store = useAgentStore();
const inputText = ref('');
const isDragOver = ref(false);
const pendingFiles = ref<File[]>([]);
const fileInputRef = ref<HTMLInputElement | null>(null);

const modelOptions = computed(() => {
  return store.availableModels.map(m => ({ label: m.name, value: m.model }));
});

/** 有文字或有文件时才能发送 */
const canSend = computed(() => {
  return inputText.value.trim().length > 0 || pendingFiles.value.length > 0;
});

onMounted(() => {
  if (store.availableModels.length === 0) {
    store.loadModels();
  }
});

async function handleSend() {
  const hasFiles = pendingFiles.value.length > 0;
  const hasText = inputText.value.trim().length > 0;

  if (hasFiles) {
    // 有文件：立即清空输入框和文件列表（乐观更新），再等待上传
    const filesToUpload = [...pendingFiles.value];
    const queryText = hasText ? inputText.value.trim() : undefined;
    pendingFiles.value = [];
    inputText.value = '';

    const result = await store.uploadFiles(filesToUpload, queryText);

    if (result?.answer) {
      // 后端已直接审查，结果已在 uploadFiles 中写入消息列表
    } else {
      // 后端只上传了文件，需要后续 chat 触发审查
      if (!hasText) {
        MessagePlugin.info('文件已上传，可继续输入审查指令');
      }
    }
  } else if (hasText) {
    // 纯文字：立即清空（乐观更新），再 await 发送触发 loading
    const content = inputText.value.trim();
    inputText.value = '';
    await store.sendMessage(content);
  }
}

function handleKeydown(value: string, context: { e: KeyboardEvent }) {
  const e = context.e || (value as unknown as KeyboardEvent);
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function handleKnowledgeClick() {
  MessagePlugin.info('知识库关联对话功能建设中，后续可支持拖拽法务条款');
}

async function handleSpeechClick() {
  try {
    // 请求麦克风权限并获取音频流
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mediaRecorder = new MediaRecorder(stream, {
      mimeType: 'audio/webm;codecs=opus'
    });
    const audioChunks: Blob[] = [];

    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        audioChunks.push(event.data);
      }
    };

    // 开始录音
    store.speechRecognizing = true;
    mediaRecorder.start();

    // 显示录音中提示
    MessagePlugin.info('正在聆听，请说话...');

    // 监听录音结束（通过再次点击按钮停止）
    const stopRecording = () => {
      mediaRecorder.stop();
      stream.getTracks().forEach(track => track.stop());
    };

    // 设置超时自动停止（10秒）
    const timeout = setTimeout(() => {
      if (mediaRecorder.state === 'recording') {
        stopRecording();
      }
    }, 10000);

    mediaRecorder.onstop = async () => {
      clearTimeout(timeout);
      store.speechRecognizing = false;

      const audioBlob = new Blob(audioChunks, { type: 'audio/webm' });

      try {
        // 转换为 File 对象
        const audioFile = new File([audioBlob], 'recording.webm', { type: 'audio/webm' });

        // 调用语音识别 API
        const result = await speechRecognize(audioFile, 'webm', 16000, 'zh');

        if (result.success && result.text) {
          // 将识别结果填入输入框
          inputText.value = inputText.value
            ? inputText.value + ' ' + result.text
            : result.text;
          MessagePlugin.success('语音识别成功');
        } else {
          MessagePlugin.error(result.errorMessage || '语音识别失败');
        }
      } catch (e) {
        console.error('[handleSpeechClick] 语音识别失败:', e);
        MessagePlugin.error('语音识别失败，请重试');
      }
    };

    // 停止录音（点击按钮时）
    // 注意：这里通过再次调用来停止，实际使用时按钮应切换状态
    setTimeout(() => {
      if (mediaRecorder.state === 'recording') {
        stopRecording();
      }
    }, 5000); // 默认5秒后自动停止

  } catch (e) {
    store.speechRecognizing = false;
    console.error('[handleSpeechClick] 麦克风权限获取失败:', e);
    MessagePlugin.error('无法访问麦克风，请检查权限设置');
  }
}

function handleDragLeave(e: DragEvent) {
  // 仅在真正离开 composer-panel 时才取消高亮，防止子元素事件干扰
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (
    e.clientX <= rect.left ||
    e.clientX >= rect.right ||
    e.clientY <= rect.top ||
    e.clientY >= rect.bottom
  ) {
    isDragOver.value = false;
  }
}

function handleDrop(e: DragEvent) {
  isDragOver.value = false;
  const droppedFiles = Array.from(e.dataTransfer?.files || []);
  const validFiles = droppedFiles.filter((f) =>
    /\.(pdf|doc|docx|md|txt)$/i.test(f.name)
  );
  if (validFiles.length === 0) {
    MessagePlugin.warning('仅支持 PDF、Word、Markdown、TXT 格式');
    return;
  }
  validFiles.forEach((file) => {
    if (!pendingFiles.value.some((f) => f.name === file.name)) {
      pendingFiles.value.push(file);
    }
  });
  MessagePlugin.success(`已添加 ${validFiles.length} 个文件，可直接发送`);
}

function removePendingFile(index: number) {
  pendingFiles.value.splice(index, 1);
}

function triggerFileInput() {
  fileInputRef.value?.click();
}

function handleFileSelect(e: Event) {
  const target = e.target as HTMLInputElement;
  const files = Array.from(target.files || []);
  if (files.length === 0) return;
  const validFiles = files.filter((f) =>
    /\.(pdf|doc|docx|md|txt)$/i.test(f.name)
  );
  if (validFiles.length === 0) {
    MessagePlugin.warning('仅支持 PDF、Word、Markdown、TXT 格式');
  } else {
    validFiles.forEach((file) => {
      if (!pendingFiles.value.some((f) => f.name === file.name)) {
        pendingFiles.value.push(file);
      }
    });
  }
  if (target) target.value = '';
}

function isPdf(filename: string) {
  return /\.pdf$/i.test(filename);
}

function isWord(filename: string) {
  return /\.(doc|docx)$/i.test(filename);
}

function getFileDesc(filename: string) {
  if (isPdf(filename)) return 'PDF 文档';
  if (isWord(filename)) return 'Word 文档';
  if (/\.ts$/i.test(filename)) return 'TypeScript 文件';
  if (/\.txt$/i.test(filename)) return '文本文件';
  if (/\.md$/i.test(filename)) return 'Markdown 文档';
  return '文档';
}
</script>

<style scoped>
.composer-shell {
  position: relative;
  width: 100%;
}

.composer-panel {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e1e5ec;
  border-radius: 12px;
  box-shadow: 0 4px 16px -4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.composer-panel:focus-within {
  border-color: #cbd5e1;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.composer-panel.drag-active {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.2);
}

/* 拖拽覆盖层 */
.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  background: rgba(96, 165, 250, 0.08);
  border: 2px dashed #60a5fa;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.drop-hint {
  color: #60a5fa;
  text-align: center;
}

.drop-hint p {
  margin: 12px 0 0;
  font-size: 16px;
  font-weight: 600;
}

/* 文件 chip V2 */
.pending-files {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px 4px;
}

.file-chip-v2 {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f7f8fa;
  border: 1px solid #e1e5ec;
  border-radius: 8px;
  max-width: 200px;
  transition: background 0.2s;
}

.file-chip-v2:hover {
  background: #f1f3f6;
}

.file-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: #ffffff;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  color: #4b5563;
}

.file-info-box {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 120px;
}

.file-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-type {
  font-size: 11px;
  color: #6b7280;
  margin-top: 2px;
}

.chip-remove-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #ffffff;
  border: none;
  color: #9ca3af;
  border-radius: 50%;
  width: 18px;
  height: 18px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: color 0.2s, background 0.2s;
}

.chip-remove-btn:hover {
  color: #ef4444;
  background: #fee2e2;
}

/* 输入区 */
.composer-input-wrapper {
  padding: 8px 10px;
}

:deep(.t-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 4px 6px;
  resize: none;
  color: #1f2937;
  font-size: 15px;
  line-height: 1.6;
  background: transparent !important;
  outline: none;
}

:deep(.t-textarea__inner:focus) {
  box-shadow: none !important;
}

:deep(.t-textarea__inner::placeholder) {
  color: #a1a1aa;
}

/* 分割线 */
.composer-divider {
  display: none; /* 去掉分割线更接近截图 */
}

/* 底部操作区 */
.composer-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 16px 12px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.tool-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.2s, color 0.2s;
}

.tool-icon-btn:hover {
  background: #f1f5f9;
  color: #334155;
}

.model-select {
  width: 135px;
}

:deep(.model-select .t-input) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  color: #64748b;
}

:deep(.model-select .t-input:hover) {
  background: #f1f5f9 !important;
  border-radius: 6px;
}


/* 发送按钮 */
.composer-actions {
  display: flex;
  align-items: center;
}

.send-btn-circle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: #e2e8f0;
  color: #ffffff;
  cursor: not-allowed;
  transition: all 0.2s;
}

.send-btn-circle.active {
  background: #3b82f6;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.send-btn-circle.active:hover {
  background: #2563eb;
}

.send-btn-circle.active:active {
  transform: translateY(1px);
}

/* 说明文字 */
.composer-note {
  margin-top: 14px;
  color: #9aa7ba;
  text-align: center;
  font-size: 12px;
}
</style>
