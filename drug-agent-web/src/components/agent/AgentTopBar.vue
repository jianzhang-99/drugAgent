<template>
  <header class="h-14 border-b border-slate-100 px-6 flex items-center justify-between bg-white/80 backdrop-blur-md sticky top-0 z-10">
    <!-- Left: Breadcrumb -->
    <div class="flex items-center gap-2">
      <span class="text-slate-400 text-sm font-medium">
        {{ activeView === 'WORKSPACE' ? 'Agent 审查工作台' : activeViewLabel }}
      </span>
      <template v-if="activeView === 'WORKSPACE' && activeSessionTitle">
        <ChevronRight size="14" class="text-slate-300" />
        <span class="text-sm font-bold text-slate-800">{{ activeSessionTitle }}</span>
      </template>
    </div>

    <!-- Right: Actions -->
    <div class="flex items-center gap-4">
      <!-- Task Center Popover -->
      <TaskCenterPopover
        :tasks="tasks"
        :is-open="isTaskCenterOpen"
        @toggle="$emit('toggle-task-center')"
        @close="$emit('close-task-center')"
        @select-task="$emit('select-task', $event)"
      />

      <!-- User Avatar -->
      <div class="w-7 h-7 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-full shadow-md flex items-center justify-center text-[10px] font-bold text-white">
        DA
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import TaskCenterPopover from './TaskCenterPopover.vue'
import type { TaskItem } from '@/store/agent/types'

const props = defineProps<{
  activeView: string
  activeSessionTitle?: string
  tasks: TaskItem[]
  isTaskCenterOpen: boolean
}>()

defineEmits<{
  'toggle-task-center': []
  'close-task-center': []
  'select-task': [task: TaskItem]
}>()

const activeViewLabel = computed(() => {
  switch (props.activeView) {
    case 'TASKS':
      return '任务调度看板'
    case 'KNOWLEDGE':
      return '知识大脑'
    case 'SETTINGS':
      return '系统配置'
    default:
      return 'Agent 审查工作台'
  }
})
</script>
