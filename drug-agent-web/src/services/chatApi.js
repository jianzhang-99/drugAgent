import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 会话相关 API
export const chatApi = {
  // 获取所有会话
  getSessions: () => api.get('/sessions'),

  // 获取单个会话（含消息）
  getSession: (id) => api.get(`/sessions/${id}`),

  // 创建会话
  createSession: (data) => api.post('/sessions', data),

  // 更新会话标题
  updateSessionTitle: (id, title) => api.put(`/sessions/${id}/title`, { title }),

  // 删除会话
  deleteSession: (id) => api.delete(`/sessions/${id}`),

  // 搜索会话
  searchSessions: (keyword) => api.get('/sessions/search', { params: { q: keyword } }),

  // 获取会话消息
  getMessages: (sessionId) => api.get(`/sessions/${sessionId}/messages`),

  // 发送消息
  sendMessage: (sessionId, data) => api.post(`/sessions/${sessionId}/messages`, data)
}

export default api
