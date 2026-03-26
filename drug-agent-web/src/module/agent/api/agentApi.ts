/**
 * Agent API 层
 * 调用后端接口：/agent/chat, /agent/submit, /agent/sessions, /agent/sessions/{id}
 */

import request from '@/utils/request';
import type {
  ChatRequest,
  CreateSessionRequest,
  UpdateTitleRequest,
  DrugAgentResp,
  ChatSession,
  ChatMessage,
  ApiResponse,
} from '../types/agent';
import * as mockAgentApi from './mockAgentApi';

const USE_MOCK = import.meta.env.VITE_AGENT_USE_MOCK !== 'false';

/**
 * 同步对话
 * POST /agent/chat
 */
export function chat(req: ChatRequest) {
  if (USE_MOCK) return mockAgentApi.chat(req);
  return request.post<ApiResponse<DrugAgentResp>>('/agent/chat', req);
}

/**
 * 文件上传对话
 * POST /agent/submit
 */
export function submit(
  query: string | undefined,
  sceneHint: string | undefined,
  sessionId: string | undefined,
  userId: string | undefined,
  submittedBy: string,
  files: File[]
) {
  if (USE_MOCK) {
    return mockAgentApi.submit(
      query,
      sceneHint,
      sessionId,
      userId,
      submittedBy,
      files
    );
  }
  const formData = new FormData();
  if (query) formData.append('query', query);
  if (sceneHint) formData.append('sceneHint', sceneHint);
  if (sessionId) formData.append('sessionId', sessionId);
  if (userId) formData.append('userId', userId);
  formData.append('submittedBy', submittedBy);
  files.forEach((file) => {
    formData.append('files', file);
  });

  return request.post<ApiResponse<DrugAgentResp>>('/agent/submit', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

/**
 * 获取所有会话列表
 * GET /agent/sessions
 */
export function getSessions() {
  if (USE_MOCK) return mockAgentApi.getSessions();
  return request.get<ApiResponse<ChatSession[]>>('/agent/sessions');
}

/**
 * 获取会话详情（含消息）
 * GET /agent/sessions/{id}
 */
export function getSessionById(id: string) {
  if (USE_MOCK) return mockAgentApi.getSessionById(id);
  return request.get<ApiResponse<ChatSession>>(`/agent/sessions/${id}`);
}

/**
 * 创建新会话
 * POST /agent/sessions
 */
export function createSession(data: CreateSessionRequest) {
  if (USE_MOCK) return mockAgentApi.createSession(data);
  return request.post<ApiResponse<ChatSession>>('/agent/sessions', data);
}

/**
 * 更新会话标题
 * PUT /agent/sessions/{id}/title
 */
export function updateSessionTitle(id: string, data: UpdateTitleRequest) {
  if (USE_MOCK) return mockAgentApi.updateSessionTitle(id, data);
  return request.put<ApiResponse<null>>(`/agent/sessions/${id}/title`, data);
}

/**
 * 删除会话（软删除）
 * DELETE /agent/sessions/{id}
 */
export function deleteSession(id: string) {
  if (USE_MOCK) return mockAgentApi.deleteSession(id);
  return request.delete<ApiResponse<null>>(`/agent/sessions/${id}`);
}

/**
 * 搜索会话
 * GET /agent/sessions/search?q=关键词
 */
export function searchSessions(q: string) {
  if (USE_MOCK) return mockAgentApi.searchSessions(q);
  return request.get<ApiResponse<ChatSession[]>>('/agent/sessions/search', {
    params: { q },
  });
}

/**
 * 获取会话的所有消息
 * GET /agent/sessions/{sessionId}/messages
 */
export function getMessages(sessionId: string) {
  if (USE_MOCK) return mockAgentApi.getMessages(sessionId);
  return request.get<ApiResponse<ChatMessage[]>>(
    `/agent/sessions/${sessionId}/messages`
  );
}

/**
 * 发送消息并获取AI响应
 * POST /agent/sessions/{sessionId}/messages
 */
export function addMessage(
  sessionId: string,
  content: string,
  role: string = 'user'
) {
  if (USE_MOCK) return mockAgentApi.addMessage(sessionId, content, role);
  return request.post<ApiResponse<any>>(
    `/agent/sessions/${sessionId}/messages`,
    { content, role }
  );
}
