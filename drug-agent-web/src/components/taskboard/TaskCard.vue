<template>
  <div
    class="task-card bg-white rounded-xl p-5 shadow-card hover:shadow-card-hover transition-all duration-300 cursor-pointer border border-slate-200 hover:border-slate-300"
    :class="{ 'ring-2 ring-red-200': isHighRisk }"
    @click="$emit('click', task)"
  >
    <!-- Header: 场景标签 + 优先级 -->
    <div class="flex items-center justify-between mb-3">
      <span
        :class="sceneClass"
        class="px-2.5 py-1 rounded-full text-xs font-medium border"
      >
        {{ task.taskTypeText || task.taskType }}
      </span>
      <div class="flex items-center gap-2">
        <span
          v-if="isHighRisk"
          class="flex items-center gap-1 px-2 py-0.5 bg-red-50 text-red-600 text-xs font-medium rounded-full"
        >
          <AlertTriangle class="w-3 h-3" />
          高风险
        </span>
        <span class="text-xs text-slate-400 font-mono">#{{ task.id?.slice(0, 8) }}</span>
      </div>
    </div>

    <!-- Task Name -->
    <h3 class="text-base font-semibold text-slate-800 mb-2 line-clamp-2 leading-snug">
      {{ task.taskName }}
    </h3>

    <!-- Summary -->
    <p v-if="task.summary" class="text-sm text-slate-500 mb-3 line-clamp-2">
      {{ task.summary }}
    </p>

    <!-- Risk Tags -->
    <div v-if="task.riskTags?.length" class="flex flex-wrap gap-1 mb-3">
      <span
        v-for="tag in task.riskTags.slice(0, 3)"
        :key="tag"
        class="px-2 py-0.5 bg-slate-100 text-slate-600 text-xs rounded"
      >
        {{ tag }}
      </span>
      <span v-if="task.riskTags.length > 3" class="text-xs text-slate-400">
        +{{ task.riskTags.length - 3 }}
      </span>
    </div>

    <!-- Progress Section -->
    <div class="space-y-2 mb-3">
      <div class="flex justify-between text-sm">
        <span class="text-slate-500">进度</span>
        <span :class="statusTextClass" class="font-medium">
          {{ task.progress || 0 }}%
        </span>
      </div>

      <!-- Progress Bar -->
      <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
        <div
          :class="progressBarClass"
          :style="{ width: (task.progress || 0) + '%' }"
          class="h-full rounded-full transition-all duration-500"
        />
      </div>

      <!-- Current Step -->
      <div v-if="task.currentStep" class="text-xs text-slate-400 truncate">
        {{ task.currentStep }}
      </div>
    </div>

    <!-- Stats Row -->
    <div class="flex items-center justify-between text-xs text-slate-500 mb-3">
      <div class="flex items-center gap-3">
        <!-- Risk Items -->
        <span
          v-if="task.riskItemCount > 0"
          class="flex items-center gap-1"
          :class="{ 'text-red-500': task.unhandledRiskCount > 0 }"
        >
          <ShieldAlert class="w-3.5 h-3.5" />
          {{ task.riskItemCount }}项风险
          <span v-if="task.unhandledRiskCount > 0" class="text-red-600 font-medium">
            ({{ task.unhandledRiskCount }}待处理)
          </span>
        </span>
        <!-- Hit Rules -->
        <span v-if="task.hitRules > 0" class="flex items-center gap-1">
          <Gavel class="w-3.5 h-3.5" />
          {{ task.hitRules }}条规则
        </span>
      </div>
      <!-- Score -->
      <span
        v-if="task.score != null"
        class="flex items-center gap-1 font-medium"
        :class="scoreClass"
      >
        <Star class="w-3.5 h-3.5" />
        {{ task.score }}分
      </span>
    </div>

    <!-- Footer: Submitter + Time -->
    <div class="flex items-center justify-between pt-3 border-t border-slate-100">
      <div class="flex items-center gap-2">
        <User class="w-3.5 h-3.5 text-slate-400" />
        <span class="text-xs text-slate-500">{{ task.submittedBy || '系统' }}</span>
      </div>
      <div class="flex items-center gap-1 text-xs text-slate-400">
        <Clock class="w-3.5 h-3.5" />
        {{ formatTime(task.createdAt) }}
      </div>
    </div>

    <!-- Action Buttons (shown on hover) -->
    <div
      v-if="availableActions.length"
      class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity"
    >
      <button
        v-for="action in availableActions.slice(0, 2)"
        :key="action"
        :class="getActionButtonClass(action)"
        class="p-1.5 rounded-lg text-white text-xs font-medium hover:opacity-80 transition-opacity"
        @click.stop="handleAction(action)"
      >
        {{ getActionLabel(action) }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  AlertTriangle,
  ShieldAlert,
  Gavel,
  Star,
  User,
  Clock
} from 'lucide-vue-next'
import { formatTime } from '@/utils/timeFormat'

