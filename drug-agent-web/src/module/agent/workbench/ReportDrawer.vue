<template>
  <div class="w-1/3 bg-slate-50 border-l border-slate-200 flex flex-col animate-slide-in shadow-[-12px_0_24px_-12px_rgba(0,0,0,0.05)] z-20 relative">
    <!-- 头部 -->
    <div class="h-14 border-b border-slate-200 px-4 flex items-center justify-between bg-white shrink-0 sticky top-0 z-10">
      <div class="flex items-center gap-2 text-slate-800">
        <button
          class="p-1.5 hover:bg-slate-100 rounded-md transition-colors mr-1"
          @click="$emit('close')"
        >
          <ChevronLeft size="18" />
        </button>
        <FileText size="16" class="text-blue-600" />
        <h3 class="font-bold text-sm">结构化分析报告</h3>
      </div>
      <button class="p-1.5 text-slate-400 hover:text-slate-800 transition-colors">
        <Download size="16" />
      </button>
    </div>

    <!-- 报告内容 -->
    <div class="flex-1 overflow-y-auto p-5 space-y-6">
      <!-- 风险概览 -->
      <div class="flex items-center justify-between bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
        <div>
          <p class="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">风险定级</p>
          <div class="flex items-center gap-1.5 text-lg font-black" :class="riskConfig.color">
            <component :is="riskConfig.icon" size="20" />
            {{ riskConfig.label }}
          </div>
        </div>
        <div class="text-right">
          <p class="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">综合分值</p>
          <span class="text-xl font-black text-slate-800">{{ report?.score || 0 }}</span>
        </div>
      </div>

      <!-- 结果摘要 -->
      <section class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <Activity size="14" class="text-blue-600" />
          结果摘要 (Agent Output)
        </h4>
        <p class="text-sm text-slate-600 leading-relaxed font-medium">
          {{ report?.summary || '暂无摘要' }}
        </p>
      </section>

      <!-- 管理摘要 -->
      <section class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <Network size="14" class="text-indigo-600" />
          管理摘要
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(point, i) in managementSummary"
            :key="i"
            class="flex gap-2 text-sm text-slate-600 leading-relaxed"
          >
            <span class="text-slate-400 mt-1.5 text-[10px] font-black">•</span>
            {{ point }}
          </li>
        </ul>
      </section>

      <!-- 建议动作 -->
      <section class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm border-l-4 border-l-blue-500">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <CheckSquare size="14" class="text-blue-600" />
          建议动作
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(action, i) in suggestedActions"
            :key="i"
            class="flex gap-2 text-sm text-slate-700 font-medium"
          >
            <span class="text-blue-400 mt-1.5 text-[10px] font-black">→</span>
            {{ action }}
          </li>
        </ul>
      </section>

      <!-- 执行步骤 -->
      <section class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-4 flex items-center gap-2">
          <Terminal size="14" class="text-slate-600" />
          执行步骤 (Execution Trace)
        </h4>
        <div class="space-y-0 relative before:absolute before:inset-y-0 before:left-[7px] before:w-px before:bg-slate-200 pl-1">
          <div
            v-for="(step, idx) in executionSteps"
            :key="idx"
            class="relative pl-6 pb-4 last:pb-0"
          >
            <div class="absolute left-0 top-1 w-[14px] h-[14px] rounded-full border-2 border-white bg-slate-300 z-10"></div>
            <span class="text-[13px] font-mono text-slate-600">{{ stepLabelMap[step] || step }}</span>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ChevronLeft,
  FileText,
  Download,
  Activity,
  Network,
  CheckSquare,
  Terminal,
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Info
} from 'lucide-vue-next'
import type { ReportSummary } from '@/module/agent/types/chatMessage'
import { RiskLevelConfig } from '@/module/agent/types/chatMessage'

defineProps<{
  report: ReportSummary | null
}>()

defineEmits<{
  (e: 'close'): void
}>()

// 风险配置
const riskConfig = computed(() => {
  const level = report.value?.riskLevel?.toUpperCase() || 'UNKNOWN'
  return RiskLevelConfig[level] || RiskLevelConfig.UNKNOWN
})

// 管理摘要
const managementSummary = computed(() => {
  // TODO: 从 report 中提取
  return [
    '本次共审查文档，综合风险等级为中等。',
    '建议对重点章节进行抽样复核。'
  ]
})

// 建议动作
const suggestedActions = computed(() => {
  // TODO: 从 report 中提取
  return [
    '保留当前报告作为初筛结果',
    '结合业务经验抽样检查重点章节'
  ]
})

// 执行步骤
const executionSteps = [
  'scene_route',
  'structured_load',
  'rule_hit',
  'false_positive_exemption',
  'risk_fusion',
  'evidence_assembly',
  'report_generation'
]

// 步骤标签映射
const stepLabelMap: Record<string, string> = {
  scene_route: '场景路由',
  structured_load: '结构化加载',
  rule_hit: '规则命中分析',
  false_positive_exemption: '误报豁免',
  risk_fusion: '风险融合',
  evidence_assembly: '证据组装',
  report_generation: '报告生成'
}
</script>

<style scoped>
.animate-slide-in {
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
