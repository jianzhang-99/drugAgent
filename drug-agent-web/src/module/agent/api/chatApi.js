import request from '@/api/request'

// ============================================================
// 会话聊天 API 模块
// ============================================================

/**
 * 同步对话（不带文件的对话）
 * @param {Object} data - 对话数据 { query, sessionId, userId, sceneHint }
 */
export const chat = (data) => {
  return request.post('/agent/chat', data)
}

/**
 * 获取所有会话列表
 */
export const getSessions = () => {
  return request.get('/agent/sessions')
}

/**
 * 获取单个会话详情
 * @param {string} sessionId - 会话ID
 */
export const getSession = (sessionId) => {
  return request.get(`/agent/sessions/${sessionId}`)
}

/**
 * 创建新会话
 * @param {Object} data - 会话数据 { title, scene }
 */
export const createSession = (data) => {
  return request.post('/agent/sessions', data)
}

/**
 * 更新会话标题
 * @param {string} sessionId - 会话ID
 * @param {string} title - 新标题
 */
export const updateSessionTitle = (sessionId, title) => {
  return request.put(`/agent/sessions/${sessionId}/title`, { title })
}

/**
 * 删除会话
 * @param {string} sessionId - 会话ID
 */
export const deleteSession = (sessionId) => {
  return request.delete(`/agent/sessions/${sessionId}`)
}

/**
 * 搜索会话
 * @param {string} query - 搜索关键词
 */
export const searchSessions = (query) => {
  return request.get('/agent/sessions/search', { params: { q: query } })
}

/**
 * 获取会话消息列表
 * @param {string} sessionId - 会话ID
 */
export const getMessages = (sessionId) => {
  return request.get(`/agent/sessions/${sessionId}/messages`)
}

export const chatApi = {
  chat,
  getSessions,
  getSession,
  createSession,
  updateSessionTitle,
  deleteSession,
  searchSessions,
  getMessages
}
