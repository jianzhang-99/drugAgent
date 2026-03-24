<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="visible" class="drawer-overlay fixed inset-0 z-50">
        <!-- 遮罩 -->
        <div
          class="absolute inset-0 bg-black/30 backdrop-blur-sm"
          @click="$emit('close')"
        />

        <!-- 抽屉内容 -->
        <div class="drawer-content absolute right-0 top-0 bottom-0 w-full max-w-2xl bg-white shadow-2xl overflow-hidden flex flex-col">
          <!-- Header -->
          <div class="flex items-center justify-between p-6 border-b border-slate-200">
            <div>
              <h2 class="text-lg font-semibold text-slate-800">任务详情</h2>
              <p class="text-sm text-slate-500 mt-1">#{{ task?.id }}</p>
            </div>
            <button
              class="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              @click="$emit('close')"
            >
              <X class="w-5 h-5 text-slate-500" />
            </button>
          </div>

          <!-- Content -->
          <div class="flex-1 overflow-y-auto p-6">
            <!-- 基本信息 -->
            <div class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">基本信息</h3>
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <p class="text-xs text-slate-500 mb-1">任务名称</p>
                  <p class="text-sm text-slate-800 font-medium">{{ task?.taskName }}</p>
                </div>
                <div>
                  <p class="text-xs text-slate-500 mb-1">任务类型</p>
                  <p class="text-sm text-slate-800">{{ task?.taskTypeText }}</p>
                </div>
                <div>
                  <p class="text-xs text-slate-500 mb-1">状态</p>
                  <span :class="statusClass" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium">
                    {{ task?.statusText }}
                  </span>
                </div>
                <div>
                  <p class="text-xs text-slate-500 mb-1">风险等级</p>
                  <span :class="riskLevelClass" class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium">
                    {{ task?.riskLevelText }}
                  </span>
                </div>
                <div>
                  <p class="text-xs text-slate-500 mb-1">提交人</p>
                  <p class="text-sm text-slate-800">{{ task?.submittedBy || '系统' }}</p>
                </div>
                <div>
                  <p class="text-xs text-slate-500 mb-1">提交时间</p>
                  <p class="text-sm text-slate-800">{{ formatDateTime(task?.createdAt) }}</p>
                </div>
              </div>
            </div>

            <!-- 进度 -->
            <div class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">执行进度</h3>
              <div class="space-y-3">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-slate-600">整体进度</span>
                  <span class="text-sm font-medium text-slate-800">{{ task?.progress || 0 }}%</span>
                </div>
                <div class="h-2 bg-slate-100 rounded-full overflow-hidden">
                  <div
                    :class="progressBarClass"
                    :style="{ width: (task?.progress || 0) + '%' }"
                    class="h-full rounded-full transition-all duration-500"
                  />
                </div>
                <div v-if="task?.currentStep" class="text-xs text-slate-500">
                  当前步骤: {{ task.currentStep }}
                </div>
              </div>
            </div>

            <!-- 阶段列表 -->
            <div v-if="task?.phases?.length" class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">执行阶段</h3>
              <div class="space-y-2">
                <div
                  v-for="(phase, index) in task.phases"
                  :key="phase.id"
                  class="flex items-center gap-3 p-3 bg-slate-50 rounded-lg"
                >
                  <div
                    :class="getPhaseStatusClass(phase.status)"
                    class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-medium"
                  >
                    <Check v-if="phase.status === 'COMPLETED'" class="w-4 h-4" />
                    <Loader v-else-if="phase.status === 'RUNNING'" class="w-4 h-4 animate-spin" />
                    <span v-else>{{ index + 1 }}</span>
                  </div>
                  <div class="flex-1">
                    <p class="text-sm font-medium text-slate-800">{{ phase.phaseName }}</p>
                    <p class="text-xs text-slate-500">{{ phase.statusText }}</p>
                  </div>
                  <div class="text-xs text-slate-400">
                    {{ phase.progress }}%
                  </div>
                </div>
              </div>
            </div>

            <!-- 评分 -->
            <div v-if="task?.score != null" class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">综合评分</h3>
              <div class="flex items-center gap-4">
                <div class="text-3xl font-bold" :class="scoreClass">
                  {{ task.score }}
                </div>
                <div class="flex-1">
                  <p class="text-sm font-medium text-slate-800">{{ task.scoreGrade }}</p>
                  <p class="text-xs text-slate-500">满分100分</p>
                </div>
              </div>
            </div>

            <!-- 风险摘要 -->
            <div v-if="task?.riskItemCount > 0" class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">风险摘要</h3>
              <div class="bg-red-50 border border-red-100 rounded-lg p-4">
                <div class="flex items-center gap-2 mb-2">
                  <AlertTriangle class="w-4 h-4 text-red-600" />
                  <span class="text-sm font-medium text-red-800">
                    共{{ task.riskItemCount }}项风险
                  </span>
                  <span v-if="task.unhandledRiskCount > 0" class="text-xs text-red-600">
                    ({{ task.unhandledRiskCount }}待处理)
                  </span>
                </div>
                <p class="text-xs text-red-700">
                  包含 {{ task.hitRules }} 条命中规则
                </p>
              </div>
            </div>

            <!-- 摘要 -->
            <div v-if="task?.summary" class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">任务摘要</h3>
              <p class="text-sm text-slate-600 leading-relaxed">
                {{ task.summary }}
              </p>
            </div>

            <!-- 时间线 -->
            <div class="mb-6">
              <h3 class="text-sm font-semibold text-slate-800 mb-3">时间线</h3>
              <div class="space-y-3">
                <div class="flex items-center gap-3">
                  <div class="w-2 h-2 bg-slate-300 rounded-full" />
                  <div class="flex-1">
                    <p class="text-xs text-slate-500">创建时间</p>
                    <p class="text-sm text-slate-800">{{ formatDateTime(task?.createdAt) }}</p>
                  </div>
                </div>
                <div v-if="task?.startedAt" class="flex items-center gap-3">
                  <div class="w-2 h-2 bg-blue-400 rounded-full" />
                  <div class="flex-1">
                    <p class="text-xs text-slate-500">开始时间</p>
                    <p class="text-sm text-slate-800">{{ formatDateTime(task.startedAt) }}</p>
                  </div>
                </div>
                <div v-if="task?.completedAt" class="flex items-center gap-3">
                  <div class="w-2 h-2 bg-emerald-400 rounded-full" />
                  <div class="flex-1">
                    <p class="text-xs text-slate-500">完成时间</p>
                    <p class="text-sm text-slate-800">{{ formatDateTime(task.completedAt) }}</p>
                  </div>
                </div>
                <div v-if="task?.executionDuration" class="flex items-center gap-3">
                  <div class="w-2 h-2 bg-indigo-400 rounded-full" />
                  <div class="flex-1">
                    <p class="text-xs text-slate-500">执行耗时</p>
                    <p class="text-sm text-slate-800">{{ formatDuration(task.executionDuration) }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-between p-6 border-t border-slate-200 bg-slate-50">
            <div class="flex items-center gap-2">
              <button
                v-for="action in task?.availableActions"
                :key="action"
                :class="getActionButtonClass(action)"
                class="px-4 py-2 text-sm font-medium rounded-lg transition-colors"
                @click="handleAction(action)"
              >
                {{ getActionLabel(action) }}
              </button>
            </div>
            <button
              class="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              @click="$emit('close')"
            >
              关闭
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import {
  X,
  Check,
  Loader,
  AlertTriangle
} from 'lucide-vue-next'

const props = defineProps({
  task: {
    type: Object,
    default: null
  },
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'action'])

// ==================== 计算属性 ====================

const statusClass = computed(() => {
  const map = {
    PENDING: 'bg-slate-100 text-slate-600',
    PARSING: 'bg-blue-100 text-blue-600',
    PARSED: 'bg-blue-100 text-blue-700',
    RUNNING: 'bg-blue-100 text-blue-600',
    COMPLETED: 'bg-emerald-100 text-emerald-700',
    FAILED: 'bg-red-100 text-red-600',
    CANCELLED: 'bg-slate-100 text-slate-500'
  }
  return map[props.task?.status] || 'bg-slate-100 text-slate-600'
})

const riskLevelClass = computed(() => {
  const map = {
    HIGH: 'bg-red-100 text-red-700',
    MEDIUM: 'bg-amber-100 text-amber-700',
    LOW: 'bg-emerald-100 text-emerald-700',
    UNKNOWN: 'bg-slate-100 text-slate-600'
  }
  return map[props.task?.riskLevel] || 'bg-slate-100 text-slate-600'
})

const progressBarClass = computed(() => {
  const map = {
    PENDING: 'bg-slate-300',
    PARSING: 'bg-blue-400 animate-pulse',
    RUNNING: 'bg-blue-500 animate-pulse',
    COMPLETED: 'bg-emerald-500',
    FAILED: 'bg-red-500'
  }
  return map[props.task?.status] || 'bg-slate-300'
})

const scoreClass = computed(() => {
  const score = props.task?.score
  if (score == null) return 'text-slate-400'
  if (score >= 90) return 'text-emerald-600'
  if (score >= 70) return 'text-blue-600'
  if (score >= 60) return 'text-amber-600'
  return 'text-red-600'
})

// ==================== 方法 ====================

const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatDuration = (ms) => {
  if (!ms) return '-'
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  if (hours > 0) {
    return `${hours}小时${minutes % 60}分钟`
  }
  if (minutes > 0) {
    return `${minutes}分钟${seconds % 60}秒`
  }
  return `${seconds}秒`
}

const getPhaseStatusClass = (status) => {
  const map = {
    WAITING: 'bg-slate-100 text-slate-500',
    RUNNING: 'bg-blue-100 text-blue-600',
    COMPLETED: 'bg-emerald-100 text-emerald-600',
    SKIPPED: 'bg-slate-100 text-slate-400',
    FAILED: 'bg-red-100 text-red-600'
  }
  return map[status] || 'bg-slate-100 text-slate-500'
}

const getActionLabel = (action) => {
  const map = {
    START: '开始任务',
    VIEW: '查看详情',
    CANCEL: '取消任务',
    EXPORT: '导出报告',
    ARCHIVE: '归档',
    RETRY: '重试',
    DELETE: '删除'
  }
  return map[action] || action
}

const getActionButtonClass = (action) => {
  const map = {
    START: 'bg-blue-600 text-white hover:bg-blue-700',
    VIEW: 'bg-slate-600 text-white hover:bg-slate-700',
    CANCEL: 'bg-amber-600 text-white hover:bg-amber-700',
    EXPORT: 'bg-emerald-600 text-white hover:bg-emerald-700',
    ARCHIVE: 'bg-slate-500 text-white hover:bg-slate-600',
    RETRY: 'bg-blue-600 text-white hover:bg-blue-700',
    DELETE: 'bg-red-600 text-white hover:bg-red-700'
  }
  return map[action] || 'bg-slate-600 text-white hover:bg-slate-700'
}

const handleAction = (action) => {
  emit('action', { action, task: props.task })
}
</script>

<style scoped>
.drawer-overlay {
  animation: fadeIn 0.2s ease;
}

.drawer-content {
  animation: slideIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.animate-pulse {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
