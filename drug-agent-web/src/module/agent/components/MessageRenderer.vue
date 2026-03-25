<template>
  <div :class="['message-renderer', `type-${message.type}`]">
    <!-- 用户消息 -->
    <div v-if="message.role === 'user'" class="user-message">
      <div class="message-content">{{ message.content }}</div>
    </div>

    <!-- AI 消息 - 文本 -->
    <div v-else-if="message.type === 'assistant_text'" class="assistant-message">
      <div class="message-content">{{ message.content }}</div>
    </div>

    <!-- AI 消息 - 澄清 -->
    <div v-else-if="message.type === 'assistant_clarify'" class="clarify-message">
      <div class="message-header">
        <t-icon name="help-circle" />
        <span>需要更多信息</span>
      </div>
      <div class="message-content">{{ message.content }}</div>
    </div>

    <!-- AI 消息 - 结果卡片 -->
    <div v-else-if="message.type === 'assistant_result_card'" class="result-message">
      <ResultCard :data="message.result" />
    </div>

    <!-- 系统错误 -->
    <div v-else-if="message.type === 'system_error'" class="error-message">
      <t-icon name="error-circle" />
      <span>{{ message.content }}</span>
    </div>

    <!-- 上传中 -->
    <div v-else-if="message.type === 'uploading'" class="uploading-message">
      <t-loading-indicator />
      <span>文件上传中...</span>
    </div>

    <!-- 默认 -->
    <div v-else class="assistant-message">
      <div class="message-content">{{ message.content }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Message } from '../types/agent';
import ResultCard from './ResultCard.vue';

defineProps<{
  message: Message;
}>();
</script>

<style scoped>
.message-renderer {
  max-width: 80%;
}

.message-content {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.assistant-message .message-content {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.clarify-message {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #faad14;
  font-weight: 500;
  margin-bottom: 8px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff2f0;
  color: #ff4d4f;
  border-radius: 8px;
}

.uploading-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  color: #666;
  border-radius: 8px;
}
</style>
