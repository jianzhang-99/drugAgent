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

  // ==================== Actions ====================

  /**
   * 获取会话列表
   */
  async function fetchSessions() {
    isLoading.value = true
    error.value = null

    try {
      const response = await chatApi.getSessions()
      sessions.value = response.data || []
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
      activeSession.value = response.data
      return response.data
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
      const newSession = response.data
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
   * 搜索会话
   */
  async function searchSessions(query) {
    try {
      const response = await chatApi.searchSessions(query)
      sessions.value = response.data || []
    } catch (e) {
      console.error('Failed to search sessions:', e)
    }
  }

  /**
   * 设置当前活跃会话
   */
  function setActiveSession(session) {
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

    // Actions
    fetchSessions,
    fetchSession,
    createSession,
    updateSessionTitle,
    deleteSession,
    searchSessions,
    setActiveSession
  }
})
