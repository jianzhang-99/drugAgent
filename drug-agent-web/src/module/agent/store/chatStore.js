import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/module/agent/api/chatApi'

export const useChatStore = defineStore('chat', () => {
  // State
  const sessions = ref([])
  const currentSession = ref(null)
  const messages = ref([])
  const loading = ref(false)
  const searchQuery = ref('')

  // Getters
  const filteredSessions = computed(() => {
    if (!searchQuery.value) return sessions.value
    const query = searchQuery.value.toLowerCase()
    return sessions.value.filter(s =>
      s.title?.toLowerCase().includes(query)
    )
  })

  const groupedSessions = computed(() => {
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterday = new Date(today.getTime() - 86400000)
    const weekAgo = new Date(today.getTime() - 7 * 86400000)

    const groups = {
      '今天': [],
      '昨天': [],
      '过去7天': [],
      '更早': []
    }

    filteredSessions.value.forEach(session => {
      const sessionDate = new Date(session.updatedAt || session.createdAt)
      if (sessionDate >= today) {
        groups['今天'].push(session)
      } else if (sessionDate >= yesterday) {
        groups['昨天'].push(session)
      } else if (sessionDate >= weekAgo) {
        groups['过去7天'].push(session)
      } else {
        groups['更早'].push(session)
      }
    })

    return groups
  })

  // Actions
  async function fetchSessions() {
    loading.value = true
    try {
      const res = await chatApi.getSessions()
      // 拦截器已返回 res.data，直接使用
      sessions.value = res || []
    } catch (error) {
      console.error('Failed to fetch sessions:', error)
    } finally {
      loading.value = false
    }
  }

  async function fetchSession(sessionId) {
    loading.value = true
    try {
      const res = await chatApi.getSession(sessionId)
      // 拦截器已返回 res.data，直接使用
      currentSession.value = res
      messages.value = res?.messages || []
    } catch (error) {
      console.error('Failed to fetch session:', error)
    } finally {
      loading.value = false
    }
  }

  async function createSession(title = '新会话', scene = 'general') {
    try {
      const res = await chatApi.createSession({ title, scene })
      // 拦截器已返回 res.data，直接使用
      sessions.value.unshift(res)
      currentSession.value = res
      messages.value = []
      return res
    } catch (error) {
      console.error('Failed to create session:', error)
    }
  }

  async function updateSessionTitle(sessionId, title) {
    try {
      await chatApi.updateSessionTitle(sessionId, title)
      const session = sessions.value.find(s => s.id === sessionId)
      if (session) session.title = title
      if (currentSession.value?.id === sessionId) currentSession.value.title = title
    } catch (error) {
      console.error('Failed to update title:', error)
    }
  }

  async function deleteSession(sessionId) {
    try {
      await chatApi.deleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (currentSession.value?.id === sessionId) {
        currentSession.value = null
        messages.value = []
      }
    } catch (error) {
      console.error('Failed to delete session:', error)
    }
  }

  async function sendMessage(content, metadata = null) {
    if (!currentSession.value) {
      // 如果没有当前会话，先创建一个
      await createSession()
    }

    try {
      // 添加用户消息到本地
      const userMsg = {
        id: 'temp-' + Date.now(),
        role: 'user',
        content,
        createdAt: new Date().toISOString()
      }
      messages.value.push(userMsg)

      // 调用 /agent/chat 同步对话接口
      const res = await chatApi.chat({
        query: content,
        sessionId: currentSession.value.id,
        userId: 'user'
      })

      // 添加助手消息
      if (res) {
        const agentMsg = {
          id: 'temp-' + Date.now() + '-agent',
          role: 'agent',
          content: res.answer || res.summary || '处理完成',
          createdAt: new Date().toISOString(),
          result: res
        }
        messages.value.push(agentMsg)
      }

      // 更新会话标题（如果这是第一条消息）
      if (messages.value.length === 1 && currentSession.value.title === '新会话') {
        const autoTitle = content.substring(0, 30) + (content.length > 30 ? '...' : '')
        await updateSessionTitle(currentSession.value.id, autoTitle)
      }

      return res
    } catch (error) {
      console.error('Failed to send message:', error)
      // 移除临时消息
      messages.value = messages.value.filter(m => !m.id.startsWith('temp-'))
    }
  }

  async function searchSessions(query) {
    searchQuery.value = query
    if (!query) {
      await fetchSessions()
      return
    }
    try {
      const res = await chatApi.searchSessions(query)
      // 拦截器已返回 res.data，直接使用
      sessions.value = res || []
    } catch (error) {
      console.error('Failed to search sessions:', error)
    }
  }

  function setCurrentSession(session) {
    currentSession.value = session
    messages.value = session?.messages || []
  }

  return {
    // State
    sessions,
    currentSession,
    messages,
    loading,
    searchQuery,
    // Getters
    filteredSessions,
    groupedSessions,
    // Actions
    fetchSessions,
    fetchSession,
    createSession,
    updateSessionTitle,
    deleteSession,
    sendMessage,
    searchSessions,
    setCurrentSession
  }
})
