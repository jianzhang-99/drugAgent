<template>
  <div
    @click="handleClick"
    class="bg-white border-2 rounded-xl p-4 cursor-pointer transition-all duration-200 hover:shadow-md"
    :class="[
      selected
        ? 'border-blue-400 ring-2 ring-blue-50'
        : 'border-slate-200 hover:border-slate-300'
    ]"
  >
    <!-- Header -->
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center gap-2">
        <span class="px-2 py-0.5 text-xs font-medium bg-slate-100 text-slate-600 rounded">
          {{ report.scene }}
        </span>
        <span class="text-xs text-slate-400 font-mono">{{ report.traceId }}</span>
      </div>
      <span
        class="px-2 py-0.5 text-xs font-medium rounded"
        :class="riskLevelClass"
      >
        {{ riskLevelText }}
      </span>
    </div>

    <!-- Summary -->
    <p class="text-sm text-slate-700 line-clamp-2 mb-3">
      {{ report.summary }}
    </p>

    <!-- Footer -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-1">
          <Star :size="14" class="text-amber-500" />
          <span class="text-sm font-medium text-slate-700">{{ report.score }}</span>
          <span class="text-xs text-slate-400">分</span>
        </div>
        <div class="flex items-center gap-1 text-slate-400">
          <FileText :size="14" />
          <span class="text-xs">{{ report.docCount }} 份文档</span>
        </div>
      </div>
      <button class="text-xs text-blue-600 hover:text-blue-700 font-medium">
        查看详细报告
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Star, FileText } from 'lucide-vue-next'

const props = defineProps({
  report: {
    type: Object,
    required: true
  },
  selected: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click'])

const riskLevelClass = computed(() => {
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

const handleClick = () => {
  emit('click', props.report)
}
</script>
