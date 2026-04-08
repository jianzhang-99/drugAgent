<template>
  <div class="composer-shell">
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

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
        <div v-for="(file, index) in pendingFiles" :key="index" class="file-chip">
          <t-icon name="file-pdf" size="14px" />
          <span class="chip-name">{{ file.name }}</span>
          <button class="chip-remove" type="button" @click.stop="removePendingFile(index)">
            <t-icon name="close" size="12px" />
          </button>
        </div>
      </div>

      <!-- 上半部分：多行文本输入区 -->
      <div class="composer-input-wrapper">
        <t-textarea
          v-model="inputText"
          class="composer-input"
          :disabled="store.sending || store.uploading"
          placeholder="描述您的监管需求，例如：帮我审查这几份标书的围标风险..."
          :autosize="{ minRows: 2, maxRows: 8 }"
          @keydown="handleKeydown"
        />
      </div>

      <!-- 分割线 -->
      <div class="composer-divider"></div>

      <!-- 下半部分：操作栏 -->
      <div class="composer-footer">
        <div class="composer-tools">
          <button class="tool-btn" type="button" @click="showUploadPanel = true">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <span>上传材料</span>
          </button>

          <button class="tool-btn" type="button" @click="handleKnowledgeClick">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            <span>引用知识</span>
          </button>

          <button
            class="tool-btn"
            type="button"
            :disabled="store.speechRecognizing"
            @click="handleSpeechClick"
          >
            <svg v-if="!store.speechRecognizing" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>
            <t-loading v-if="store.speechRecognizing" size="small" />
            <span>{{ store.speechRecognizing ? '识别中' : '语音输入' }}</span>
          </button>

          <!-- 拖拽提示（无文件时显示） -->
          <span v-if="pendingFiles.length === 0" class="drag-tip">
            或直接拖拽文件到此处
          </span>
        </div>

        <div class="composer-actions">
          <t-select
            v-model="store.currentModel"
            :options="modelOptions"
            size="small"
            style="width: 140px; margin-right: 12px"
            placeholder="选择模型"
          >
            <template #valueDisplay="{ value }">
              <span style="font-size: 13px">{{ value === 'minimax' ? 'MiniMax' : (value === 'dashscope' ? '阿里云百炼' : value) }}</span>
            </template>
          </t-select>

          <button
            class="send-btn"
            :class="{ active: canSend && !store.sending && !store.uploading }"
            :disabled="!canSend || store.sending || store.uploading"
            @click="handleSend"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            <span>{{ store.uploading ? '上传中' : '发送任务' }}</span>
            <t-loading v-if="store.sending || store.uploading" size="small" inherit-color style="margin-left: 4px" />
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
import UploadPanel from '../../components/UploadPanel.vue';
import { MessagePlugin } from 'tdesign-vue-next';
import { speechRecognize } from '../../api/agentApi';

const store = useAgentStore();
const inputText = ref('');
const showUploadPanel = ref(false);
const isDragOver = ref(false);
const pendingFiles = ref<File[]>([]);

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

function handleSend() {
  const hasFiles = pendingFiles.value.length > 0;
  const hasText = inputText.value.trim().length > 0;

  if (hasFiles) {
    // 有文件：先上传（query 带上文字内容），再自动 chat
    store.uploadFiles(pendingFiles.value, hasText ? inputText.value.trim() : undefined).then((result) => {
      pendingFiles.value = [];
      inputText.value = '';
      // uploadFiles 成功后 fileIds 已写入 sessionFileIds，后续 chat 自动携带
      // 若后端返回了直接审查结果（code=0 且有 answer），这里无需额外操作
      // 若后端只上传了文件未审查，result 为审查结果或 null
      if (result?.answer) {
        // 后端已直接审查，结果已在 uploadFiles 中写入消息列表
      } else {
        // 后端只上传了文件，需要后续 chat 触发审查
        // 此时 sessionFileIds 已有文件ID，用户可再发文字触发审查
        if (!hasText) {
          MessagePlugin.info('文件已上传，可继续输入审查指令');
        }
      }
    });
  } else if (hasText) {
    // 无文件：走普通文字 chat（自动带上 sessionFileIds 中的历史文件）
    store.sendMessage(inputText.value.trim());
    inputText.value = '';
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

/* 文件 chip */
.pending-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px 0;
}

.file-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 20px;
  color: #1d4ed8;
  font-size: 13px;
  max-width: 220px;
}

.chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-remove {
  border: none;
  background: transparent;
  color: #60a5fa;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  transition: color 0.2s;
}

.chip-remove:hover {
  color: #2563eb;
}

/* 输入区 */
.composer-input-wrapper {
  padding: 8px 6px;
}

:deep(.t-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 8px 12px;
  resize: none;
  color: #334155;
  font-size: 15px;
  line-height: 1.6;
  background: transparent !important;
  outline: none;
}

:deep(.t-textarea__inner:focus) {
  box-shadow: none !important;
}

:deep(.t-textarea__inner::placeholder) {
  color: #94a3b8;
}

/* 分割线 */
.composer-divider {
  height: 1px;
  background-color: #f1f5f9;
  margin: 0 12px;
}

/* 底部操作区 */
.composer-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px 10px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 16px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.drag-tip {
  font-size: 12px;
  color: #94a3b8;
  font-style: italic;
}

/* 发送按钮 */
.composer-actions {
  display: flex;
  align-items: center;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 20px;
  height: 36px;
  border-radius: 6px;
  border: none;
  background: #cbd5e1;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: not-allowed;
  transition: all 0.2s;
}

.send-btn.active {
  background: #60a5fa;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(96, 165, 250, 0.3);
}

.send-btn.active:hover {
  background: #3b82f6;
}

.send-btn.active:active {
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
