<template>
  <div class="flex gap-4 justify-start animate-fadeIn">
    <div class="w-8 h-8 rounded-full bg-red-100 text-red-600 flex items-center justify-center flex-shrink-0 mt-1">
      <AlertOctagon size="16" />
    </div>
    <div class="max-w-[85%] sm:max-w-[75%]">
      <span class="text-[10px] text-slate-400 font-semibold mb-1 px-1 block">
        系统 · {{ formatTime(message.createdAt) }}
      </span>
      <div class="bg-red-50 border border-red-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
        <div class="flex items-center gap-2 mb-2">
          <AlertOctagon size="14" class="text-red-600" />
          <span class="text-xs font-bold text-red-700">错误</span>
        </div>
        <p class="text-sm text-slate-700">{{ message.content }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { AlertOctagon } from 'lucide-vue-next'
import type { SystemErrorMessage } from '@/store/agent/types'

defineProps<{
  message: SystemErrorMessage
}>()

function formatTime(isoString: string): string {
  const date = new Date(isoString)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
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

.animate-fadeIn {
  animation: fadeIn 0.3s ease-out;
}
</style>
