<template>
  <div class="chat-view">
    <!-- 左侧边栏 -->
    <aside class="chat-sidebar">
      <AgentSidebar />
    </aside>

    <!-- 右侧聊天区域 -->
    <main class="chat-main">
      <!-- 聊天消息列表 -->
      <div ref="messageListRef" class="message-list">
        <div
          v-for="msg in store.activeMessages"
          :key="msg.id"
          :class="['message-row', `role-${msg.role}`]"
        >
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="message-item user-message">
            <div class="avatar user-avatar">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            </div>
            <div class="message-content user-content">{{ msg.content }}</div>
          </div>

          <!-- 助手消息 -->
          <div v-else class="message-item assistant-message">
            <div class="avatar agent-avatar">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
            </div>
            <div class="message-content assistant-content">
              <div v-if="msg.result" class="result-info">
                <span class="trace-id">traceId: {{ msg.result.traceId }}</span>
                <span class="scene-badge">{{ msg.result.scene }}</span>
              </div>
              <div v-if="msg.result?.summary" class="result-summary">{{ msg.result.summary }}</div>
              <div v-if="msg.result?.riskLevel" class="risk-badge" :class="msg.result.riskLevel">
                风险等级: {{ msg.result.riskLevel }}
              </div>
              <div v-if="msg.result?.score" class="score-badge">评分: {{ msg.result.score }}</div>
              <div v-if="msg.content" class="answer-text">{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <!-- 加载中状态 -->
        <div v-if="store.sending" class="message-row role-assistant">
          <div class="message-item assistant-message">
            <div class="avatar agent-avatar">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
            </div>
            <div class="message-content assistant-content loading-content">
              <div class="loading-dot"></div>
              <div class="loading-dot"></div>
              <div class="loading-dot"></div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!store.activeSessionId && !store.sending" class="empty-state">
          <div class="empty-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
          </div>
          <h3>欢迎使用横渡智能体</h3>
          <p>描述您的监管需求，例如：检测这两份标书文件是否雷同</p>
        </div>
      </div>

      <!-- 底部输入区 -->
      <div class="input-area">
        <!-- 文件上传预览 -->
        <div v-if="pendingFiles.length > 0" class="file-preview">
          <t-tag v-for="pf in pendingFiles" :key="pf.attachment.id" closable @close="removeFile(pf.attachment.id)">
            {{ pf.attachment.name }}
          </t-tag>
        </div>

        <div class="input-row">
          <!-- 上传按钮 -->
          <t-button variant="outline" theme="default" @click="triggerUpload">
            <template #icon>
              <t-icon name="upload" />
            </template>
            上传标书
          </t-button>
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.md,.txt"
            style="display: none"
            @change="handleFileChange"
          />

          <!-- 文本输入框 -->
          <t-textarea
            v-model="inputText"
            class="input-textarea"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            :autosize="{ minRows: 1, maxRows: 4 }"
            @keydown="handleKeydown"
          />

          <!-- 发送按钮 -->
          <t-button
            theme="primary"
            :disabled="!inputText.trim() && pendingFiles.length === 0"
            :loading="store.sending"
            @click="handleSend"
          >
            发送
          </t-button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch, onMounted } from 'vue';
import { useAgentStore } from '../store/agentStore';
import AgentSidebar from '../sections/sidebar/AgentSidebar.vue';
import type { Attachment } from '../types/agent';

const store = useAgentStore();
const messageListRef = ref<HTMLElement>();
const fileInputRef = ref<HTMLInputElement>();
const inputText = ref('');
// 存储待上传的文件和对应的附件信息
const pendingFiles = ref<{ file: File; attachment: Attachment }[]>([]);

// 初始化加载会话
onMounted(async () => {
  await store.loadSessions();
  if (store.sessions.length > 0 && !store.activeSessionId) {
    await store.selectSession(store.sessions[0].id);
  }
});

// 滚动到底部
watch(
  () => store.activeMessages.length,
  () => {
    nextTick(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
      }
    });
  }
);

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

async function handleSend() {
  const text = inputText.value.trim();
  const files = pendingFiles.value;

  if (!text && files.length === 0) return;

  // 如果有文件，调用上传接口
  if (files.length > 0) {
    const fileList = files.map((pf) => pf.file);
    await store.uploadFiles(fileList, text || undefined);
    pendingFiles.value = [];
  } else {
    await store.sendMessage(text);
  }

  inputText.value = '';
}

function triggerUpload() {
  fileInputRef.value?.click();
}

function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  if (target.files) {
    const newFiles = Array.from(target.files).map((file) => ({
      file,
      attachment: {
        id: `file_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        name: file.name,
        size: file.size,
        type: file.type,
      },
    }));
    pendingFiles.value.push(...newFiles);
  }
  // 清空 input 以允许重复选择同一文件
  target.value = '';
}

function removeFile(fileId: string) {
  pendingFiles.value = pendingFiles.value.filter((f) => f.attachment.id !== fileId);
}
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100vh;
  width: 100%;
  background: linear-gradient(180deg, #f8fbff 0%, #f5f7fb 100%);
}

.chat-sidebar {
  width: 280px;
  flex-shrink: 0;
  border-right: 1px solid #dfe7f1;
  background: #fff;
  overflow: hidden;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.message-row {
  display: flex;
  margin-bottom: 16px;
}

.role-user {
  justify-content: flex-end;
}

.role-assistant {
  justify-content: flex-start;
}

.message-item {
  display: flex;
  align-items: flex-start;
  max-width: 80%;
  gap: 12px;
}

.user-message {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar {
  background: linear-gradient(135deg, #0ea5e9, #3b82f6);
  color: #fff;
}

.agent-avatar {
  background: linear-gradient(135deg, #818cf8, #c084fc);
  color: #fff;
}

.message-content {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.user-content {
  background: linear-gradient(135deg, #0ea5e9, #3b82f6);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.assistant-content {
  background: #fff;
  color: #0f172a;
  border-bottom-left-radius: 6px;
  border: 1px solid #e2e8f0;
}

.loading-content {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 16px 20px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background: #818cf8;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dot:nth-child(1) { animation-delay: -0.32s; }
.loading-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.result-info {
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
  font-size: 12px;
  color: #64748b;
}

.trace-id {
  font-family: monospace;
}

.scene-badge {
  background: #f0f3f7;
  padding: 2px 8px;
  border-radius: 4px;
}

.result-summary {
  font-weight: 500;
  margin-bottom: 8px;
  color: #1e293b;
}

.risk-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 8px;
}

.risk-badge.high {
  background: #fef2f2;
  color: #dc2626;
}

.risk-badge.medium {
  background: #fffbeb;
  color: #d97706;
}

.risk-badge.low {
  background: #f0fdf4;
  color: #16a34a;
}

.score-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  background: #f0f3f7;
  color: #475569;
  margin-bottom: 8px;
}

.answer-text {
  line-height: 1.7;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #64748b;
}

.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  background: linear-gradient(135deg, #38bdf8, #818cf8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.empty-state p {
  margin: 0;
  font-size: 15px;
}

.input-area {
  padding: 16px 24px 24px;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}

.file-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.input-textarea {
  flex: 1;
}

:deep(.t-textarea__inner) {
  border-radius: 12px;
}
</style>
