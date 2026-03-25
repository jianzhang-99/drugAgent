<template>
  <div class="system-error-message flex gap-4 justify-start animate-fade-in">
    <!-- 系统图标 -->
    <div class="w-8 h-8 rounded-full bg-red-100 flex items-center justify-center text-red-600 text-xs font-bold flex-shrink-0">
      !
    </div>

    <!-- 错误消息卡片 -->
    <div class="max-w-2xl bg-red-50 border border-red-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
      <div class="flex items-start gap-2">
        <AlertCircle class="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0" />
        <div class="text-sm text-red-700">
          {{ message.content }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AlertCircle } from 'lucide-vue-next'
import type { SystemErrorMessage as ErrorMessage } from '@/module/agent/types/chatMessage'

defineProps<{
  message: ErrorMessage
}>()
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
