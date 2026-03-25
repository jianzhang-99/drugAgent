import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { chatApi } from '@/module/agent/api/chatApi'

/**
 * 会话状态管理
 * 负责管理聊天会话的创建、加载、切换等
 */
export const useSessionStore = defineStore('session', () => {
  // ==================== 状态定义 ====================

  // 会话列表
  const sessions = ref([])

  // 当前活跃会话
  const activeSession = ref(null)

  // 加载状态
  const isLoading = ref(false)

  // 错误信息
  const error = ref(null)

  // ==================== 计算属性 ====================

  // 按时间分组的会话
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

    sessions.value.forEach(session => {
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

  const activeSessionId = computed(() => activeSession.value?.id || null)

  // ==================== Actions ====================

  /**
   * 获取会话列表
   */
  async function fetchSessions() {
    isLoading.value = true
    error.value = null

    try {
      const response = await chatApi.getSessions()
      // 拦截器已返回 res.data，直接使用
      const incomingSessions = response || []
      sessions.value = incomingSessions.map(incomingSession => {
        const existingSession = sessions.value.find(session => session.id === incomingSession.id)
        if (existingSession?.messages?.length && !incomingSession?.messages?.length) {
          return {
            ...incomingSession,
            messages: existingSession.messages.filter(message => !message?.isLoading)
          }
        }
        if (incomingSession?.messages?.length) {
          return {
            ...incomingSession,
            messages: incomingSession.messages.filter(message => !message?.isLoading)
          }
        }
        return incomingSession
      })
    } catch (e) {
      error.value = e.message || '获取会话列表失败'
      console.error('Failed to fetch sessions:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取单个会话详情
   */
  async function fetchSession(sessionId) {
    isLoading.value = true
    error.value = null

    try {
      const response = await chatApi.getSession(sessionId)
      // 拦截器已返回 res.data，直接使用
      const existingIndex = sessions.value.findIndex(s => s.id === sessionId)
      const existingSession = existingIndex >= 0 ? sessions.value[existingIndex] : null
      const mergedResponse = existingSession?.messages?.length && !response?.messages?.length
        ? {
            ...response,
            messages: existingSession.messages.filter(message => !message?.isLoading)
          }
        : {
            ...response,
            messages: (response?.messages || []).filter(message => !message?.isLoading)
          }

      if (existingIndex >= 0) {
        sessions.value[existingIndex] = mergedResponse
      } else if (mergedResponse) {
        sessions.value.unshift(mergedResponse)
      }
      activeSession.value = mergedResponse
      return mergedResponse
    } catch (e) {
      error.value = e.message || '获取会话详情失败'
      console.error('Failed to fetch session:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 创建新会话
   */
  async function createSession(title = '新会话', scene = 'general') {
    isLoading.value = true
    error.value = null

    try {
      const response = await chatApi.createSession({ title, scene })
      // 拦截器已返回 res.data，直接使用
      const newSession = response
      sessions.value.unshift(newSession)
      activeSession.value = newSession
      return newSession
    } catch (e) {
      error.value = e.message || '创建会话失败'
      console.error('Failed to create session:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 更新会话标题
   */
  async function updateSessionTitle(sessionId, title) {
    try {
      await chatApi.updateSessionTitle(sessionId, title)
      const session = sessions.value.find(s => s.id === sessionId)
      if (session) {
        session.title = title
      }
      if (activeSession.value?.id === sessionId) {
        activeSession.value.title = title
      }
    } catch (e) {
      console.error('Failed to update session title:', e)
    }
  }

  /**
   * 添加消息到会话
   */
  function addMessage(sessionId, message) {
    const session = sessions.value.find(s => s.id === sessionId)
    const normalizedMessage = {
      ...message,
      id: message.id || Date.now().toString(),
      createdAt: message.createdAt || new Date().toISOString()
    }

    if (session) {
      if (!session.messages) {
        session.messages = []
      }
      session.messages.push(normalizedMessage)
    }

    // activeSession 通常与 sessions 中对象同引用，避免重复 push
    if (activeSession.value?.id === sessionId && activeSession.value !== session) {
      if (!activeSession.value.messages) {
        activeSession.value.messages = []
      }
      activeSession.value.messages.push(normalizedMessage)
    }
  }

  /**
   * 更新会话信息
   */
  function updateSession(sessionId, updates) {
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) {
      Object.assign(session, updates)
    }
    if (activeSession.value?.id === sessionId) {
      Object.assign(activeSession.value, updates)
    }
  }

  /**
   * 删除会话
   */
  async function deleteSession(sessionId) {
    try {
      await chatApi.deleteSession(sessionId)
      sessions.value = sessions.value.filter(s => s.id !== sessionId)
      if (activeSession.value?.id === sessionId) {
        activeSession.value = null
      }
    } catch (e) {
      console.error('Failed to delete session:', e)
    }
  }

  /**
   * 清空所有会话
   */
  async function clearAllSessions() {
    try {
      await chatApi.deleteAllSessions()
    } catch (e) {
      console.error('Failed to clear all sessions:', e)
    }
    // 不管 API 是否成功，都清空本地状态
    sessions.value = []
    activeSession.value = null
  }

  /**
   * 搜索会话
   */
  async function searchSessions(query) {
    try {
      const response = await chatApi.searchSessions(query)
      // 拦截器已返回 res.data，直接使用
      sessions.value = response || []
    } catch (e) {
      console.error('Failed to search sessions:', e)
    }
  }

  /**
   * 设置当前活跃会话
   */
  function setActiveSession(session) {
    if (!session) {
      activeSession.value = null
      return
    }

    if (typeof session === 'string') {
      activeSession.value = sessions.value.find(s => s.id === session) || { id: session }
      return
    }

    activeSession.value = session
  }

  return {
    // 状态
    sessions,
    activeSession,
    isLoading,
    error,

    // 计算属性
    groupedSessions,
    activeSessionId,

    // Actions
    fetchSessions,
    fetchSession,
    createSession,
    updateSessionTitle,
    addMessage,
    updateSession,
    deleteSession,
    clearAllSessions,
    searchSessions,
    setActiveSession
  }
})
