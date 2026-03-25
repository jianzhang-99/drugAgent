<template>
  <div class="user-message-bubble flex gap-4 justify-end animate-fade-in">
    <div class="max-w-md">
      <!-- 消息内容 -->
      <div class="bg-indigo-600 text-white rounded-xl rounded-br-md px-5 py-3 shadow-sm">
        <p class="text-sm">{{ message.content }}</p>
      </div>

      <!-- 附件列表 -->
      <div v-if="message.attachments?.length" class="flex flex-wrap justify-end gap-2 mt-2">
        <span
          v-for="(file, idx) in message.attachments"
          :key="idx"
          class="px-2 py-1 bg-slate-100 text-slate-600 text-xs rounded-lg flex items-center gap-1"
        >
          <FileText class="w-3.5 h-3.5" />
          {{ truncateFileName(file) }}
        </span>
      </div>
    </div>

    <!-- 用户头像 -->
    <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-slate-600 text-xs font-bold flex-shrink-0">
      U
    </div>
  </div>
</template>

<script setup lang="ts">
import { FileText } from 'lucide-vue-next'
import type { UserMessage } from '@/module/agent/types/chatMessage'

defineProps<{
  message: UserMessage
}>()

function truncateFileName(name: string, maxLength: number = 20) {
  if (name.length <= maxLength) return name
  const ext = name.split('.').pop() || ''
  const baseName = name.slice(0, -(ext.length + 1))
  return baseName.slice(0, maxLength - ext.length - 4) + '...' + ext
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
</style>
