<template>
  <Transition
    name="slide"
    @after-leave="onAfterLeave"
  >
    <div
      v-if="visible"
      class="fixed inset-y-0 right-0 w-full max-w-lg bg-white shadow-2xl z-50 flex flex-col"
    >
      <!-- Header -->
      <div class="flex items-center justify-between px-6 py-4 border-b border-slate-200">
        <div class="flex items-center gap-3">
          <button
            @click="handleClose"
            class="p-2 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <ArrowLeft :size="20" class="text-slate-600" />
          </button>
          <h2 class="text-lg font-semibold text-slate-900">结构化分析报告</h2>
        </div>
        <button
          @click="handleDownload"
          class="p-2 hover:bg-slate-100 rounded-lg transition-colors"
        >
          <Download :size="20" class="text-slate-600" />
        </button>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto">
        <div class="p-6 space-y-6" v-if="report">
          <!-- Risk Level Badge -->
          <div class="flex items-center gap-3">
            <span
              class="inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium"
              :class="riskLevelClass"
            >
              <component :is="riskLevelIcon" :size="18" />
              {{ riskLevelText }}
            </span>
            <span class="text-sm text-slate-500">风险等级</span>
          </div>

          <!-- Overall Score -->
          <div class="bg-slate-50 rounded-xl p-6">
            <div class="text-center">
              <div class="text-4xl font-bold text-slate-900 mb-1">{{ report.score }}</div>
              <div class="text-sm text-slate-500">综合评分</div>
            </div>
          </div>

          <!-- Result Summary -->
          <div class="bg-white border border-slate-200 rounded-xl p-5">
            <h3 class="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
              <FileText :size="16" class="text-blue-500" />
              结果摘要
            </h3>
            <p class="text-sm text-slate-600 leading-relaxed">{{ report.summary }}</p>
          </div>

          <!-- Management Summary -->
          <div class="bg-white border border-slate-200 rounded-xl p-5">
            <h3 class="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
              <Building2 :size="16" class="text-indigo-500" />
              管理摘要
            </h3>
            <ul class="space-y-2">
              <li
                v-for="(item, index) in report.managementSummary"
                :key="index"
                class="flex items-start gap-2 text-sm text-slate-600"
              >
                <CheckCircle2 :size="16" class="text-emerald-500 mt-0.5 flex-shrink-0" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- Recommended Actions -->
          <div class="bg-white border border-slate-200 rounded-xl p-5">
            <h3 class="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
              <Lightbulb :size="16" class="text-amber-500" />
              建议动作
            </h3>
            <ul class="space-y-2">
              <li
                v-for="(action, index) in report.recommendedActions"
                :key="index"
                class="flex items-start gap-2 text-sm text-slate-600"
              >
                <ArrowRight :size="16" class="text-blue-500 mt-0.5 flex-shrink-0" />
                <span>{{ action }}</span>
              </li>
            </ul>
          </div>

          <!-- Execution Steps -->
          <div class="bg-white border border-slate-200 rounded-xl p-5">
            <h3 class="text-sm font-semibold text-slate-900 mb-4 flex items-center gap-2">
              <ListTodo :size="16" class="text-purple-500" />
              执行步骤
            </h3>
            <div class="relative">
              <div class="absolute left-2 top-2 bottom-2 w-0.5 bg-slate-200"></div>
              <div class="space-y-4">
                <div
                  v-for="(step, index) in report.executionSteps"
                  :key="index"
                  class="relative flex items-start gap-3 pl-6"
                >
                  <div
                    class="absolute left-0 top-1 w-4 h-4 rounded-full flex items-center justify-center"
                    :class="step.status === 'completed' ? 'bg-emerald-500' : 'bg-slate-300'"
                  >
                    <Check :size="10" class="text-white" v-if="step.status === 'completed'" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <p class="text-sm font-medium text-slate-700">{{ step.title }}</p>
                    <p class="text-xs text-slate-400 mt-0.5">{{ step.description }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>

  <!-- Backdrop -->
  <Transition name="fade">
    <div
      v-if="visible"
      class="fixed inset-0 bg-black/20 z-40"
      @click="handleClose"
    ></div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue'
import {
  ArrowLeft,
  Download,
  FileText,
  Building2,
  Lightbulb,
  ListTodo,
  Check,
  CheckCircle2,
  ArrowRight,
  AlertTriangle,
  AlertCircle,
  ShieldCheck
} from 'lucide-vue-next'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  report: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close', 'download'])

const riskLevelClass = computed(() => {
  if (!props.report) return ''
  switch (props.report.riskLevel) {
    case 'high':
      return 'bg-red-50 text-red-600'
    case 'medium':
      return 'bg-amber-50 text-amber-600'
    case 'low':
    default:
      return 'bg-emerald-50 text-emerald-600'
  }
})

const riskLevelText = computed(() => {
  if (!props.report) return ''
  switch (props.report.riskLevel) {
    case 'high':
      return '高风险'
    case 'medium':
      return '中风险'
    case 'low':
    default:
      return '低风险'
  }
})

const riskLevelIcon = computed(() => {
  if (!props.report) return ShieldCheck
  switch (props.report.riskLevel) {
    case 'high':
      return AlertTriangle
    case 'medium':
      return AlertCircle
    case 'low':
    default:
      return ShieldCheck
  }
})

const handleClose = () => {
  emit('close')
}

const handleDownload = () => {
  emit('download', props.report)
}

const onAfterLeave = () => {
  // Cleanup after animation completes
}
</script>

<style scoped>
.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
