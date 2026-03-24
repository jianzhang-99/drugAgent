import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as taskboardApi from '@/module/task_board/api/taskboard'

/**
 * 任务状态管理
 * 负责管理任务的列表、统计、筛选等
 */
export const useTaskStore = defineStore('task', () => {
  // ==================== 状态定义 ====================

  // 任务列表
  const tasks = ref([])

  // 统计数据
  const statistics = ref(null)

  // 筛选项配置
  const filterOptions = ref({
    statusOptions: {},
    taskTypeOptions: {},
    riskLevelOptions: {}
  })

  // 当前筛选条件
  const filters = ref({
    status: '',
    taskType: '',
    riskLevel: '',
    keyword: '',
    sortField: 'createdAt',
    sortOrder: 'desc'
  })

  // 分页信息
  const pagination = ref({
    page: 1,
    pageSize: 20,
    total: 0,
    totalPages: 0
  })

  // 加载状态
  const isLoading = ref(false)

  // 错误信息
  const error = ref(null)

  // 选中的任务
  const selectedTask = ref(null)

  // ==================== 计算属性 ====================

  // 待处理任务数
  const pendingTasks = computed(() => {
    return tasks.value.filter(t => t.status === 'PENDING' || t.status === 'PARSING')
  })

  // 执行中任务数
  const inProgressTasks = computed(() => {
    return tasks.value.filter(t => t.status === 'RUNNING')
  })

  // 已完成任务数
  const completedTasks = computed(() => {
    return tasks.value.filter(t => t.status === 'COMPLETED')
  })

  // 高风险任务数
  const highRiskTasks = computed(() => {
    return tasks.value.filter(t => t.riskLevel === 'HIGH' && t.status !== 'COMPLETED')
  })

  // ==================== Actions ====================

  /**
   * 获取任务列表
   */
  async function fetchTasks() {
    isLoading.value = true
    error.value = null

    try {
      const params = {
        page: pagination.value.page,
        pageSize: pagination.value.pageSize,
        ...filters.value
      }

      const response = await taskboardApi.getTaskList(params)
      const data = response.data || response

      tasks.value = data.records || data.list || []
      pagination.value.total = data.total || 0
      pagination.value.totalPages = data.pages || 1

      return tasks.value
    } catch (e) {
      error.value = e.message || '获取任务列表失败'
      console.error('Failed to fetch tasks:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取统计数据
   */
  async function fetchStatistics() {
    try {
      const response = await taskboardApi.getTaskStatistics()
      statistics.value = response.data || response
    } catch (e) {
      console.error('Failed to fetch statistics:', e)
    }
  }

  /**
   * 获取筛选项配置
   */
  async function fetchFilterOptions() {
    try {
      const response = await taskboardApi.getFilterOptions()
      const data = response.data || response
      filterOptions.value = {
        statusOptions: data.statusOptions || {},
        taskTypeOptions: data.taskTypeOptions || {},
        riskLevelOptions: data.riskLevelOptions || {}
      }
    } catch (e) {
      console.error('Failed to fetch filter options:', e)
    }
  }

  /**
   * 获取任务详情
   */
  async function fetchTaskDetail(taskId) {
    try {
      const response = await taskboardApi.getTaskDetail(taskId)
      const task = response.data || response
      selectedTask.value = task
      return task
    } catch (e) {
      console.error('Failed to fetch task detail:', e)
    }
  }

  /**
   * 创建任务
   */
  async function createTask(taskData) {
    try {
      const response = await taskboardApi.createTask(taskData)
      const created = response.data || response
      tasks.value.unshift(created)
      return created
    } catch (e) {
      console.error('Failed to create task:', e)
    }
  }

  /**
   * 更新任务
   */
  async function updateTask(taskId, taskData) {
    try {
      const response = await taskboardApi.updateTask(taskId, taskData)
      const updated = response.data || response
      const index = tasks.value.findIndex(t => t.id === taskId)
      if (index !== -1) {
        tasks.value[index] = updated
      }
      return updated
    } catch (e) {
      console.error('Failed to update task:', e)
    }
  }

  /**
   * 设置筛选条件
   */
  function setFilters(newFilters) {
    filters.value = { ...filters.value, ...newFilters }
    pagination.value.page = 1
  }

  /**
   * 重置筛选条件
   */
  function resetFilters() {
    filters.value = {
      status: '',
      taskType: '',
      riskLevel: '',
      keyword: '',
      sortField: 'createdAt',
      sortOrder: 'desc'
    }
    pagination.value.page = 1
  }

  /**
   * 设置分页
   */
  function setPage(page) {
    pagination.value.page = page
  }

  /**
   * 设置选中的任务
   */
  function setSelectedTask(task) {
    selectedTask.value = task
  }

  return {
    // 状态
    tasks,
    statistics,
    filterOptions,
    filters,
    pagination,
    isLoading,
    error,
    selectedTask,

    // 计算属性
    pendingTasks,
    inProgressTasks,
    completedTasks,
    highRiskTasks,

    // Actions
    fetchTasks,
    fetchStatistics,
    fetchFilterOptions,
    fetchTaskDetail,
    createTask,
    updateTask,
    setFilters,
    resetFilters,
    setPage,
    setSelectedTask
  }
})
