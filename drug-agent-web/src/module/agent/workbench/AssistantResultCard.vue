<template>
  <div class="assistant-result-card animate-fade-in">
    <!-- AI 头像 -->
    <div class="flex gap-4 mb-2">
      <div class="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
        DA
      </div>
    </div>

    <!-- 结果卡片 -->
    <div
      class="ml-12 bg-white rounded-xl border shadow-sm cursor-pointer transition-all hover:shadow-md"
      :class="[
        isSelected
          ? 'border-blue-400 ring-2 ring-blue-50'
          : 'border-slate-200 hover:border-blue-300'
      ]"
      @click="handleClick"
    >
      <!-- 风险头部 -->
      <div class="px-6 py-5 bg-slate-50 border-b border-slate-200">
        <div class="flex items-start justify-between gap-4">
          <div class="flex items-center gap-4">
            <!-- 风险等级标签 -->
            <div
              class="px-3 py-1.5 rounded-full text-xs font-bold flex items-center gap-1"
              :class="riskConfig.bg"
            >
              <component :is="riskConfig.icon" class="w-4 h-4" :class="riskConfig.color" />
              <span :class="riskConfig.color">{{ riskConfig.label }}</span>
            </div>

            <!-- 场景和追踪ID -->
            <div class="pt-1">
              <div class="text-xs text-slate-500">{{ sceneLabel }}</div>
              <div class="text-[11px] text-slate-400 font-mono mt-1">{{ message.result.traceId }}</div>
            </div>
          </div>

          <!-- 查看完整报告按钮 -->
          <button
            class="px-3 py-1.5 text-xs font-medium rounded-lg border border-indigo-200 text-indigo-600 bg-indigo-50 hover:bg-indigo-100 transition-colors"
            @click.stop="handleViewReport"
          >
            查看完整报告
          </button>
        </div>

        <!-- 指标网格 -->
        <div class="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3">
          <div
            v-for="metric in summaryMetrics"
            :key="metric.label"
            class="rounded-xl border border-slate-200 bg-white/80 px-4 py-3"
          >
            <div class="text-[11px] text-slate-500">{{ metric.label }}</div>
            <div class="text-sm font-semibold text-slate-800 mt-1">{{ metric.value }}</div>
          </div>
        </div>
      </div>

      <!-- 结果摘要 -->
      <div class="px-6 py-4 border-b border-slate-100">
        <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">结果摘要</h4>
        <p class="text-sm text-slate-700 leading-7">{{ normalizeSummary(message.result.summary) }}</p>
      </div>

      <!-- 关键证据预览 -->
      <div v-if="evidencePreview.length" class="px-6 py-4">
        <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">关键证据</h4>
        <ul class="space-y-2">
          <li
            v-for="(evidence, idx) in evidencePreview"
            :key="idx"
            class="flex gap-2 text-xs text-slate-600"
          >
            <span class="text-indigo-400 flex-shrink-0">•</span>
            <span class="leading-relaxed">{{ evidence }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Info
} from 'lucide-vue-next'
import type { AssistantResultCardMessage } from '@/module/agent/types/chatMessage'
import { RiskLevelConfig } from '@/module/agent/types/chatMessage'

const props = defineProps<{
  message: AssistantResultCardMessage
  isSelected?: boolean
}>()

const emit = defineEmits<{
  (e: 'click', message: AssistantResultCardMessage): void
  (e: 'view-report', message: AssistantResultCardMessage): void
}>()

// 风险配置
const riskConfig = computed(() => {
  const level = props.message.result.riskLevel?.toUpperCase() || 'UNKNOWN'
  return RiskLevelConfig[level] || RiskLevelConfig.UNKNOWN
})

// 场景标签映射
const sceneLabel = computed(() => {
  const scene = props.message.result.scene
  const sceneMap: Record<string, string> = {
    'TENDER': '标书审查',
    'CONTRACT': '合同预审',
    'RISK_ALERT': '合规预警'
  }
  return sceneMap[scene] || '智能审查'
})

// 摘要指标
const summaryMetrics = computed(() => {
  const result = props.message.result
  return [
    { label: '综合风险', value: riskConfig.value.label },
    { label: '参考分值', value: result.score ?? 0 },
    { label: '比对文件', value: `${result.docCount ?? 0} 份` },
    { label: '风险等级', value: result.riskLevel || '未知' }
  ]
})

// 证据预览（取前3条）
const evidencePreview = computed(() => {
  // 这里可以从result中提取evidenceList，如果没有则返回空
  return []
})

// 规范化摘要
function normalizeSummary(summary: string) {
  if (summary === 'No retained high-risk hits after rule scan.') {
    return '规则扫描后未保留高风险命中。'
  }
  return summary || '暂无摘要'
}

// 处理卡片点击
function handleClick() {
  emit('click', props.message)
}

// 处理查看报告按钮
function handleViewReport() {
  emit('view-report', props.message)
}
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
