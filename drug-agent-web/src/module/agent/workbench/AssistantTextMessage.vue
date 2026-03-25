<template>
  <div class="assistant-text-message flex gap-4 justify-start animate-fade-in">
    <!-- AI 头像 -->
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
      DA
    </div>

    <!-- 消息内容 -->
    <div class="max-w-2xl bg-white border border-slate-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
      <div
        v-if="isMarkdownContent(message.content)"
        class="text-sm text-slate-700 report-markdown"
        v-html="renderMarkdown(message.content)"
      />
      <p v-else class="text-sm text-slate-700">{{ message.content }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { marked } from 'marked'
import type { AssistantTextMessage as TextMessage } from '@/module/agent/types/chatMessage'

defineProps<{
  message: TextMessage
}>()

function isMarkdownContent(content: string) {
  if (!content) return false
  return content.includes('# ') || content.includes('## ') || content.includes('| :--- |')
}

function renderMarkdown(content: string) {
  if (!content) return ''
  return marked.parse(content, { breaks: true })
}
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.report-markdown :deep(p) {
  margin: 0.5rem 0;
  line-height: 1.7;
}

.report-markdown :deep(ul),
.report-markdown :deep(ol) {
  padding-left: 1.25rem;
  margin: 0.5rem 0;
}

.report-markdown :deep(code) {
  background: #f1f5f9;
  padding: 0.1rem 0.35rem;
  border-radius: 0.35rem;
  font-size: 0.9em;
}
</style>
