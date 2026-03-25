<template>
  <div class="relative">
    <button
      @click="$emit('toggle')"
      class="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all"
      :class="{
        'bg-amber-50 text-amber-700 ring-1 ring-amber-200 shadow-sm': hasRunningTasks,
        'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200 shadow-sm': hasFailedTasks,
        'bg-slate-50 text-slate-600 hover:bg-slate-100': !hasRunningTasks && !hasFailedTasks
      }"
    >
      <Loader2 v-if="hasRunningTasks" size="14" class="animate-spin" />
      <AlertCircle v-else-if="hasFailedTasks" size="14" class="text-rose-500" />
      <Activity v-else size="14" />
      <span v-if="failedTaskCount > 0">{{ failedTaskCount }} 失败任务</span>
      <span v-else-if="runningTaskCount > 0">{{ runningTaskCount }} 活跃任务</span>
      <span v-else>任务中心</span>
    </button>

    <div
      v-if="isOpen"
      class="absolute right-0 mt-2 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden z-50 animate-in fade-in slide-in-from-top-2"
    >
      <!-- Header -->
      <div class="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
        <h3 class="font-bold text-sm">后台任务中心</h3>
        <button @click="$emit('close')">
          <X size="16" class="text-slate-400 hover:text-slate-700" />
        </button>
      </div>

      <!-- Task List -->
      <div class="max-h-[400px] overflow-y-auto">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="p-4 border-b border-slate-50 hover:bg-slate-50 transition-all group cursor-pointer"
          :class="{
            'bg-red-50/50 hover:bg-red-50': task.status === 'failed',
            'hover:bg-slate-50': task.status !== 'failed'
          }"
          @click="$emit('select-task', task)"
        >
          <div class="flex justify-between items-start mb-2">
            <div class="flex flex-col flex-1 min-w-0">
              <span class="text-[13px] font-bold text-slate-800 leading-tight truncate">{{ task.name }}</span>
              <div class="flex items-center gap-2 mt-0.5">
                <!-- Status Badge -->
                <span
                  class="text-[10px] font-bold px-1.5 py-0.5 rounded"
                  :class="{
                    'bg-amber-100 text-amber-700': task.status === 'pending',
                    'bg-blue-100 text-blue-700': task.status === 'running',
                    'bg-emerald-100 text-emerald-700': task.status === 'completed',
                    'bg-red-100 text-red-700': task.status === 'failed'
                  }"
                >
                  {{ statusLabels[task.status] }}
                </span>
                <!-- Scene Tag -->
                <span class="text-[10px] text-slate-400">{{ getSceneLabel(task.scene) }}</span>
              </div>
            </div>
            <!-- Status Icon -->
            <CheckCircle2 v-if="task.status === 'completed'" size="16" class="text-emerald-500 flex-shrink-0 ml-2" />
            <XCircle v-else-if="task.status === 'failed'" size="16" class="text-red-500 flex-shrink-0 ml-2" />
            <Loader2 v-else-if="task.status === 'running'" size="14" class="text-blue-500 flex-shrink-0 ml-2 animate-spin" />
            <Clock v-else size="14" class="text-slate-400 flex-shrink-0 ml-2" />
          </div>

          <!-- Progress Bar (only for pending/running) -->
          <div v-if="task.status === 'pending' || task.status === 'running'" class="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
            <div
              class="h-full transition-all duration-500 bg-blue-600 animate-pulse"
              :style="{ width: `${task.progress}%` }"
            />
          </div>

          <!-- Error Message (for failed tasks) -->
          <p v-if="task.status === 'failed'" class="text-xs text-red-600 mt-2 flex items-center gap-1">
            <AlertCircle size="12" />
            执行失败，请点击重试
          </p>

          <!-- Task traceId hint -->
          <p v-if="task.traceId" class="text-[10px] text-slate-400 mt-1 font-mono">
            ID: {{ task.traceId.split('-')[1] || task.traceId }}
          </p>
        </div>

        <!-- Empty State -->
        <div v-if="tasks.length === 0" class="p-8 text-center text-slate-400 text-sm">
          <Activity size="32" class="mx-auto mb-2 opacity-50" />
          <p>暂无后台任务</p>
          <p class="text-xs mt-1">发送消息后将自动创建任务</p>
        </div>
      </div>

      <!-- Footer with clear completed -->
      <div v-if="tasks.some(t => t.status === 'completed')" class="p-3 border-t border-slate-100 bg-slate-50/50">
        <button
          @click="clearCompleted"
          class="w-full text-xs text-slate-500 hover:text-slate-700 transition-colors"
        >
          清除已完成任务
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Activity, Loader2, CheckCircle2, X, XCircle, Clock, AlertCircle } from 'lucide-vue-next'
import type { TaskItem } from '@/store/agent/types'
import { getSceneLabel } from '@/store/agent/types'

const props = defineProps<{
  tasks: TaskItem[]
  isOpen: boolean
}>()

const emit = defineEmits<{
  toggle: []
  close: []
  'select-task': [task: TaskItem]
  'clear-completed': []
}>()

const statusLabels: Record<string, string> = {
  'pending': '等待中',
  'running': '执行中',
  'completed': '已完成',
  'failed': '失败'
}

const runningTaskCount = computed(() => props.tasks.filter(t => t.status === 'running').length)
const hasRunningTasks = computed(() => runningTaskCount.value > 0)
const failedTaskCount = computed(() => props.tasks.filter(t => t.status === 'failed').length)
const hasFailedTasks = computed(() => failedTaskCount.value > 0)

const clearCompleted = () => {
  emit('clear-completed')
}
</script>

<style scoped>
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-in {
  animation: fadeIn 0.2s ease-out;
}
</style>
