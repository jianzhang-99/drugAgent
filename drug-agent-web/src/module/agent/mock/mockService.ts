/**
 * Mock 服务 - 提供模拟数据驱动完整对话流程
 * 用于阶段一原型还原，不接真实后端
 */
import type {
  ChatMessage,
  SessionDetail,
  SessionSummary,
  TaskItem,
  ReportSummary
} from '../types/chatMessage'

// ==================== Mock 数据 ====================

// 模拟会话列表
export const mockSessions: SessionSummary[] = [
  {
    id: 'sess_001',
    title: '年度设备采购标书比对',
    scene: 'TENDER',
    dateGroup: '今天',
    updatedAt: new Date().toISOString()
  },
  {
    id: 'sess_002',
    title: '骨科耗材供应商协议预审',
    scene: 'CONTRACT',
    dateGroup: '昨天',
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  },
  {
    id: 'sess_003',
    title: '医疗器械采购合规预警',
    scene: 'RISK_ALERT',
    dateGroup: '过去7天',
    updatedAt: new Date(Date.now() - 3 * 86400000).toISOString()
  }
]

// 模拟任务列表
export const mockTasks: TaskItem[] = [
  {
    id: 'T-001',
    name: '年度设备采购标书分析',
    scene: 'TENDER',
    status: 'completed',
    progress: 100,
    updatedAt: new Date(Date.now() - 600000).toISOString(),
    traceId: 'TRC-99281-A'
  }
]

// Mock会话详情（带完整消息）
const mockSessionDetails: Record<string, SessionDetail> = {
  'sess_001': {
    id: 'sess_001',
    title: '年度设备采购标书比对',
    scene: 'TENDER',
    dateGroup: '今天',
    updatedAt: new Date().toISOString(),
    messages: [
      {
        id: 'msg_001',
        role: 'user',
        type: 'user_text',
        content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
        attachments: ['样例A_XX医院标书.docx', '样例B_XX药房投标.pdf'],
        createdAt: new Date(Date.now() - 120000).toISOString()
      },
      {
        id: 'msg_002',
        role: 'assistant',
        type: 'assistant_text',
        content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。详细报告已生成，请查看下方卡片。',
        createdAt: new Date(Date.now() - 60000).toISOString()
      },
      {
        id: 'msg_003',
        role: 'assistant',
        type: 'assistant_result_card',
        content: '标书审查完成',
        result: {
          traceId: 'TRC-99281-A',
          scene: 'TENDER',
          riskLevel: 'HIGH',
          score: 87,
          docCount: 2,
          summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。'
        },
        createdAt: new Date(Date.now() - 60000).toISOString()
      }
    ]
  }
}

// ==================== Mock 服务函数 ====================

/**
 * 模拟延迟
 */
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

/**
 * 获取会话列表
 */
export async function fetchMockSessions(): Promise<SessionSummary[]> {
  await delay(300)
  return [...mockSessions]
}

/**
 * 获取会话详情
 */
export async function fetchMockSessionDetail(sessionId: string): Promise<SessionDetail | null> {
  await delay(200)

  // 如果有预存的详情，返回它
  if (mockSessionDetails[sessionId]) {
    return { ...mockSessionDetails[sessionId] }
  }

  // 否则创建一个新的空会话
  const summary = mockSessions.find(s => s.id === sessionId)
  if (!summary) return null

  return {
    ...summary,
    messages: []
  }
}

/**
 * 创建新会话
 */
export async function createMockSession(title: string = '新会话', scene: string = 'general'): Promise<SessionDetail> {
  await delay(200)

  const newSession: SessionDetail = {
    id: `sess_${Date.now()}`,
    title,
    scene,
    dateGroup: '今天',
    updatedAt: new Date().toISOString(),
    messages: []
  }

  // 添加到mock列表
  mockSessions.unshift({
    id: newSession.id,
    title: newSession.title,
    scene: newSession.scene,
    dateGroup: newSession.dateGroup,
    updatedAt: newSession.updatedAt
  })

  return newSession
}

/**
 * 发送消息并获取AI响应
 * 这是mock对话流程的核心函数
 */
