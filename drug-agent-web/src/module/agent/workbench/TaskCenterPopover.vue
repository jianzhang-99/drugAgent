<template>
  <div class="absolute right-0 mt-2 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden z-50">
    <!-- 头部 -->
    <div class="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
      <h3 class="font-bold text-sm">后台任务中心</h3>
      <button
        class="p-1 hover:bg-slate-100 rounded transition-colors"
        @click="$emit('close')"
      >
        <X size="16" class="text-slate-400" />
      </button>
    </div>

    <!-- 任务列表 -->
    <div class="max-h-[400px] overflow-y-auto">
      <div
        v-for="task in tasks"
        :key="task.id"
        class="p-4 border-b border-slate-50 hover:bg-slate-50 transition-all group cursor-pointer"
        @click="$emit('task-click', task)"
      >
        <!-- 任务基本信息 -->
        <div class="flex justify-between items-start mb-2">
          <div class="flex flex-col">
            <span class="text-[13px] font-bold text-slate-800 leading-tight">
              {{ task.name }}
            </span>
            <span class="text-[10px] text-slate-400 mt-0.5">
              {{ task.scene }}
            </span>
          </div>

          <!-- 状态图标 -->
          <CheckCircle2
            v-if="task.status === 'completed'"
            size="14"
            class="text-emerald-500"
          />
          <span v-else class="text-[10px] font-bold text-blue-600">
            {{ task.progress }}%
          </span>
        </div>

        <!-- 进度条 -->
        <div class="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
          <div
            class="h-full transition-all duration-500"
            :class="task.status === 'completed' ? 'bg-emerald-500' : 'bg-blue-600 animate-pulse'"
            :style="{ width: `${task.progress}%` }"
          />
        </div>

        <!-- 时间 -->
        <div class="mt-2 text-[10px] text-slate-400">
          {{ formatTime(task.updatedAt) }}
        </div>
      </div>

      <!-- 空状态 -->
      <div
        v-if="tasks.length === 0"
        class="p-8 text-center text-slate-400"
      >
        <Activity size="32" class="mx-auto mb-2 opacity-50" />
        <p class="text-sm">暂无运行中的任务</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { X, CheckCircle2, Activity } from 'lucide-vue-next'
import type { TaskItem } from '@/module/agent/types/chatMessage'

defineProps<{
  tasks: TaskItem[]
}>()

defineEmits<{
  (e: 'close'): void
  (e: 'task-click', task: TaskItem): void
}>()

// 格式化时间
function formatTime(isoString: string) {
  const date = new Date(isoString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`

  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}小时前`

  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}天前`
}
</script>

<style scoped>
/* 样式已在 Tailwind 中定义 */
</style>
