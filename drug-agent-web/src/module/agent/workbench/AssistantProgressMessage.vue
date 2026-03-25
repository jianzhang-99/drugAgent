<template>
  <div class="assistant-progress-message flex gap-4 justify-start animate-fade-in">
    <!-- AI 头像 -->
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
      DA
    </div>

    <!-- 进度消息卡片 -->
    <div class="max-w-2xl bg-white border border-slate-200 rounded-xl rounded-bl-md px-5 py-4 shadow-sm">
      <div class="space-y-3">
        <!-- 骨架屏效果 -->
        <div class="h-4 bg-slate-100 rounded skeleton-base w-3/4"></div>
        <div class="h-4 bg-slate-100 rounded skeleton-base w-1/2"></div>

        <!-- 加载指示器 -->
        <div class="flex items-center gap-2 mt-3">
          <Loader2 class="w-4 h-4 text-indigo-500 animate-spin" />
          <span class="text-sm text-slate-500">{{ message.content || '正在分析...' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import type { AssistantProgressMessage as ProgressMessage } from '@/module/agent/types/chatMessage'

defineProps<{
  message: ProgressMessage
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

.skeleton-base {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
