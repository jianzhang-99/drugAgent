<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition-all duration-200 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-2"
  >
    <div
      v-if="isOpen"
      class="fixed top-20 right-6 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200/60 overflow-hidden z-50"
    >
      <!-- Header -->
      <div class="px-5 py-4 bg-slate-50/80 border-b border-slate-200/50 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div class="w-8 h-8 rounded-lg bg-indigo-100 flex items-center justify-center">
            <Activity class="w-4 h-4 text-indigo-600" />
          </div>
          <h3 class="font-bold text-slate-800">后台任务中心</h3>
        </div>
        <button
          class="w-7 h-7 rounded-lg hover:bg-slate-200 flex items-center justify-center text-slate-400 hover:text-slate-600 transition-colors"
          @click="$emit('close')"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- Task List -->
      <div class="max-h-96 overflow-y-auto">
        <div v-if="tasks.length === 0" class="py-12 text-center">
          <div class="w-12 h-12 mx-auto mb-3 rounded-full bg-slate-100 flex items-center justify-center">
            <CheckCircle2 class="w-6 h-6 text-slate-400" />
          </div>
          <p class="text-sm text-slate-500">暂无运行中的任务</p>
        </div>

        <div
          v-for="task in tasks"
          :key="task.id"
          class="px-5 py-4 border-b border-slate-100 hover:bg-slate-50/50 transition-colors cursor-pointer"
          @click="$emit('select-task', task)"
        >
          <div class="flex items-start justify-between gap-3 mb-2">
            <div class="flex-1 min-w-0">
              <h4 class="text-sm font-semibold text-slate-800 truncate">{{ task.name }}</h4>
              <div class="flex items-center gap-2 mt-1">
                <span class="text-xs text-slate-400 font-mono">{{ task.id }}</span>
                <span
                  class="text-xs font-medium px-1.5 py-0.5 rounded"
                  :class="{
                    'bg-amber-50 text-amber-600': task.status === 'running',
                    'bg-emerald-50 text-emerald-600': task.status === 'completed',
                    'bg-slate-100 text-slate-500': task.status === 'pending'
                  }"
                >
                  {{ statusText(task.status) }}
                </span>
              </div>
            </div>

            <!-- Status Icon -->
            <div class="flex-shrink-0">
              <CheckCircle2
                v-if="task.progress >= 100"
                class="w-5 h-5 text-emerald-500"
              />
              <span
                v-else
                class="text-xs font-bold text-indigo-600"
              >
                {{ task.progress }}%
              </span>
            </div>
          </div>

          <!-- Progress Bar -->
          <div class="h-1.5 bg-slate-100 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500 ease-out"
              :class="{
                'bg-indigo-500': task.status === 'running',
                'bg-emerald-500': task.status === 'completed',
                'bg-slate-300': task.status === 'pending'
              }"
              :style="{ width: `${task.progress}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="px-5 py-3 bg-slate-50/50 border-t border-slate-200/50">
        <p class="text-xs text-slate-400 text-center">
          最后更新: {{ lastUpdateTime }}
        </p>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { computed } from 'vue'
import { Activity, X, CheckCircle2 } from 'lucide-vue-next'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  },
  tasks: {
    type: Array,
    default: () => []
  }
})

defineEmits(['close', 'select-task'])

const statusText = (status) => {
  const map = {
    running: '进行中',
    completed: '已完成',
    pending: '等待中',
    failed: '失败'
  }
  return map[status] || status
}

const lastUpdateTime = computed(() => {
  return new Date().toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
})
</script>