export async function sendMockMessage(
  sessionId: string,
  content: string
): Promise<{
  userMessage: ChatMessage
  progressMessage: ChatMessage
  finalMessage: ChatMessage
}> {
  const timestamp = new Date().toISOString()

  // 1. 用户消息
  const userMessage: ChatMessage = {
    id: `msg_${Date.now()}_user`,
    role: 'user',
    type: 'user_text',
    content,
    createdAt: timestamp
  }

  // 2. AI进度消息
  const progressMessage: ChatMessage = {
    id: `msg_${Date.now()}_progress`,
    role: 'assistant',
    type: 'assistant_progress',
    content: '正在分析您的请求，请稍候...',
    status: 'running',
    createdAt: timestamp
  }

  // 3. 模拟AI处理延迟
  await delay(1500)

  // 4. 根据随机概率生成不同类型的回复
  // 70% assistant_text, 15% assistant_clarify, 15% assistant_result_card
  const finalTimestamp = new Date().toISOString()
  const msgTypeRand = Math.random()
  const isLowRisk = Math.random() > 0.3

  let finalMessage: ChatMessage

  if (msgTypeRand < 0.70) {
    // 70% 概率：普通文本回复
    finalMessage = {
      id: `msg_${Date.now()}_final`,
      role: 'assistant',
      type: 'assistant_text',
      content: isLowRisk
        ? '我已经完成了对您上传文档的审查。本次共审查了文档，未发现保留的高风险命中，建议将结果作为低风险基线。如需查看详细报告，请点击下方的结果卡片。'
        : '审查已完成。系统在文档中发现了高度雷同的排版与语义特征，已判定为高风险。请务必查看详细报告并进行人工复核。',
      createdAt: finalTimestamp
    }
  } else if (msgTypeRand < 0.85) {
    // 15% 概率：澄清消息
    finalMessage = {
      id: `msg_${Date.now()}_final`,
      role: 'assistant',
      type: 'assistant_clarify',
      content: '为了更准确地完成分析，请确认以下几点：\n1. 您要对比的标书是否已经排版完成？\n2. 是否需要按照特定的评分标准进行比对？\n3. 发现疑似雷同时，您希望系统自动标记还是仅做提示？',
      createdAt: finalTimestamp
    }
  } else {
    // 15% 概率：带结果卡片的回复
    if (content.includes('标书') || content.includes('围标')) {
      // 标书审查场景
      finalMessage = {
        id: `msg_${Date.now()}_final`,
        role: 'assistant',
        type: 'assistant_result_card',
        content: '标书审查完成',
        result: {
          traceId: `TRC-${Date.now()}`,
          scene: 'TENDER',
          riskLevel: isLowRisk ? 'LOW' : 'HIGH',
          score: isLowRisk ? 0 : 87,
          docCount: 2,
          summary: isLowRisk
            ? 'No retained high-risk hits after rule scan.'
            : '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。'
        },
        createdAt: finalTimestamp
      }
    } else if (content.includes('合同')) {
      // 合同预审场景
      finalMessage = {
        id: `msg_${Date.now()}_final`,
        role: 'assistant',
        type: 'assistant_result_card',
        content: '合同预审完成',
        result: {
          traceId: `TRC-${Date.now()}`,
          scene: 'CONTRACT',
          riskLevel: isLowRisk ? 'LOW' : 'MEDIUM',
          score: isLowRisk ? 10 : 65,
          docCount: 1,
          summary: isLowRisk
            ? '合同结构完整，未发现明显风险条款。'
            : '发现 3 条倾向于供应商的免责声明及付款周期违规条款。'
        },
        createdAt: finalTimestamp
      }
    } else {
      // 通用场景
      finalMessage = {
        id: `msg_${Date.now()}_final`,
        role: 'assistant',
        type: 'assistant_result_card',
        content: '审查完成',
        result: {
          traceId: `TRC-${Date.now()}`,
          scene: 'TENDER',
          riskLevel: isLowRisk ? 'LOW' : 'HIGH',
          score: isLowRisk ? 15 : 75,
          docCount: 1,
          summary: isLowRisk
            ? '未发现显著风险，建议作为低风险基线。'
            : '发现潜在风险点，建议进行人工复核。'
        },
        createdAt: finalTimestamp
      }
    }
  }

  return { userMessage, progressMessage, finalMessage }
}

/**
 * 获取任务列表
 */
export async function fetchMockTasks(): Promise<TaskItem[]> {
  await delay(200)
  return [...mockTasks]
}

/**
 * 添加一个模拟任务
 */
export async function addMockTask(name: string, scene: string): Promise<TaskItem> {
  await delay(100)

  const newTask: TaskItem = {
    id: `T-${Date.now()}`,
    name,
    scene,
    status: 'running',
    progress: 0,
    updatedAt: new Date().toISOString()
  }

  mockTasks.unshift(newTask)
  return newTask
}

/**
 * 更新任务状态
 */
export async function updateMockTaskStatus(
  taskId: string,
  progress: number,
  status: TaskItem['status']
): Promise<void> {
  await delay(100)

  const task = mockTasks.find(t => t.id === taskId)
  if (task) {
    task.progress = progress
    task.status = status
    task.updatedAt = new Date().toISOString()
  }
}

// ==================== Mock 服务导出 ====================

export const mockService = {
  fetchSessions: fetchMockSessions,
  fetchSessionDetail: fetchMockSessionDetail,
  createSession: createMockSession,
  sendMessage: sendMockMessage,
  fetchTasks: fetchMockTasks,
  addTask: addMockTask,
  updateTaskStatus: updateMockTaskStatus
}

export default mockService
