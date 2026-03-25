<template>
  <div class="assistant-clarify-message flex gap-4 justify-start animate-fade-in">
    <!-- AI 头像 -->
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
      DA
    </div>

    <!-- 澄清消息卡片 -->
    <div class="max-w-2xl bg-amber-50 border border-amber-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
      <div class="flex items-start gap-2">
        <HelpCircle class="w-4 h-4 text-amber-600 mt-0.5 flex-shrink-0" />
        <div class="text-sm text-amber-800">
          {{ message.content }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { HelpCircle } from 'lucide-vue-next'
import type { AssistantClarifyMessage as ClarifyMessage } from '@/module/agent/types/chatMessage'

defineProps<{
  message: ClarifyMessage
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
