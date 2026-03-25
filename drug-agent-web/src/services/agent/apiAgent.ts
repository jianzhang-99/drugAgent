/**
 * API Agent - 真实后端接口调用封装
 * 用于阶段三联调，替换 mock 接口
 */

import request from '@/api/request'
import type { SessionSummary, SessionDetail, ChatMessage, TaskItem, ReportSummary } from '@/store/agent/types'

// ==================== 会话接口 ====================

/**
 * 获取所有会话列表
 */
export async function getSessions(): Promise<SessionSummary[]> {
  return request.get('/agent/sessions')
}

/**
 * 获取会话详情（含消息）
 */
export async function getSession(id: string): Promise<SessionDetail> {
  return request.get(`/agent/sessions/${id}`)
}

/**
 * 创建新会话
 */
export async function createSession(data: { title?: string; scene?: string }): Promise<SessionDetail> {
  return request.post('/agent/sessions', data)
}

/**
 * 删除会话
 */
export async function deleteSession(sessionId: string): Promise<void> {
  return request.delete(`/agent/sessions/${sessionId}`)
}

/**
 * 批量删除会话
 */
export async function deleteAllSessions(): Promise<void> {
  return request.delete('/agent/sessions')
}

/**
 * 发送消息
 * 返回 { message, task } 结构
 */
export async function sendMessage(
  sessionId: string,
  data: { content: string; attachments?: string[] }
): Promise<{ message: ChatMessage; task?: TaskItem }> {
  return request.post(`/agent/sessions/${sessionId}/messages`, data)
}

// ==================== 任务接口 ====================

/**
 * 获取活跃任务列表
 */
export async function getActiveTasks(): Promise<TaskItem[]> {
  return request.get('/tasks/active')
}

// ==================== 报告接口 ====================

/**
 * 获取报告详情
 */
export async function getReportDetail(traceId: string): Promise<ReportSummary> {
  return request.get(`/reports/${traceId}`)
}

// ==================== 文件上传接口 ====================

/**
 * 上传文件
 */
export async function uploadFiles(formData: FormData): Promise<{ url: string; name: string }[]> {
  return request.post('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// ==================== API Agent 统一导出 ====================

export const apiAgent = {
  getSessions,
  getSession,
  createSession,
  deleteSession,
  deleteAllSessions,
  sendMessage,
  getActiveTasks,
  getReportDetail,
  uploadFiles
}

export default apiAgent
