/**
 * 统一消息模型类型定义
 * 所有聊天消息都必须遵循此类型系统
 */

// 报告摘要
export interface ReportSummary {
  traceId: string
  scene: string
  riskLevel: string
  score: number
  docCount: number
  summary: string
}

// 报告详情
export interface ReportDetail extends ReportSummary {
  managementSummary: string[]
  suggestedActions: string[]
  steps: string[]
  evidenceList?: string[]
}

// 用户消息
export interface UserMessage {
  id: string
  role: 'user'
  type: 'user_text'
  content: string
  attachments?: string[]
  createdAt: string
}

// AI 进度消息 (loading态)
export interface AssistantProgressMessage {
  id: string
  role: 'assistant'
  type: 'assistant_progress'
  content: string
  status: 'running'
  createdAt: string
}

// AI 文本消息
export interface AssistantTextMessage {
  id: string
  role: 'assistant'
  type: 'assistant_text'
  content: string
  createdAt: string
}

// AI 澄清消息
export interface AssistantClarifyMessage {
  id: string
  role: 'assistant'
  type: 'assistant_clarify'
  content: string
  createdAt: string
}

// AI 结果卡片消息
export interface AssistantResultCardMessage {
  id: string
  role: 'assistant'
  type: 'assistant_result_card'
  content: string
  result: ReportSummary
  createdAt: string
}

// 系统错误消息
export interface SystemErrorMessage {
  id: string
  role: 'system'
  type: 'system_error'
  content: string
  createdAt: string
}

// 联合类型 - 所有消息类型
export type ChatMessage =
  | UserMessage
  | AssistantProgressMessage
  | AssistantTextMessage
  | AssistantClarifyMessage
  | AssistantResultCardMessage
  | SystemErrorMessage

// 会话摘要 (用于左侧列表)
export interface SessionSummary {
  id: string
  title: string
  scene: string
  dateGroup: string
  updatedAt: string
}

// 会话详情 (用于中间时间线渲染)
export interface SessionDetail extends SessionSummary {
  messages: ChatMessage[]
}

// 任务项
export interface TaskItem {
  id: string
  name: string
  scene: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  progress: number
  updatedAt: string
  traceId?: string
}

// 风险等级配置
export const RiskLevelConfig = {
  HIGH: {
    color: 'text-rose-600',
    bg: 'bg-rose-50',
    border: 'border-rose-200',
    label: '高风险',
    icon: 'AlertOctagon'
  },
  MEDIUM: {
    color: 'text-amber-600',
    bg: 'bg-amber-50',
    border: 'border-amber-200',
    label: '中风险',
    icon: 'AlertTriangle'
  },
  LOW: {
    color: 'text-emerald-600',
    bg: 'bg-emerald-50',
    border: 'border-emerald-200',
    label: '低风险',
    icon: 'CheckCircle2'
  },
  UNKNOWN: {
    color: 'text-slate-600',
    bg: 'bg-slate-50',
    border: 'border-slate-200',
    label: '信息',
    icon: 'Info'
  }
} as const

export type RiskLevel = keyof typeof RiskLevelConfig
