<template>
  <div class="message-wrap" :class="{ 'is-user-wrap': msg.role === 'user' }">
    <div class="message-bubble" :class="{ 'is-user': msg.role === 'user', 'is-ai': msg.role === 'assistant' }">
      <div class="avatar" :class="msg.role === 'user' ? 'avatar-user' : 'avatar-ai'">
        {{ msg.role === 'user' ? '我' : 'AI' }}
      </div>
      <div class="content">
        <div class="meta">{{ msg.role === 'user' ? '提问' : '合规助手' }}</div>
        <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderedContent" />
        <div v-else>{{ msg.content }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  msg: { type: Object, required: true }
})

const renderedContent = computed(() => {
  return marked.parse(props.msg.content || '', { breaks: true })
})
</script>

<style scoped>
.message-wrap {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 12px;
}

.is-user-wrap {
  justify-content: flex-end;
}

.message-bubble {
  display: flex;
  gap: 10px;
  max-width: 86%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid #e7edf8;
  background: #fff;
}

.message-bubble.is-user {
  flex-direction: row-reverse;
  background: #eaf3ff;
  border-color: #d5e5ff;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.avatar-ai {
  color: #185cc8;
  background: #e6f0ff;
}

.avatar-user {
  color: #fff;
  background: #2f74ff;
}

.content {
  flex: 1;
  line-height: 1.68;
  color: #28354d;
  word-break: break-word;
}

.meta {
  font-size: 12px;
  color: #7e8aa1;
  margin-bottom: 4px;
}

.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 8px 0;
  color: #1f2d46;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(strong) {
  color: #1d5ece;
}

.markdown-body :deep(pre) {
  background: #f3f6fb;
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
}
</style>
