<template>
  <div class="relative">
    <button
      @click="$emit('toggle')"
      class="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all"
      :class="hasRunningTasks
        ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200 shadow-sm'
        : 'bg-slate-50 text-slate-600 hover:bg-slate-100'"
    >
      <Loader2 v-if="hasRunningTasks" size="14" class="animate-spin" />
      <Activity v-else size="14" />
      <span>{{ runningTaskCount }} 活跃任务</span>
    </button>

    <div
      v-if="isOpen"
      class="absolute right-0 mt-2 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden z-50"
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
          @click="$emit('select-task', task)"
        >
          <div class="flex justify-between items-start mb-2">
            <div class="flex flex-col">
              <span class="text-[13px] font-bold text-slate-800 leading-tight">{{ task.name }}</span>
              <span class="text-[10px] text-slate-400 mt-0.5">点击查看执行详情图谱</span>
            </div>
            <CheckCircle2 v-if="task.status === 'completed'" size="14" class="text-emerald-500" />
            <span v-else class="text-[10px] font-bold text-blue-600">{{ task.progress }}%</span>
          </div>
          <!-- Progress Bar -->
          <div class="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all duration-500', task.status === 'completed' ? 'bg-emerald-500' : 'bg-blue-600 animate-pulse']"
              :style="{ width: `${task.progress}%` }"
            />
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="tasks.length === 0" class="p-8 text-center text-slate-400 text-sm">
          暂无后台任务
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Activity, Loader2, CheckCircle2, X } from 'lucide-vue-next'
import type { TaskItem } from '@/store/agent/types'

const props = defineProps<{
  tasks: TaskItem[]
  isOpen: boolean
}>()

defineEmits<{
  toggle: []
  close: []
  'select-task': [task: TaskItem]
}>()

const runningTaskCount = computed(() => props.tasks.filter(t => t.status === 'running').length)
const hasRunningTasks = computed(() => runningTaskCount.value > 0)
</script>
