<template>
  <div :class="['message-renderer', `type-${message.type}`]">
    <div v-if="message.role === 'user'" class="user-message">
      <div class="avatar user-avatar">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      </div>
      <div class="message-body">
        <div class="message-content">
          <div v-if="message.attachments?.length" class="attachment-list">
            <span v-for="file in message.attachments" :key="file.id" class="attachment-chip">
              {{ file.name }}
            </span>
          </div>
          <div>{{ message.content }}</div>
        </div>
        <div class="message-time user-time" v-if="message.createdAt">{{ formatTime(message.createdAt) }}</div>
      </div>
    </div>

    <div v-else-if="message.type === 'assistant_text'" class="assistant-message">
      <div class="avatar agent-avatar">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
      </div>
      <div class="message-body">
        <!-- 思考步骤（可折叠） -->
        <ThinkingSteps
          v-if="message.thinkingSteps && message.thinkingSteps.length > 0"
          :steps="message.thinkingSteps"
        />
        <div class="message-content">
          {{ message.content }}
          <span v-if="store.streaming && store.streamingMessageId === message.id" class="typing-cursor">|</span>
        </div>
        <div class="message-time agent-time" v-if="message.createdAt">
          <span>内容由横渡智能体生成 · {{ formatTime(message.createdAt) }}</span>
          <button type="button" class="copy-btn" @click="handleCopy(message.content)">复制</button>
        </div>
      </div>
    </div>

    <div v-else-if="message.type === 'assistant_processing'" class="processing-message">
      <div class="avatar agent-avatar">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
      </div>
      <div class="message-body">
        <!-- 思考步骤 -->
        <ThinkingSteps
          v-if="message.thinkingSteps && message.thinkingSteps.length > 0"
          :steps="message.thinkingSteps"
          :default-expanded="true"
        />
        <div class="message-content processing-content">
          <t-loading />
          <span>{{ message.content }}</span>
        </div>
      </div>
    </div>

    <div v-else-if="message.type === 'assistant_clarify'" class="clarify-message">
      <div class="message-header">
        <t-icon name="help-circle" />
        <span>需要更多信息</span>
      </div>
      <div class="message-content">{{ message.content }}</div>
    </div>

    <div v-else-if="message.type === 'assistant_result_card'" class="result-message">
      <div class="avatar agent-avatar">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
      </div>
      <div class="message-body">
        <!-- 思考步骤（可折叠） -->
        <ThinkingSteps
          v-if="message.thinkingSteps && message.thinkingSteps.length > 0"
          :steps="message.thinkingSteps"
        />
        <div class="message-content">
          <ResultCard :data="message.result" />
        </div>
        <div class="message-time agent-time" v-if="message.createdAt">
          <span>内容由横渡智能体生成 · {{ formatTime(message.createdAt) }}</span>
        </div>
      </div>
    </div>

    <div v-else-if="message.type === 'system_error'" class="error-message">
      <t-icon name="error-circle" />
      <span>{{ message.content }}</span>
    </div>

    <div v-else-if="message.type === 'uploading'" class="uploading-message">
      <t-loading />
      <span>文件上传中...</span>
    </div>

    <div v-else class="assistant-message">
      <div class="avatar agent-avatar">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
      </div>
      <div class="message-body">
        <!-- 思考步骤（可折叠） -->
        <ThinkingSteps
          v-if="message.thinkingSteps && message.thinkingSteps.length > 0"
          :steps="message.thinkingSteps"
        />
        <div class="message-content">
          {{ message.content }}
          <span v-if="store.streaming && store.streamingMessageId === message.id" class="typing-cursor">|</span>
        </div>
        <div class="message-time agent-time" v-if="message.createdAt">
          <span>内容由横渡智能体生成 · {{ formatTime(message.createdAt) }}</span>
          <button type="button" class="copy-btn" @click="handleCopy(message.content)">复制</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Message } from '../types/agent';
import ResultCard from './ResultCard.vue';
import ThinkingSteps from './ThinkingSteps.vue';
import { ElMessage } from 'element-plus';
import { useAgentStore } from '../store/agentStore';

defineProps<{ message: Message }>();
const store = useAgentStore();

function formatTime(isoStr?: string) {
  if (!isoStr) return '';
  const date = new Date(isoStr);
  if (isNaN(date.getTime())) return isoStr;
  const h = String(date.getHours()).padStart(2, '0');
  const m = String(date.getMinutes()).padStart(2, '0');
  const s = String(date.getSeconds()).padStart(2, '0');
  return `${h}:${m}:${s}`;
}

async function handleCopy(text: string) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('复制成功');
  } catch (e) {
    ElMessage.error('复制失败');
  }
}
</script>

<style scoped>
.message-renderer {
  max-width: min(820px, 88%);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
  animation: slide-up-fade 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

@keyframes slide-up-fade {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.user-avatar {
  background: linear-gradient(135deg, #0ea5e9, #3b82f6);
  color: #fff;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.2);
}

.agent-avatar {
  background: linear-gradient(135deg, #818cf8, #c084fc);
  color: #fff;
  box-shadow: 0 4px 10px rgba(139, 92, 246, 0.2);
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-content {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;
}

.message-meta {
  margin-bottom: 8px;
  font-size: 12px;
  color: #6e8792;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.attachment-chip {
  display: inline-flex;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  font-size: 12px;
}

.user-message {
  display: flex;
  flex-direction: row-reverse;
  align-items: flex-start;
}

.user-message .message-content {
  background: linear-gradient(135deg, #0ea5e9, #3b82f6);
  color: #fff;
  border-bottom-right-radius: 6px;
  box-shadow: 0 8px 24px -6px rgba(59, 130, 246, 0.35);
}

.assistant-message {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.assistant-message .message-content {
  background: #ffffff;
  color: #0f172a;
  border-bottom-left-radius: 6px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px -2px rgba(0, 0, 0, 0.04);
}

.clarify-message {
  background: #ffffff;
  border-radius: 18px;
  padding: 16px 18px;
  border: 1px solid rgba(250, 173, 20, 0.4);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.result-message {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.result-message .message-content {
  background: #f7f9fc;
  border-bottom-left-radius: 8px;
  border: 1px solid rgba(19, 49, 59, 0.06);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #faad14;
  font-weight: 500;
  margin-bottom: 10px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 242, 240, 0.9);
  color: #ff4d4f;
  border-radius: 18px;
}

.uploading-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.92);
  color: #5b7380;
  border-radius: 18px;
  border: 1px solid rgba(19, 49, 59, 0.08);
}

.processing-message {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.processing-message .message-body {
  flex: 1;
  min-width: 0;
}

.processing-content {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

.message-time {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-time {
  justify-content: flex-end;
  padding-right: 4px;
}

.agent-time {
  justify-content: flex-start;
  padding-left: 4px;
}

.copy-btn {
  background: transparent;
  border: none;
  color: #3b82f6;
  cursor: pointer;
  padding: 0;
  font-size: 12px;
  transition: color 0.2s;
}

.copy-btn:hover {
  color: #2563eb;
  text-decoration: underline;
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: #3b82f6;
  font-weight: bold;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
