<template>
  <div class="task-board">
    <!-- 统计面板 -->
    <StatsPanel
      :statistics="statistics"
      :showExtended="showExtendedStats"
      @filter-high-risk="handleFilterHighRisk"
    />

    <!-- 筛选栏 -->
    <FilterBar
      :filters="filters"
      :filterOptions="filterOptions"
      :statistics="statistics"
      @update:filters="handleFiltersUpdate"
      @change="handleFilterChange"
    />

    <!-- 任务列表 -->
    <div v-if="isLoading && tasks.length === 0" class="loading-state text-center py-20">
      <div class="inline-flex items-center justify-center w-24 h-24 bg-indigo-50 rounded-3xl mb-6">
        <Loader class="w-12 h-12 text-indigo-600 animate-spin" />
      </div>
      <h3 class="text-xl font-medium text-slate-600">加载中...</h3>
      <p class="text-slate-400 mt-2">正在获取任务列表</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error && tasks.length === 0" class="error-state">
      <EmptyState
        type="error"
        title="加载失败"
        :description="error"
        @refresh="fetchTasks"
      />
    </div>

    <!-- 空状态 -->
    <div v-else-if="tasks.length === 0" class="empty-state">
      <EmptyState
        type="empty"
        :showCreateButton="true"
        :showRefreshButton="true"
        @create="showCreateModal = true"
        @refresh="fetchTasks"
      />
    </div>

    <!-- 无搜索结果 -->
    <div v-else-if="tasks.length === 0 && hasActiveFilters" class="no-result-state">
      <EmptyState
        type="no-result"
        title="没有找到任务"
        description="请尝试调整筛选条件"
        :showCreateButton="true"
        :showRefreshButton="true"
        @create="showCreateModal = true"
        @refresh="resetFilters"
      />
    </div>

    <!-- 任务网格 -->
    <div v-else class="task-grid grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
      <TaskCard
        v-for="task in tasks"
        :key="task.id"
        :task="task"
        @click="handleTaskClick(task)"
        @action="handleTaskAction"
      />
    </div>

    <!-- 分页 -->
    <div v-if="pagination.totalPages > 1" class="pagination mt-6 flex items-center justify-center gap-2">
      <button
        class="px-3 py-1.5 text-sm bg-white border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        :disabled="pagination.page <= 1"
        @click="handlePageChange(pagination.page - 1)"
      >
        <ChevronLeft class="w-4 h-4" />
      </button>

      <div class="flex items-center gap-1">
        <template v-for="page in visiblePages" :key="page">
          <span v-if="page === '...'" class="px-2 text-slate-400">...</span>
          <button
            v-else
            class="w-8 h-8 text-sm rounded-lg transition-colors"
            :class="page === pagination.page
              ? 'bg-indigo-600 text-white'
              : 'bg-white border border-slate-200 hover:bg-slate-50'"
            @click="handlePageChange(page)"
          >
            {{ page }}
          </button>
        </template>
      </div>

      <button
        class="px-3 py-1.5 text-sm bg-white border border-slate-200 rounded-lg hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        :disabled="pagination.page >= pagination.totalPages"
        @click="handlePageChange(pagination.page + 1)"
      >
        <ChevronRight class="w-4 h-4" />
      </button>

      <span class="ml-2 text-xs text-slate-500">
        共 {{ pagination.total }} 条
      </span>
    </div>

    <!-- 任务详情抽屉 -->
    <TaskDetailDrawer
      v-if="selectedTask"
      :task="selectedTask"
      :visible="showDetailDrawer"
      @close="closeDetailDrawer"
      @action="handleTaskAction"
    />

    <!-- 创建任务弹窗 -->
    <CreateTaskModal
      :visible="showCreateModal"
      @close="showCreateModal = false"
      @created="handleTaskCreated"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Loader, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { useTaskboardStore } from '@/stores/taskboard'
import TaskCard from './TaskCard.vue'
import FilterBar from './FilterBar.vue'
import StatsPanel from './StatsPanel.vue'
import EmptyState from './EmptyState.vue'
import TaskDetailDrawer from './TaskDetailDrawer.vue'
import CreateTaskModal from './CreateTaskModal.vue'

const store = useTaskboardStore()

// ==================== 状态 ====================

const showExtendedStats = ref(false)
const showDetailDrawer = ref(false)
const showCreateModal = ref(false)

// ==================== 解构 store ====================

const tasks = computed(() => store.tasks)
const statistics = computed(() => store.statistics)
const filterOptions = computed(() => store.filterOptions)
const filters = computed(() => store.filters)
const pagination = computed(() => store.pagination)
const isLoading = computed(() => store.isLoading)
const error = computed(() => store.error)
const selectedTask = computed(() => store.selectedTask)

// ==================== 计算属性 ====================

const hasActiveFilters = computed(() => {
  const f = filters.value
  return f.keyword || f.status || f.taskType || f.riskLevel || f.unhandledOnly || f.highRiskOnly
})

const visiblePages = computed(() => {
  const total = pagination.value.totalPages
  const current = pagination.value.page
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }

  if (current <= 3) {
    return [1, 2, 3, 4, '...', total]
  }
  if (current >= total - 2) {
    return [1, '...', total - 3, total - 2, total - 1, total]
  }
  return [1, '...', current - 1, current, current + 1, '...', total]
})

// ==================== 方法 ====================

const fetchTasks = async () => {
  try {
    await store.fetchTasks()
  } catch (e) {
    console.error('Failed to fetch tasks:', e)
  }
}

const fetchStatistics = async () => {
  try {
    await store.fetchStatistics()
  } catch (e) {
    console.error('Failed to fetch statistics:', e)
  }
}

const fetchFilterOptions = async () => {
  try {
    await store.fetchFilterOptions()
  } catch (e) {
    console.error('Failed to fetch filter options:', e)
  }
}

const handleFiltersUpdate = (newFilters) => {
  store.setFilters(newFilters)
}

const handleFilterChange = () => {
  fetchTasks()
}

const handleFilterHighRisk = () => {
  store.setFilters({ ...filters.value, highRiskOnly: true })
  fetchTasks()
}

const resetFilters = () => {
  store.resetFilters()
  fetchTasks()
}

const handlePageChange = (page) => {
  store.setPage(page)
  fetchTasks()
  // 滚动到顶部
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const handleTaskClick = async (task) => {
  store.setSelectedTask(task)
  showDetailDrawer.value = true
}

const closeDetailDrawer = () => {
  showDetailDrawer.value = false
}

const handleTaskCreated = async (newTask) => {
  showCreateModal.value = false
  await fetchTasks()
  await fetchStatistics()
}

const handleTaskAction = async ({ action, task }) => {
  console.log('Task action:', action, task)

  switch (action) {
    case 'START':
      await store.updateTaskStatus(task.id, 'RUNNING')
      break
    case 'CANCEL':
      await store.updateTaskStatus(task.id, 'CANCELLED')
      break
    case 'RETRY':
      await store.updateTaskStatus(task.id, 'PENDING')
      break
    case 'VIEW':
      store.setSelectedTask(task)
      showDetailDrawer.value = true
      break
    default:
      console.log('Unknown action:', action)
  }

  await fetchTasks()
  await fetchStatistics()
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await Promise.all([
    fetchTasks(),
    fetchStatistics(),
    fetchFilterOptions()
  ])
})

// 监听筛选条件变化，自动获取任务列表
watch(filters, () => {
  // 已在 handleFilterChange 中处理
}, { deep: true })
</script>

<style scoped>
.task-board {
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

.pagination {
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
