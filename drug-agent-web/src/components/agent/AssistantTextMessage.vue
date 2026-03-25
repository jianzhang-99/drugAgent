<template>
  <div class="flex gap-4 justify-start animate-fadeIn">
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white flex items-center justify-center flex-shrink-0 mt-1 shadow-sm">
      <Bot size="16" />
    </div>
    <div class="max-w-[85%] sm:max-w-[75%] bg-transparent text-slate-800 text-[14px] leading-relaxed">
      <span class="text-[10px] text-slate-400 font-semibold mb-1 px-1 block">
        Drug-Agent · {{ formatTime(message.createdAt) }}
      </span>
      <div class="bg-white border border-slate-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
        {{ message.content }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bot } from 'lucide-vue-next'
import type { AssistantTextMessage } from '@/store/agent/types'

defineProps<{
  message: AssistantTextMessage
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
