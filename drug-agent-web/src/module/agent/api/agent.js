import request from './request'

// ============================================================
// 标书审查 API 模块
// ============================================================

/**
 * 创建标书审查任务
 * @param {FormData} formData - 表单数据，包含 files 和 submittedBy
 */
export const createTenderReviewCase = (formData) => {
  return request.post('/tender/tasks', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取标书审查任务列表
 */
export const listTenderReviewCases = () => {
  return request.get('/tender/tasks')
}

/**
 * 获取任务详情
 * @param {string} caseId - 任务ID
 */
export const getTenderReviewCase = (caseId) => {
  return request.get(`/tender/tasks/${caseId}`)
}

/**
 * 解析文档
 * @param {string} caseId - 任务ID
 * @param {string} docId - 文档ID
 */
export const parseTenderDocument = (caseId, docId) => {
  return request.post(`/tender/tasks/${caseId}/parse/${docId}`)
}

/**
 * 执行审查
 * @param {string} caseId - 任务ID
 */
export const executeTenderReview = (caseId) => {
  return request.post(`/tender/tasks/${caseId}/execute`)
}

/**
 * 获取审查结果
 * @param {string} caseId - 任务ID
 */
export const getTenderReviewResult = (caseId) => {
  return request.get(`/tender/tasks/${caseId}/result`)
}

/**
 * Agent 统一文件任务入口
 * @param {Object} params - 包含 query, sessionId, userId, submittedBy, files
 */
export const submitDrugAgentTask = (params) => {
  const formData = new FormData()
  if (params.query) formData.append('query', params.query)
  if (params.sessionId) formData.append('sessionId', params.sessionId)
  if (params.userId) formData.append('userId', params.userId)
  if (params.submittedBy) formData.append('submittedBy', params.submittedBy)
  if (params.files && params.files.length > 0) {
    params.files.forEach(file => formData.append('files', file))
  }

  return request.post('/agent/submit', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export default {
  createTenderReviewCase,
  listTenderReviewCases,
  getTenderReviewCase,
  parseTenderDocument,
  executeTenderReview,
  getTenderReviewResult,
  submitDrugAgentTask
}
