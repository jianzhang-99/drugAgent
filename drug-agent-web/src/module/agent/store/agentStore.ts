/**
 * Agent Workbench 统一状态管理
 * 负责管理会话、消息、任务、报告等状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  ChatMessage,
  SessionDetail,
  SessionSummary,
  TaskItem,
  ReportSummary
} from '@/store/agent/types'
import type {
  AssistantProgressMessage,
  UserTextMessage
} from '@/store/agent/types'
import { apiAgent } from '@/services/agent/apiAgent'
import { mockService, simulateAIResponse, USE_REAL_API } from '@/components/agent/mockService'

// 消息 ID 生成器
let msgIdCounter = 10000

export const useAgentStore = defineStore('agent', () => {
  // ==================== 状态定义 ====================

  // 会话列表
  const sessions = ref<SessionSummary[]>([])

  // 当前活跃会话详情
  const activeSession = ref<SessionDetail | null>(null)

  // 活跃任务列表
  const tasks = ref<TaskItem[]>([])

  // 选中的报告（用于右侧抽屉）
  const selectedReport = ref<ReportSummary | null>(null)

  // 是否显示任务中心Popover
  const isTaskCenterOpen = ref(false)

  // 是否显示右侧报告抽屉
  const isReportDrawerOpen = ref(false)

  // 加载状态
  const isLoading = ref(false)

  // 错误信息
  const error = ref<string | null>(null)

  // ==================== 计算属性 ====================

  // 当前会话的所有消息
  const messages = computed(() => activeSession.value?.messages || [])

  // 当前会话ID
  const activeSessionId = computed(() => activeSession.value?.id || null)

  // 是否有活跃会话
  const hasActiveSession = computed(() => messages.value.length > 0)

  // 运行中的任务数量
  const runningTaskCount = computed(() =>
    tasks.value.filter(t => t.status === 'running').length
  )

  // 按时间分组的会话
  const groupedSessions = computed(() => {
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const yesterday = new Date(today.getTime() - 86400000)
    const weekAgo = new Date(today.getTime() - 7 * 86400000)

    const groups: Record<string, SessionSummary[]> = {
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

  // ==================== 会话 Actions ====================

  /**
   * 添加消息到当前会话
   */
  function addMessage(message: ChatMessage) {
    if (!activeSession.value) return

    if (!activeSession.value.messages) {
      activeSession.value.messages = []
    }
    activeSession.value.messages.push(message)
  }

  /**
   * 替换进度消息为最终消息
   * 这是实现 "loading -> 最终消息" 替换的核心机制
   */
  function replaceProgressMessage(finalMessage: ChatMessage) {
    if (!activeSession.value?.messages) return

    // 找到最后一个进度消息的索引（从后向前找）
    let progressIndex = -1
    for (let i = activeSession.value.messages.length - 1; i >= 0; i--) {
      if (activeSession.value.messages[i].type === 'assistant_progress') {
        progressIndex = i
        break
      }
    }

    if (progressIndex !== -1) {
      // 直接在进度消息的位置替换为最终消息
      activeSession.value.messages.splice(progressIndex, 1, finalMessage)
    } else {
      // 如果没有进度消息（异常情况），直接添加
      activeSession.value.messages.push(finalMessage)
    }
  }

  /**
   * 设置活跃会话
   */
  function setActiveSession(session: SessionDetail | null) {
    activeSession.value = session
    // 切换会话时清空选中的报告
    if (!session) {
      selectedReport.value = null
      isReportDrawerOpen.value = false
    }
  }

  /**
   * 更新会话列表
   */
  function setSessions(newSessions: SessionSummary[]) {
    sessions.value = newSessions
  }

  /**
   * 添加新会话到列表
   */
  function addSession(session: SessionSummary) {
    sessions.value.unshift(session)
  }

  /**
   * 更新会话标题
   */
  function updateSessionTitle(sessionId: string, title: string) {
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) {
      session.title = title
    }
    if (activeSession.value?.id === sessionId) {
      activeSession.value.title = title
    }
  }

  // ==================== 异步 Actions ====================

  /**
   * 获取会话列表
   */
  async function fetchSessions() {
    isLoading.value = true
    error.value = null
    try {
      // Mock 模式下直接使用 mockService
      if (!USE_REAL_API) {
        const mockList = mockService.getSessions()
        const details = mockList.map(s => mockService.getSession(s.id)).filter(Boolean) as SessionDetail[]
        sessions.value = details
      } else {
        // 真实 API 模式
        const sessionList = await apiAgent.getSessions()
        const details = await Promise.all(
          sessionList.map(s => apiAgent.getSession(s.id).catch(() => null))
        )
        sessions.value = details.filter(Boolean) as SessionDetail[]
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取会话列表失败'
      console.error('Failed to fetch sessions:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取单个会话详情
   */
  async function fetchSession(sessionId: string) {
    isLoading.value = true
    error.value = null
    try {
      let detail: SessionDetail | null = null

      if (!USE_REAL_API) {
        // Mock 模式
        detail = mockService.getSession(sessionId)
      } else {
        // 真实 API 模式
        detail = await apiAgent.getSession(sessionId)
      }

      if (detail) {
        // 更新 sessions 列表
        const index = sessions.value.findIndex(s => s.id === sessionId)
        if (index >= 0) {
          sessions.value[index] = detail
        } else {
          sessions.value.unshift(detail)
        }
        // 设置活跃会话
        activeSession.value = detail
      }
      return detail
    } catch (e) {
      error.value = e instanceof Error ? e.message : '获取会话详情失败'
      console.error('Failed to fetch session:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 创建新会话
   */
  async function createSession(title: string = '新会话', scene: string = 'general') {
    isLoading.value = true
    error.value = null
    try {
      let newSession: SessionDetail | null = null

      if (!USE_REAL_API) {
        // Mock 模式
        newSession = mockService.createSession(title, scene)
      } else {
        // 真实 API 模式
        newSession = await apiAgent.createSession({ title, scene })
      }

      if (newSession) {
        sessions.value.unshift(newSession)
        activeSession.value = newSession
      }
      return newSession
    } catch (e) {
      error.value = e instanceof Error ? e.message : '创建会话失败'
      console.error('Failed to create session:', e)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 发送消息（核心方法）
   * 1. 先插入 assistant_progress 消息
   * 2. 收到响应后用最终消息替换
   *
   * 阶段三改动：
   * - 当 USE_REAL_API 为 true 时调用真实 API
   * - API 失败时回退到 mockService.simulateAIResponse
   */
  async function sendMessage(content: string, attachments?: string[]) {
    if (!activeSession.value) return
    if (isLoading.value) return

    isLoading.value = true
    error.value = null

    const sessionId = activeSession.value.id

    // 1. 创建用户消息
    const userMsg: UserTextMessage = {
      id: `msg_${++msgIdCounter}`,
      role: 'user',
      type: 'user_text',
      content,
      attachments,
      createdAt: new Date().toISOString()
    }

    // 2. 添加用户消息到当前会话
    addMessage(userMsg)

    // 3. 创建进度消息（loading 状态）
    const progressMsg: AssistantProgressMessage = {
      id: `msg_${++msgIdCounter}`,
      role: 'assistant',
      type: 'assistant_progress',
      content: 'Agent 正在执行深层编排工作流...',
      status: 'running',
      createdAt: new Date().toISOString()
    }

    // 4. 添加进度消息
    addMessage(progressMsg)

    // 5. 创建任务（mock 模式先用，真实 API 会返回真实 task）
    const mockTask = mockService.addTask(content.substring(0, 20) + '...', 'TENDER')
    addTask(mockTask)

    try {
      if (USE_REAL_API) {
        // 真实 API 模式
        const response = await apiAgent.sendMessage(sessionId, { content, attachments })

        // 7. 用最终消息替换进度消息
        if (response.message) {
          replaceProgressMessage(response.message)
        }

        // 8. 更新任务状态（使用真实返回的 task 或 mock task）
        const finalTaskId = response.task?.id || mockTask.id
        updateTask(finalTaskId, { status: 'completed', progress: 100 })
      } else {
        // Mock 模式 - 使用 simulateAIResponse
        const finalMsg = await simulateAIResponse(sessionId, content)

        // 7. 用最终消息替换进度消息
        replaceProgressMessage(finalMsg)

        // 8. 更新任务状态
        updateTask(mockTask.id, { status: 'completed', progress: 100 })
      }

    } catch (e) {
      error.value = e instanceof Error ? e.message : '发送消息失败'
      console.error('Failed to send message:', e)

      // 失败时尝试回退到 mock
      if (USE_REAL_API) {
        try {
          const finalMsg = await simulateAIResponse(sessionId, content)
          replaceProgressMessage(finalMsg)
          updateTask(mockTask.id, { status: 'completed', progress: 100 })
          error.value = null // 恢复后清除错误
        } catch {
          // 回退也失败，保持错误状态
        }
      }

      // 如果出错，移除进度消息
      const progressIndex = activeSession.value.messages.findIndex(
        msg => msg.type === 'assistant_progress'
      )
      if (progressIndex !== -1) {
        activeSession.value.messages.splice(progressIndex, 1)
      }
    } finally {
      isLoading.value = false
    }
  }

  // ==================== 任务 Actions ====================

  /**
   * 获取活跃任务列表
   */
  async function fetchTasks() {
    try {
      if (USE_REAL_API) {
        tasks.value = await apiAgent.getActiveTasks()
      } else {
        tasks.value = mockService.getTasks()
      }
    } catch (e) {
      console.error('Failed to fetch tasks:', e)
      // 失败时使用 mock
      tasks.value = mockService.getTasks()
    }
  }

  // ==================== 任务 Actions ====================

  /**
   * 添加任务
   */
  function addTask(task: TaskItem) {
    tasks.value.unshift(task)
  }

  /**
   * 更新任务进度
   */
  function updateTask(taskId: string, updates: Partial<TaskItem>) {
    const task = tasks.value.find(t => t.id === taskId)
    if (task) {
      Object.assign(task, updates)
    }
  }

  /**
   * 移除任务
   */
  function removeTask(taskId: string) {
    tasks.value = tasks.value.filter(t => t.id !== taskId)
  }

  /**
   * 清除已完成的任务
   */
  function clearCompletedTasks() {
    tasks.value = tasks.value.filter(t => t.status !== 'completed')
  }

  // ==================== 报告抽屉 Actions ====================

  /**
   * 打开报告抽屉
   */
  function openReportDrawer(report: ReportSummary) {
    selectedReport.value = report
    isReportDrawerOpen.value = true
  }

  /**
   * 关闭报告抽屉
   */
  function closeReportDrawer() {
    isReportDrawerOpen.value = false
  }

  /**
   * 切换报告抽屉
   */
  function toggleReportDrawer(report?: ReportSummary) {
    if (isReportDrawerOpen.value && selectedReport.value?.traceId === report?.traceId) {
      closeReportDrawer()
    } else if (report) {
      openReportDrawer(report)
    }
  }

  // ==================== 任务中心 Actions ====================

  /**
   * 切换任务中心Popover
   */
  function toggleTaskCenter() {
    isTaskCenterOpen.value = !isTaskCenterOpen.value
  }

  /**
   * 关闭任务中心Popover
   */
  function closeTaskCenter() {
    isTaskCenterOpen.value = false
  }

  // ==================== 状态重置 ====================

  /**
   * 重置所有状态（用于退出或切换用户）
   */
  function resetAll() {
    sessions.value = []
    activeSession.value = null
    tasks.value = []
    selectedReport.value = null
    isTaskCenterOpen.value = false
    isReportDrawerOpen.value = false
    isLoading.value = false
    error.value = null
  }

  return {
    // 状态
    sessions,
    activeSession,
    tasks,
    selectedReport,
    isTaskCenterOpen,
    isReportDrawerOpen,
    isLoading,
    error,

    // 计算属性
    messages,
    activeSessionId,
    hasActiveSession,
    runningTaskCount,
    groupedSessions,

    // 会话 Actions
    addMessage,
    replaceProgressMessage,
    setActiveSession,
    setSessions,
    addSession,
    updateSessionTitle,

    // 异步 Actions
    fetchSessions,
    fetchSession,
    createSession,
    sendMessage,

    // 任务 Actions
    addTask,
    updateTask,
    removeTask,
    clearCompletedTasks,
    fetchTasks,

    // 报告抽屉 Actions
    openReportDrawer,
    closeReportDrawer,
    toggleReportDrawer,

    // 任务中心 Actions
    toggleTaskCenter,
    closeTaskCenter,

    // 状态重置
    resetAll
  }
})
