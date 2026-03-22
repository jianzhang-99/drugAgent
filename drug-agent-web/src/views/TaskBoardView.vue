<template>
  <div class="min-h-screen bg-gray-50 p-6">
    <div class="max-w-7xl mx-auto">
      <!-- Header -->
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900">任务调度看板</h1>
        <p class="text-gray-500 mt-2">实时监控所有任务执行状态</p>
      </div>

      <!-- Task Grid -->
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="bg-white rounded-xl p-5 shadow-sm hover:shadow-xl transition-all duration-300 cursor-pointer border border-gray-100"
        >
          <!-- Scene Tag -->
          <div class="flex items-center justify-between mb-4">
            <span
              :class="getSceneClass(task.scene)"
              class="px-3 py-1 rounded-full text-xs font-medium"
            >
              {{ task.scene }}
            </span>
            <span class="text-xs text-gray-400">#{{ task.id }}</span>
          </div>

          <!-- Task Name -->
          <h3 class="text-lg font-semibold text-gray-800 mb-3">{{ task.name }}</h3>

          <!-- Progress Section -->
          <div class="space-y-2">
            <div class="flex justify-between text-sm">
              <span class="text-gray-500">进度</span>
              <span :class="getStatusClass(task.status)" class="font-medium">
                {{ task.progress }}%
              </span>
            </div>

            <!-- Progress Bar -->
            <div class="h-2 bg-gray-100 rounded-full overflow-hidden">
              <div
                :class="getProgressBarClass(task.status)"
                :style="{ width: task.progress + '%' }"
                class="h-full rounded-full transition-all duration-500"
              ></div>
            </div>

            <!-- Status Text -->
            <div class="text-xs text-gray-400 mt-2">
              {{ getStatusText(task) }}
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="tasks.length === 0" class="text-center py-20">
        <div class="inline-flex items-center justify-center w-24 h-24 bg-gray-100 rounded-full mb-6">
          <LayoutGrid class="w-12 h-12 text-gray-400" />
        </div>
        <h3 class="text-xl font-medium text-gray-600">暂无任务</h3>
        <p class="text-gray-400 mt-2">开始一个新任务来体验 AI 审查流程</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { LayoutGrid } from 'lucide-vue-next'

const tasks = ref([
  { id: 'T20240321001', name: '某省医疗采购招标异常检测', scene: 'TENDER', status: 'running', progress: 68, startTime: '2024-03-21 10:30:00' },
  { id: 'T20240321002', name: '医疗器械采购合同风险评估', scene: 'CONTRACT', status: 'running', progress: 45, startTime: '2024-03-21 11:15:00' },
  { id: 'T20240321003', name: '药品价格波动监控预警', scene: 'COMPLIANCE', status: 'completed', progress: 100, startTime: '2024-03-21 09:00:00' },
  { id: 'T20240321004', name: '供应商资质审查', scene: 'TENDER', status: 'pending', progress: 0, startTime: '2024-03-21 14:00:00' },
  { id: 'T20240321005', name: '合同条款合规性复核', scene: 'CONTRACT', status: 'completed', progress: 100, startTime: '2024-03-21 08:30:00' },
  { id: 'T20240321006', name: '采购数据异常波动分析', scene: 'COMPLIANCE', status: 'running', progress: 82, startTime: '2024-03-21 12:00:00' }
])

let interval = null
onMounted(() => {
  interval = setInterval(() => {
    tasks.value.forEach(task => {
      if (task.status === 'running' && task.progress < 100) {
        task.progress = Math.min(100, task.progress + Math.random() * 5)
      }
    })
  }, 2000)
})
onUnmounted(() => { if (interval) clearInterval(interval) })

function getSceneClass(scene) {
  return { TENDER: 'bg-amber-100 text-amber-700', CONTRACT: 'bg-blue-100 text-blue-700', COMPLIANCE: 'bg-purple-100 text-purple-700', GENERAL: 'bg-gray-100 text-gray-700' }[scene] || 'bg-gray-100 text-gray-700'
}
function getStatusClass(status) {
  return { running: 'text-blue-600', completed: 'text-green-600', pending: 'text-gray-400', failed: 'text-red-600' }[status] || 'text-gray-400'
}
function getProgressBarClass(status) {
  return { running: 'bg-blue-500 animate-pulse', completed: 'bg-green-500', pending: 'bg-gray-300', failed: 'bg-red-500' }[status] || 'bg-gray-300'
}
function getStatusText(task) {
  return { running: `运行中 · 开始于 ${task.startTime}`, completed: `已完成 · ${task.startTime}`, pending: `等待中 · 计划 ${task.startTime}`, failed: `失败 · ${task.startTime}` }[task.status] || task.status
}
</script>

<style scoped>
.animate-pulse { animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.6; } }
</style>
