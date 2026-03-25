<template>
  <div class="flex gap-4 justify-start animate-fadeIn">
    <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white flex items-center justify-center flex-shrink-0 mt-1 shadow-sm">
      <Bot size="16" />
    </div>
    <div class="max-w-[85%] sm:max-w-[75%] flex flex-col gap-3">
      <span class="text-[10px] text-slate-400 font-semibold mb-1 px-1">
        Drug-Agent · {{ formatTime(message.createdAt) }}
      </span>

      <!-- Text Content -->
      <div class="bg-transparent text-slate-800 text-[14px] leading-relaxed">
        {{ message.content }}
      </div>

      <!-- Result Card -->
      <div
        v-if="message.result"
        @click="$emit('click-card', message.result)"
        class="bg-white border rounded-2xl p-4 shadow-sm w-full cursor-pointer transition-all hover:shadow-md"
        :class="isSelected
          ? 'border-blue-400 ring-2 ring-blue-50'
          : 'border-slate-200 hover:border-blue-300'"
      >
        <div class="flex justify-between items-center mb-3">
          <div class="flex items-center gap-2">
            <span class="px-2.5 py-1 bg-slate-100 text-slate-600 text-[10px] font-bold uppercase rounded-md tracking-wider">
              {{ message.result.scene }}
            </span>
            <span class="text-[10px] text-slate-400 font-mono">ID: {{ message.result.traceId.split('-')[1] }}</span>
          </div>
          <div :class="[
            'px-2.5 py-1 rounded-md text-[10px] font-bold uppercase border flex items-center gap-1',
            riskConfig.bg,
            riskConfig.color,
            riskConfig.border
          ]">
            <component :is="riskConfig.iconComponent" size="12" />
            {{ riskConfig.label }}
          </div>
        </div>

        <p class="text-sm text-slate-700 font-medium mb-4 line-clamp-2">
          {{ message.result.summary }}
        </p>

        <div class="flex items-center justify-between pt-3 border-t border-slate-100">
          <div class="flex gap-4">
            <div class="flex flex-col">
              <span class="text-[10px] text-slate-400">综合评分</span>
              <span class="text-xs font-bold text-slate-700">{{ message.result.score }} 分</span>
            </div>
            <div class="flex flex-col">
              <span class="text-[10px] text-slate-400">处理文档</span>
              <span class="text-xs font-bold text-slate-700">{{ message.result.docCount || 0 }} 份</span>
            </div>
          </div>
          <button class="text-xs font-bold text-blue-600 flex items-center gap-1 bg-blue-50 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors">
            查看详细报告 <ChevronRight size="14" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChevronRight, AlertOctagon, AlertTriangle, CheckCircle2, Info, Bot } from 'lucide-vue-next'
import { computed } from 'vue'
import type { AssistantResultCardMessage, ReportSummary } from '@/store/agent/types'
import { getRiskConfig } from '@/store/agent/types'

const props = defineProps<{
  message: AssistantResultCardMessage
  isSelected?: boolean
}>()

defineEmits<{
  'click-card': [result: ReportSummary]
}>()

function formatTime(isoString: string): string {
  const date = new Date(isoString)
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

const riskConfig = computed(() => {
  const config = getRiskConfig(props.message.result.riskLevel)
  const iconMap: Record<string, any> = {
    'AlertOctagon': AlertOctagon,
    'AlertTriangle': AlertTriangle,
    'CheckCircle2': CheckCircle2,
    'Info': Info
  }
  return {
    ...config,
    iconComponent: iconMap[config.icon] || Info
  }
})
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
