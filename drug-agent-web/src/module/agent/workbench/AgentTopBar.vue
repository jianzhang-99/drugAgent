<template>
  <header class="h-14 border-b border-slate-100 px-6 flex items-center justify-between bg-white/80 backdrop-blur-md sticky top-0 z-10">
    <!-- 标题区域 -->
    <div class="flex items-center gap-2">
      <span class="text-slate-400 text-sm font-medium">
        {{ pageTitle }}
      </span>

      <template v-if="activeView === 'WORKSPACE' && activeSessionTitle">
        <ChevronRight size="14" class="text-slate-300" />
        <span class="text-sm font-bold text-slate-800">{{ activeSessionTitle }}</span>
      </template>
    </div>

    <!-- 右侧区域 -->
    <div class="flex items-center gap-4">
      <!-- 任务中心按钮 -->
      <div class="relative">
        <button
          class="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all"
          :class="runningTaskCount > 0
            ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200 shadow-sm'
            : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
          "
          @click="$emit('toggle-task-center')"
        >
          <Loader2
            v-if="runningTaskCount > 0"
            size="14"
            class="animate-spin"
          />
          <Activity v-else size="14" />
          <span>{{ runningTaskCount }} 活跃任务</span>
        </button>
      </div>

      <!-- 用户头像 -->
      <div class="w-7 h-7 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-full shadow-md flex items-center justify-center text-[10px] font-bold text-white">
        DA
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, Activity, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  activeSessionTitle?: string
  activeView: string
  runningTaskCount: number
  isTaskCenterOpen: boolean
}>()

defineEmits<{
  (e: 'toggle-task-center'): void
}>()

// 页面标题
const pageTitle = computed(() => {
  const titles: Record<string, string> = {
    'WORKSPACE': 'Agent 审查工作台',
    'TASKS': '任务调度看板',
    'KNOWLEDGE': '知识大脑',
    'SETTINGS': '系统配置'
  }
  return titles[props.activeView] || 'Agent 审查工作台'
})
</script>

<style scoped>
/* 样式已在 Tailwind 中定义 */
</style>
