import request from '@/api/request'

// Agent 会话 API
export const agentApi = {
  // 获取所有会话
  getSessions: () => request.get('/agent/sessions'),

  // 获取单个会话（含消息）
  getSession: (id) => request.get(`/agent/sessions/${id}`),

  // 创建会话
  createSession: (data) => request.post('/agent/sessions', data),

  // 更新会话标题
  updateSessionTitle: (id, title) => request.put(`/agent/sessions/${id}/title`, { title }),

  // 删除会话
  deleteSession: (id) => request.delete(`/agent/sessions/${id}`),

  // 搜索会话
  searchSessions: (keyword) => request.get('/agent/sessions/search', { params: { q: keyword } }),

  // 获取会话消息
  getMessages: (sessionId) => request.get(`/agent/sessions/${sessionId}/messages`),

  // 发送消息
  sendMessage: (sessionId, data) => request.post(`/agent/sessions/${sessionId}/messages`, data),

  // Agent 对话入口
  chat: (data) => request.post('/agent/chat', data),

  // Agent 文件任务提交
  submitTask: (formData) => request.post('/agent/submit', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  }),

  // Agent SSE 流式对话
  streamChat: async (data, handlers = {}) => {
    const response = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      body: JSON.stringify(data)
    })

    if (!response.ok || !response.body) {
      throw new Error(`SSE 请求失败: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const emit = (eventName, payload) => {
      if (eventName === 'meta' && handlers.onMeta) handlers.onMeta(payload)
      if (eventName === 'delta' && handlers.onDelta) handlers.onDelta(payload)
      if (eventName === 'done' && handlers.onDone) handlers.onDone(payload)
      if (eventName === 'error' && handlers.onError) handlers.onError(payload)
    }

    const processEventBlock = (block) => {
      const lines = block.split('\n')
      let eventName = 'message'
      const dataLines = []

      lines.forEach((line) => {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      })

      if (!dataLines.length) return
      const rawData = dataLines.join('\n')
      let payload = rawData
      try {
        payload = JSON.parse(rawData)
      } catch (error) {
        // Plain text chunks are expected for delta events.
      }
      emit(eventName, payload)
    }

    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      blocks.forEach(processEventBlock)
    }

    if (buffer.trim()) {
      processEventBlock(buffer)
    }
  }
}
