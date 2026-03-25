<template>
  <div class="flex-1 overflow-y-auto p-4 sm:p-8 space-y-6 pb-32">
    <!-- Empty State -->
    <div v-if="!hasActiveSession" class="flex flex-col items-center justify-center min-h-[60vh] animate-in fade-in slide-in-from-bottom-4">
      <div class="w-16 h-16 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl flex items-center justify-center shadow-xl shadow-blue-200 mb-6">
        <Sparkles class="text-white" size="32" />
      </div>
      <h1 class="text-2xl sm:text-3xl font-bold text-slate-800 mb-2">有什么我可以帮您分析的？</h1>
      <p class="text-slate-500 mb-10 text-sm">直接描述您的监管需求，Agent 将自动分发到对应的工作流</p>

      <!-- Quick Actions -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 w-full max-w-4xl">
        <div
          v-for="action in quickActions"
          :key="action.label"
          @click="$emit('quick-action', action.prompt)"
          class="bg-white border border-slate-200 p-4 rounded-2xl cursor-pointer hover:border-blue-300 hover:shadow-md transition-all group"
        >
          <div :class="['w-10 h-10', action.bg, action.color, 'rounded-xl flex items-center justify-center mb-3 group-hover:scale-110 transition-transform']">
            <component :is="action.icon" size="20" />
          </div>
          <h4 class="font-bold text-slate-800 text-sm mb-1">{{ action.label }}</h4>
          <p class="text-xs text-slate-500 line-clamp-2">{{ action.prompt }}</p>
        </div>
      </div>
    </div>

    <!-- Messages -->
    <template v-else>
      <MessageRenderer
        v-for="message in messages"
        :key="message.id"
        :message="message"
        :is-selected="selectedReport?.traceId === message.result?.traceId"
        @click-card="$emit('select-report', $event)"
      />
    </template>

    <!-- Scroll anchor -->
    <div ref="messagesEndRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Sparkles, FileText, ShieldCheck, AlertTriangle } from 'lucide-vue-next'
import MessageRenderer from './MessageRenderer.vue'
import type { ChatMessage, ReportSummary } from '@/store/agent/types'

defineProps<{
  messages: ChatMessage[]
  hasActiveSession: boolean
  selectedReport: ReportSummary | null
}>()

defineEmits<{
  'quick-action': [prompt: string]
  'select-report': [result: ReportSummary]
}>()

const messagesEndRef = ref<HTMLElement>()

const quickActions = [
  {
    icon: FileText,
    label: '标书审查',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    color: 'text-indigo-600',
    bg: 'bg-indigo-50'
  },
  {
    icon: ShieldCheck,
    label: '合同预审',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    color: 'text-emerald-600',
    bg: 'bg-emerald-50'
  },
  {
    icon: AlertTriangle,
    label: '合规预警',
    prompt: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    color: 'text-amber-600',
    bg: 'bg-amber-50'
  }
]

// Auto-scroll to bottom when messages change
const scrollToBottom = () => {
  nextTick(() => {
    messagesEndRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

defineExpose({
  scrollToBottom
})
</script>
