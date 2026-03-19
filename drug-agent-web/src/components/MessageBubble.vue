<template>
  <div class="message-row" :class="{ 'is-user-row': msg.role === 'user' }">
    <div v-if="msg.role === 'assistant'" class="bot-avatar">
      <el-icon><MagicStick /></el-icon>
    </div>

    <div class="message-container">
      <div v-if="msg.role === 'assistant'" class="meta">
        <span class="author">横渡智能监管</span>
        <span class="dot">·</span>
        <span class="time">{{ msg.time || '刚刚' }}</span>
      </div>
      <div v-else class="meta user-meta">
        <span class="time">您 · {{ msg.time || '14:20' }}</span>
      </div>

      <div class="message-bubble" :class="{ 'is-user': msg.role === 'user', 'is-ai': msg.role === 'assistant' }">
        <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderedContent" />
        <div v-else class="plain-content">{{ msg.content }}</div>
      </div>

      <div v-if="msg.role === 'user' && msg.files && msg.files.length" class="attachment-list">
        <div v-for="file in msg.files" :key="file.name" class="attachment-card">
          <el-icon class="file-icon"><Document /></el-icon>
          <span class="file-name">{{ file.name }}</span>
        </div>
      </div>
    </div>

    <div v-if="msg.role === 'user'" class="user-avatar">
      <el-icon><User /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import { MagicStick, Document, User } from '@element-plus/icons-vue'

const props = defineProps({
  msg: { type: Object, required: true }
})

const renderedContent = computed(() => marked.parse(props.msg.content || '', { breaks: true }))
</script>

<style scoped>
.message-row {
  display: flex;
  margin-bottom: 24px;
  gap: 16px;
  width: 100%;
}

.is-user-row {
  justify-content: flex-end;
}

.bot-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #3b82f6;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 20px;
  flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.2);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  color: #a0aec0;
  display: grid;
  place-items: center;
  font-size: 20px;
  flex-shrink: 0;
}

.message-container {
  max-width: 85%;
  display: flex;
  flex-direction: column;
}

.meta {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-meta {
  justify-content: flex-end;
}

.author {
  font-size: 13px;
  font-weight: 600;
  color: #718096;
}

.dot {
  color: #cbd5e1;
  font-size: 14px;
}

.time {
  font-size: 12px;
  color: #a0aec0;
}

.message-bubble {
  font-size: 15px;
  line-height: 1.6;
}

.message-bubble.is-ai {
  color: #1a202c;
  padding: 4px 0;
}

.message-bubble.is-user {
  background: #0f172a;
  color: #fff;
  padding: 14px 20px;
  border-radius: 12px 2px 12px 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
  justify-content: flex-end;
}

.attachment-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #edf2f7;
  border-radius: 30px;
  font-size: 13px;
  color: #4a5568;
  transition: all 0.2s;
  cursor: pointer;
}

.attachment-card:hover {
  border-color: #3b82f6;
  background: #f0f7ff;
}

.file-icon {
  color: #3b82f633;
  color: #3b82f6; 
}

.file-name {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.markdown-body :deep(p) {
  margin: 0;
}
</style>

