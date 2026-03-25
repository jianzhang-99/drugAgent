<template>
  <div class="flex-1 overflow-y-auto p-4 sm:p-8 space-y-6 pb-32" ref="scrollContainerRef">
    <!-- Error Banner -->
    <div v-if="hasError" class="bg-red-50 border border-red-200 rounded-xl p-4 mb-6 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <AlertOctagon size="18" class="text-red-500" />
        <span class="text-sm text-red-700">{{ errorMessage }}</span>
      </div>
      <button @click="retry" class="text-xs font-bold text-red-600 hover:text-red-800 flex items-center gap-1">
        <RefreshCw size="12" />
        重试
      </button>
    </div>

    <!-- Empty State -->
    <div v-if="!hasActiveSession && !hasError" class="flex flex-col items-center justify-center min-h-[60vh] animate-in fade-in slide-in-from-bottom-4">
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
        @quick-reply="$emit('quick-reply', $event)"
      />
    </template>

    <!-- Scroll anchor -->
    <div ref="messagesEndRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { Sparkles, FileText, ShieldCheck, AlertTriangle, AlertOctagon, RefreshCw } from 'lucide-vue-next'
import MessageRenderer from './MessageRenderer.vue'
import type { ChatMessage, ReportSummary } from '@/store/agent/types'

const props = defineProps<{
  messages: ChatMessage[]
  hasActiveSession: boolean
  selectedReport: ReportSummary | null
  error?: string | null
}>()

const emit = defineEmits<{
  'quick-action': [prompt: string]
  'select-report': [result: ReportSummary]
  'quick-reply': [reply: string]
  retry: []
}>()

const messagesEndRef = ref<HTMLElement>()
const scrollContainerRef = ref<HTMLElement>()

const hasError = computed(() => !!props.error)
const errorMessage = computed(() => props.error || '')

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

// Store current scroll position before drawer opens
const scrollPosition = ref(0)

const saveScrollPosition = () => {
  if (scrollContainerRef.value) {
    scrollPosition.value = scrollContainerRef.value.scrollTop
  }
}

const restoreScrollPosition = () => {
  nextTick(() => {
    if (scrollContainerRef.value) {
      scrollContainerRef.value.scrollTop = scrollPosition.value
    }
  })
}

// Auto-scroll to bottom when messages change
const scrollToBottom = () => {
  nextTick(() => {
    messagesEndRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

// Scroll to top (for session switching)
const scrollToTop = () => {
  nextTick(() => {
    scrollContainerRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
  })
}

const retry = () => {
  emit('retry')
}

defineExpose({
  scrollToBottom,
  scrollToTop,
  saveScrollPosition,
  restoreScrollPosition
})
</script>
