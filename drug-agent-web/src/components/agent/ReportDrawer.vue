<template>
  <div
    v-if="report"
    class="w-1/3 bg-slate-50 border-l border-slate-200 flex flex-col animate-slideIn shadow-[-12px_0_24px_-12px_rgba(0,0,0,0.05)] z-20 relative"
  >
    <!-- Header -->
    <div class="h-14 border-b border-slate-200 px-4 flex items-center justify-between bg-white shrink-0 sticky top-0 z-10">
      <div class="flex items-center gap-2 text-slate-800">
        <button @click="$emit('close')" class="p-1.5 hover:bg-slate-100 rounded-md transition-colors mr-1">
          <ChevronLeft size="18" />
        </button>
        <FileText size="16" class="text-blue-600" />
        <h3 class="font-bold text-sm">结构化分析报告</h3>
      </div>
      <button class="p-1.5 text-slate-400 hover:text-slate-800 transition-colors">
        <Download size="16" />
      </button>
    </div>

    <!-- Content -->
    <div class="flex-1 overflow-y-auto p-5 space-y-6">
      <!-- Risk Level & Score -->
      <div class="flex items-center justify-between bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
        <div>
          <p class="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">风险定级</p>
          <div :class="['flex items-center gap-1.5 text-lg font-black', riskConfig.color]">
            <component :is="riskConfig.iconComponent" size="20" />
            {{ riskConfig.label }}
          </div>
        </div>
        <div class="text-right">
          <p class="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">综合分值</p>
          <span class="text-xl font-black text-slate-800">{{ report.score }}</span>
        </div>
      </div>

      <!-- Result Summary -->
      <section class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <Activity size="14" class="text-blue-600"/>
          结果摘要 (Agent Output)
        </h4>
        <p class="text-sm text-slate-600 leading-relaxed font-medium">
          Reviewed {{ report.docCount }} documents,
          retained {{ report.riskLevel === 'High' ? 'multiple' : '0' }} high-risk hits.
          Overall risk={{ report.riskLevel.toUpperCase() }}, score={{ report.score }}.
          Top focus: {{ report.summary }}
        </p>
      </section>

      <!-- Management Summary -->
      <section v-if="detail?.managementSummary?.length" class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <Network size="14" class="text-indigo-600"/>
          管理摘要
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(point, i) in detail.managementSummary"
            :key="i"
            class="flex gap-2 text-sm text-slate-600 leading-relaxed"
          >
            <span class="text-slate-400 mt-1.5 text-[10px] font-black">*</span>
            {{ point }}
          </li>
        </ul>
      </section>

      <!-- Suggested Actions -->
      <section v-if="detail?.suggestedActions?.length" class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm border-l-4 border-l-blue-500">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <CheckSquare size="14" class="text-blue-600"/>
          建议动作
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(action, i) in detail.suggestedActions"
            :key="i"
            class="flex gap-2 text-sm text-slate-700 font-medium"
          >
            <span class="text-blue-400 mt-1.5 text-[10px] font-black">*</span>
            {{ action }}
          </li>
        </ul>
      </section>

      <!-- Execution Steps -->
      <section v-if="detail?.steps?.length" class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-4 flex items-center gap-2">
          <Terminal size="14" class="text-slate-600"/>
          执行步骤 (Execution Trace)
        </h4>
        <div class="space-y-0 relative before:absolute before:inset-y-0 before:left-[7px] before:w-px before:bg-slate-200 pl-1">
          <div
            v-for="(step, idx) in detail.steps"
            :key="idx"
            class="relative pl-6 pb-4 last:pb-0"
          >
            <div class="absolute left-0 top-1 w-[14px] h-[14px] rounded-full border-2 border-white bg-slate-300 z-10"></div>
            <span class="text-[13px] font-mono text-slate-600">{{ step }}</span>
          </div>
        </div>
      </section>

      <!-- Evidence List -->
      <section v-if="detail?.evidenceList?.length" class="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
        <h4 class="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
          <FileText size="14" class="text-amber-600"/>
          关键证据
        </h4>
        <ul class="space-y-2">
          <li
            v-for="(evidence, i) in detail.evidenceList"
            :key="i"
            class="flex gap-2 text-xs text-slate-600 leading-relaxed"
          >
            <span class="text-amber-400 flex-shrink-0">*</span>
            {{ evidence }}
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ChevronLeft,
  Download,
  FileText,
  Activity,
  Network,
  CheckSquare,
  Terminal,
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Info
} from 'lucide-vue-next'
import type { ReportSummary, ReportDetail } from '@/store/agent/types'
import { getRiskConfig } from '@/store/agent/types'
import { mockService } from './mockService'

const props = defineProps<{
  report: ReportSummary | null
}>()

defineEmits<{
  close: []
}>()

const detail = computed((): ReportDetail | null => {
  if (!props.report) return null
  return mockService.getReportDetail(props.report.traceId)
})

const riskConfig = computed(() => {
  if (!props.report) {
    return { color: 'text-slate-600', iconComponent: Info, label: '信息' }
  }
  const config = getRiskConfig(props.report.riskLevel)
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

.animate-slideIn {
  animation: slideIn 0.3s ease-out;
}
</style>
