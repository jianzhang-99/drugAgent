<template>
  <div class="flex gap-4 justify-start animate-fadeIn">
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white flex items-center justify-center flex-shrink-0 mt-1 shadow-sm animate-pulse">
      <Bot size="16" />
    </div>
    <div class="bg-slate-100/50 px-5 py-3.5 rounded-2xl rounded-tl-sm text-slate-400 text-[14px] flex items-center gap-2">
      <Loader2 size="16" class="animate-spin" />
      {{ message.content }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { Loader2, Bot } from 'lucide-vue-next'
import type { AssistantProgressMessage } from '@/store/agent/types'

defineProps<{
  message: AssistantProgressMessage
}>()
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
