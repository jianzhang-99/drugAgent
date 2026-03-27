<template>
  <div :class="['message-renderer', `type-${message.type}`]">
    <div v-if="message.role === 'user'" class="user-message">
      <div class="avatar user-avatar">
        <span>👤</span>
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
      </div>
    </div>

    <div v-else-if="message.type === 'assistant_text'" class="assistant-message">
      <div class="avatar agent-avatar">
        <span>🤖</span>
      </div>
      <div class="message-body">
        <div class="message-content">{{ message.content }}</div>
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
        <span>🤖</span>
      </div>
      <div class="message-body">
        <div class="message-content">
          <ResultCard :data="message.result" />
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
        <span>🤖</span>
      </div>
      <div class="message-body">
        <div class="message-content">{{ message.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Message } from '../types/agent';
import ResultCard from './ResultCard.vue';

defineProps<{ message: Message }>();
</script>

<style scoped>
.message-renderer {
  max-width: min(820px, 88%);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
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
  background: linear-gradient(135deg, #0f766e, #0284c7);
}

.agent-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-content {
  padding: 14px 18px;
  border-radius: 20px;
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
  background: linear-gradient(135deg, #0f766e, #0284c7);
  color: #fff;
  border-bottom-right-radius: 8px;
  box-shadow: 0 18px 40px rgba(2, 132, 199, 0.2);
}

.assistant-message {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.assistant-message .message-content {
  background: #f7f9fc;
  color: #16333e;
  border-bottom-left-radius: 8px;
  border: 1px solid rgba(19, 49, 59, 0.06);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06);
}

.clarify-message {
  background: rgba(255, 255, 255, 0.94);
  border-radius: 22px;
  padding: 16px 18px;
  border: 1px solid rgba(250, 173, 20, 0.26);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.08);
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
</style>