const props = defineProps({
  task: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click', 'action'])

// ==================== 计算属性 ====================

// 是否高风险
const isHighRisk = computed(() => {
  return props.task.riskLevel === 'HIGH' && props.task.status !== 'COMPLETED'
})

// 场景样式
const sceneClass = computed(() => {
  const classMap = {
    TENDER_REVIEW: 'bg-amber-100 text-amber-700 border-amber-200',
    CONTRACT_CHECK: 'bg-indigo-100 text-indigo-700 border-indigo-200',
    COMPLIANCE_ALERT: 'bg-purple-100 text-purple-700 border-purple-200'
  }
  return classMap[props.task.taskType] || 'bg-slate-100 text-slate-700 border-slate-200'
})

// 状态文字颜色
const statusTextClass = computed(() => {
  const classMap = {
    PENDING: 'text-slate-400',
    PARSING: 'text-blue-500',
    PARSED: 'text-blue-600',
    RUNNING: 'text-blue-600',
    COMPLETED: 'text-emerald-600',
    FAILED: 'text-red-600'
  }
  return classMap[props.task.status] || 'text-slate-400'
})

// 进度条颜色
const progressBarClass = computed(() => {
  const classMap = {
    PENDING: 'bg-slate-300',
    PARSING: 'bg-blue-400 animate-pulse',
    PARSED: 'bg-blue-500',
    RUNNING: 'bg-blue-500 animate-pulse',
    COMPLETED: 'bg-emerald-500',
    FAILED: 'bg-red-500'
  }
  return classMap[props.task.status] || 'bg-slate-300'
})

// 评分颜色
const scoreClass = computed(() => {
  const score = props.task.score
  if (score == null) return 'text-slate-400'
  if (score >= 90) return 'text-emerald-600'
  if (score >= 70) return 'text-blue-600'
  if (score >= 60) return 'text-amber-600'
  return 'text-red-600'
})

// 可用操作
const availableActions = computed(() => {
  return props.task.availableActions || []
})

// ==================== 方法 ====================

const getActionLabel = (action) => {
  const labelMap = {
    START: '开始',
    VIEW: '查看',
    CANCEL: '取消',
    EXPORT: '导出',
    ARCHIVE: '归档',
    RETRY: '重试',
    DELETE: '删除'
  }
  return labelMap[action] || action
}

const getActionButtonClass = (action) => {
  const classMap = {
    START: 'bg-blue-500',
    VIEW: 'bg-slate-500',
    CANCEL: 'bg-amber-500',
    EXPORT: 'bg-green-500',
    ARCHIVE: 'bg-slate-400',
    RETRY: 'bg-blue-500',
    DELETE: 'bg-red-500'
  }
  return classMap[action] || 'bg-slate-500'
}

const handleAction = (action) => {
  emit('action', { action, task: props.task })
}
</script>

<style scoped>
.task-card {
  position: relative;
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.line-clamp-3 {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.animate-pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.shadow-card {
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1);
}

.shadow-card-hover {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
}
</style>
