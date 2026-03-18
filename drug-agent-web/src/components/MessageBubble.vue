<template>
  <div class="message-row" :class="{ 'is-user-row': msg.role === 'user' }">
    <div v-if="msg.role === 'assistant'" class="bot-avatar">
      <el-icon><MagicStick /></el-icon>
    </div>

    <div class="message-container">
      <div v-if="msg.role === 'assistant'" class="meta">
        <span class="author">Drug-Agent</span>
        <span class="time">{{ msg.time || '刚刚' }}</span>
      </div>
      <div v-else class="meta user-meta">
        <span class="time">您 • {{ msg.time || '刚刚' }}</span>
      </div>

      <div class="message-bubble" :class="{ 'is-user': msg.role === 'user', 'is-ai': msg.role === 'assistant' }">
        <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderedContent" />
        <div v-else class="plain-content">{{ msg.content }}</div>
      </div>

      <div v-if="msg.role === 'user' && msg.files && msg.files.length" class="attachment-list">
        <div v-for="file in msg.files" :key="file.name" class="attachment-card">
          <el-icon><Document /></el-icon>
          <span class="file-name">{{ file.name }}</span>
        </div>
      </div>
    </div>

    <div v-if="msg.role === 'user'" class="user-avatar-placeholder">
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
  margin-bottom: 32px;
  gap: 12px;
}

.is-user-row {
  justify-content: flex-end;
}

.bot-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #3b82f6;
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 20px;
  flex-shrink: 0;
}

.user-avatar-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  color: #64748b;
  display: grid;
  place-items: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-container {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.meta {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-meta {
  justify-content: flex-end;
}

.author {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.time {
  font-size: 12px;
  color: #94a3b8;
}

.message-bubble {
  border-radius: 16px;
  padding: 12px 16px;
  font-size: 15px;
  line-height: 1.6;
}

.message-bubble.is-ai {
  background: #fff;
  color: #1e293b;
  border: 1px solid transparent;
  width: fit-content;
}

.message-bubble.is-user {
  background: #1e293b;
  color: #fff;
  border-radius: 20px 4px 20px 20px;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  justify-content: flex-end;
}

.attachment-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 13px;
  color: #475569;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.file-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.markdown-body :deep(p) {
  margin: 0;
}
</style>

