import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/services/chatApi'

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
      sessions.value = res.data
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
      currentSession.value = res.data
      messages.value = res.data.messages || []
    } catch (error) {
      console.error('Failed to fetch session:', error)
    } finally {
      loading.value = false
    }
  }

  async function createSession(title = '新会话', scene = 'general') {
    try {
      const res = await chatApi.createSession({ title, scene })
      sessions.value.unshift(res.data)
      currentSession.value = res.data
      messages.value = []
      return res.data
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

      // 发送消息到后端
      const res = await chatApi.sendMessage(currentSession.value.id, {
        role: 'user',
        content,
        metadata: metadata ? JSON.stringify(metadata) : null
      })

      // 添加助手消息
      if (res.data) {
        messages.value.push(res.data)
      }

      // 更新会话标题（如果这是第一条消息）
      if (messages.value.length === 1 && currentSession.value.title === '新会话') {
        const autoTitle = content.substring(0, 30) + (content.length > 30 ? '...' : '')
        await updateSessionTitle(currentSession.value.id, autoTitle)
      }

      return res.data
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
      sessions.value = res.data
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
