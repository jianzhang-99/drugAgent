<template>
  <div class="task-board-view min-h-screen bg-slate-50">
    <!-- Header -->
    <div class="bg-white border-b border-slate-200 sticky top-0 z-10">
      <div class="max-w-7xl mx-auto px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-slate-800">任务调度看板</h1>
            <p class="text-sm text-slate-500 mt-1">实时监控所有任务执行状态，3秒内看懂任务情况</p>
          </div>
          <div class="flex items-center gap-3">
            <button
              class="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors flex items-center gap-2"
              @click="toggleExtendedStats"
            >
              <BarChart3 class="w-4 h-4" />
              {{ showExtendedStats ? '收起统计' : '更多统计' }}
            </button>
            <button
              class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors flex items-center gap-2"
              @click="showCreateModal = true"
            >
              <Plus class="w-4 h-4" />
              新建任务
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="max-w-7xl mx-auto px-6 py-6">
      <TaskBoard />
    </div>

    <!-- Create Task Modal -->
    <CreateTaskModal
      :visible="showCreateModal"
      @close="showCreateModal = false"
      @created="handleTaskCreated"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus, BarChart3 } from 'lucide-vue-next'
import TaskBoard from '@/components/taskboard/TaskBoard.vue'
import CreateTaskModal from '@/components/taskboard/CreateTaskModal.vue'

const showCreateModal = ref(false)
const showExtendedStats = ref(false)

const toggleExtendedStats = () => {
  showExtendedStats.value = !showExtendedStats.value
}

const handleTaskCreated = (task) => {
  showCreateModal.value = false
  console.log('Task created:', task)
}
</script>

<style scoped>
.task-board-view {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
</style>
