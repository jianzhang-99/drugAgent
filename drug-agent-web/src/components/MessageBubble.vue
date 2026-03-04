<template>
  <div class="message-bubble" :class="{ 'is-user': msg.role === 'user' }">
    <div class="avatar">
      {{ msg.role === 'user' ? '👤' : '🤖' }}
    </div>
    <div class="content">
      <!-- AI 消息用 Markdown 渲染 -->
      <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderedContent" />
      <!-- 用户消息纯文本 -->
      <div v-else>{{ msg.content }}</div>
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
.message-bubble {
    display: flex;
    gap: 12px;
    padding: 16px;
    margin-bottom: 12px;
}
.message-bubble.is-user {
    flex-direction: row-reverse;
    background: #f0f7ff;
    border-radius: 12px;
}
.avatar {
    font-size: 24px;
    flex-shrink: 0;
}
.content {
    flex: 1;
    line-height: 1.6;
    word-break: break-word;
}
/* 简单的 Markdown 样式适配 */
.markdown-body :deep(p) {
    margin: 0 0 10px 0;
}
.markdown-body :deep(pre) {
    background: #f6f8fa;
    padding: 10px;
    border-radius: 6px;
    overflow-x: auto;
}
</style>
