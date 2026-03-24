<template>
  <header class="h-14 px-6 flex items-center justify-between bg-white/80 backdrop-blur-sm border-b border-slate-200/50 sticky top-0 z-50">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm">
      <span class="text-slate-400">{{ parentTitle }}</span>
      <template v-if="currentTitle">
        <ChevronRight class="w-4 h-4 text-slate-300" />
        <span class="font-semibold text-slate-800">{{ currentTitle }}</span>
      </template>
    </div>

    <!-- Right Section -->
    <div class="flex items-center gap-4">
      <!-- Task Status Button -->
      <button
        class="flex items-center gap-2 px-4 py-2 rounded-full bg-slate-50 hover:bg-slate-100 border border-slate-200 transition-all duration-200"
        @click="$emit('toggle-task-pane')"
      >
        <div class="relative">
          <Activity class="w-4 h-4 text-indigo-600" />
          <span
            v-if="activeTaskCount > 0"
            class="absolute -top-1.5 -right-1.5 w-4 h-4 bg-indigo-600 text-white text-[10px] font-bold rounded-full flex items-center justify-center animate-pulse"
          >
            {{ activeTaskCount > 9 ? '9+' : activeTaskCount }}
          </span>
        </div>
        <span class="text-sm font-medium text-slate-600">
          {{ activeTaskCount > 0 ? `${activeTaskCount} 活跃任务` : '任务中心' }}
        </span>
      </button>

      <!-- User Avatar -->
      <div class="w-9 h-9 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white text-sm font-bold shadow-lg shadow-indigo-500/20 cursor-pointer hover:shadow-indigo-500/30 transition-shadow">
        DA
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ChevronRight, Activity } from 'lucide-vue-next'

const props = defineProps({
  activeTaskCount: {
    type: Number,
    default: 0
  }
})

defineEmits(['toggle-task-pane'])

const route = useRoute()

const routeTitles = {
  '/workspace': '智能审查工作台',
  '/tasks': '任务调度看板',
  '/knowledge': '合规知识库',
  '/settings': '系统配置'
}

const parentTitle = '横渡智能系统'

const currentTitle = computed(() => {
  const path = route.path
  for (const [key, value] of Object.entries(routeTitles)) {
    if (path === key || path.startsWith(key + '/')) {
      return value
    }
  }
  return null
})
</script>
