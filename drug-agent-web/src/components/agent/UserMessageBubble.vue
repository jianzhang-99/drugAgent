<template>
  <div class="flex gap-4 justify-end animate-fadeIn">
    <div class="flex flex-col items-end gap-2">
      <span class="text-[10px] text-slate-400 font-semibold mb-1 px-1">
        您 · {{ formatTime(message.createdAt) }}
      </span>
      <div class="bg-slate-900 text-white px-5 py-3.5 rounded-2xl rounded-tr-sm text-[14px] leading-relaxed shadow-sm">
        {{ message.content }}
      </div>
      <!-- Attachments -->
      <div v-if="message.attachments?.length" class="flex flex-wrap justify-end gap-2 mt-1">
        <div
          v-for="(att, i) in message.attachments"
          :key="i"
          class="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg shadow-sm text-xs text-slate-600"
        >
          <FileText size="12" class="text-blue-500" />
          {{ att }}
        </div>
      </div>
    </div>
    <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-slate-600 text-xs font-bold flex-shrink-0">
      U
    </div>
  </div>
</template>

<script setup lang="ts">
import { FileText } from 'lucide-vue-next'
import type { UserTextMessage } from '@/store/agent/types'

defineProps<{
  message: UserTextMessage
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
