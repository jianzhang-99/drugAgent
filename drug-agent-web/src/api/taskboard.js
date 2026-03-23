import request from './request'

// ============================================================
// 任务看板 API 模块
// ============================================================

/**
 * 获取任务看板统计信息
 */
export const getTaskStatistics = () => {
  return request.get('/task-board/statistics')
}

/**
 * 分页获取任务卡片列表
 * @param {Object} params - 查询参数
 */
export const getTaskList = (params) => {
  return request.get('/task-board/tasks', { params })
}

/**
 * 获取单个任务卡片详情
 * @param {string} taskId - 任务ID
 */
export const getTaskDetail = (taskId) => {
  return request.get(`/task-board/tasks/${taskId}`)
}

/**
 * 创建新任务
 * @param {Object} data - 任务数据
 */
export const createTask = (data) => {
  return request.post('/task-board/tasks', data)
}

/**
 * 更新任务状态
 * @param {string} taskId - 任务ID
 * @param {string} status - 新状态
 */
export const updateTaskStatus = (taskId, status) => {
  return request.patch(`/task-board/tasks/${taskId}/status`, { status })
}

/**
 * 更新任务进度
 * @param {string} taskId - 任务ID
 * @param {number} progress - 进度 0-100
 * @param {string} currentStep - 当前步骤
 */
export const updateTaskProgress = (taskId, progress, currentStep) => {
  return request.patch(`/task-board/tasks/${taskId}/progress`, { progress, currentStep })
}

/**
 * 获取筛选项选项列表
 */
export const getFilterOptions = () => {
  return request.get('/task-board/filter-options')
}
