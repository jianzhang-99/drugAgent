/**
 * Drug Agent - 消息类型定义
 * 统一的消息模型，用于会话时间线渲染
 */

// ==================== 报告模型 ====================

export interface ReportSummary {
  traceId: string
  scene: string
  riskLevel: string
  score: number
  docCount: number
  summary: string
}

export interface ReportDetail extends ReportSummary {
  managementSummary: string[]
  suggestedActions: string[]
  steps: string[]
  evidenceList?: string[]
}

// ==================== 任务模型 ====================

export type TaskStatus = 'pending' | 'running' | 'completed' | 'failed'

export interface TaskItem {
  id: string
  name: string
  scene: string
  status: TaskStatus
  progress: number
  updatedAt: string
  traceId?: string
  riskLevel?: string
  findings?: string
}

// ==================== 会话模型 ====================

export interface SessionSummary {
  id: string
  title: string
  scene: string
  dateGroup: string
  updatedAt: string
}

export interface SessionDetail extends SessionSummary {
  messages: ChatMessage[]
}

// ==================== 消息模型 ====================

// User Message
export interface UserTextMessage {
  id: string
  role: 'user'
  type: 'user_text'
  content: string
  attachments?: string[]
  createdAt: string
}

// Assistant Progress Message (loading状态)
export interface AssistantProgressMessage {
  id: string
  role: 'assistant'
  type: 'assistant_progress'
  content: string
  status: 'running'
  createdAt: string
}

// Assistant Text Message (普通文本回复)
export interface AssistantTextMessage {
  id: string
  role: 'assistant'
  type: 'assistant_text'
  content: string
  createdAt: string
}

// Assistant Clarify Message (澄清请求)
export interface AssistantClarifyMessage {
  id: string
  role: 'assistant'
  type: 'assistant_clarify'
  content: string
  createdAt: string
}

// Assistant Result Card Message (带结果卡)
export interface AssistantResultCardMessage {
  id: string
  role: 'assistant'
  type: 'assistant_result_card'
  content: string
  result: ReportSummary
  createdAt: string
}

// System Error Message
export interface SystemErrorMessage {
  id: string
  role: 'system'
  type: 'system_error'
  content: string
  createdAt: string
}

// Union Type for all messages
export type ChatMessage =
  | UserTextMessage
  | AssistantProgressMessage
  | AssistantTextMessage
  | AssistantClarifyMessage
  | AssistantResultCardMessage
  | SystemErrorMessage

// ==================== 工具函数 ====================

/**
 * 判断消息是否为用户消息
 */
export function isUserMessage(msg: ChatMessage): boolean {
  return msg.type === 'user_text'
}

/**
 * 判断消息是否为 Assistant 消息
 */
export function isAssistantMessage(msg: ChatMessage): boolean {
  return ['assistant_progress', 'assistant_text', 'assistant_clarify', 'assistant_result_card'].includes(msg.type)
}

/**
 * 判断消息是否为 Progress 状态
 */
export function isProgressMessage(msg: ChatMessage): boolean {
  return msg.type === 'assistant_progress'
}

/**
 * 判断消息是否为结果卡片
 */
export function isResultCardMessage(msg: ChatMessage): boolean {
  return msg.type === 'assistant_result_card'
}

/**
 * 判断消息是否为错误消息
 */
export function isErrorMessage(msg: ChatMessage): boolean {
  return msg.type === 'system_error'
}

/**
 * 获取风险等级配置
 */
export function getRiskConfig(level: string): {
  color: string
  bg: string
  border: string
  label: string
  icon: string
} {
  switch (level?.toUpperCase()) {
    case 'HIGH':
      return { color: 'text-rose-600', bg: 'bg-rose-50', border: 'border-rose-200', label: '高风险', icon: 'AlertOctagon' }
    case 'MEDIUM':
      return { color: 'text-amber-600', bg: 'bg-amber-50', border: 'border-amber-200', label: '中风险', icon: 'AlertTriangle' }
    case 'LOW':
      return { color: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-200', label: '低风险', icon: 'CheckCircle2' }
    default:
      return { color: 'text-slate-600', bg: 'bg-slate-50', border: 'border-slate-200', label: '信息', icon: 'Info' }
  }
}

/**
 * 获取场景标签
 */
export function getSceneLabel(scene: string): string {
  if (scene === 'TENDER' || scene === 'TENDER_REVIEW') return '标书审查'
  if (scene === 'CONTRACT' || scene === 'CONTRACT_PRECHECK') return '合同预审'
  if (scene === 'RISK_ALERT') return '合规预警'
  return '智能审查'
}
