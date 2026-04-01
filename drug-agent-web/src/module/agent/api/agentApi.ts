/**
 * Agent API 层
 * 调用后端接口：/api/agent/chat, /api/agent/submit, /api/agent/sessions, /api/agent/sessions/{id}
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
  ModelInfo,
} from '../types/agent';
import * as mockAgentApi from './mockAgentApi';

const USE_MOCK = import.meta.env.VITE_AGENT_USE_MOCK !== 'false';

/**
 * 同步对话
 * POST /api/agent/chat
 */
export function chat(req: ChatRequest) {
  if (USE_MOCK) return mockAgentApi.chat(req);
  return request.post<ApiResponse<DrugAgentResp>>('/api/agent/chat', req);
}

/**
 * 获取可用模型列表
 * GET /api/agent/models
 */
export function getModels() {
  if (USE_MOCK) return mockAgentApi.getModels();
  return request.get<ApiResponse<ModelInfo[]>>('/api/agent/models');
}

/**
 * 文件上传对话
 * POST /api/agent/submit (multipart/form-data)
 */
export function submit(
  query: string | undefined,
  sceneHint: string | undefined,
  sessionId: string | undefined,
  userId: string | undefined,
  submittedBy: string,
  model: string | undefined,
  files: File[]
) {
  if (USE_MOCK) {
    return mockAgentApi.submit(
      query,
      sceneHint,
      sessionId,
      userId,
      submittedBy,
      model,
      files
    );
  }
  // 构建请求对象，序列化为 JSON 字符串通过 'req' 参数传递
  const req = {
    query: query || '请审查这些文件',
    sceneHint,
    sessionId,
    userId,
    submittedBy,
    model,
  };
  const formData = new FormData();
  formData.append('req', JSON.stringify(req));
  files.forEach((file) => {
    formData.append('files', file);
  });

  return request.post<ApiResponse<DrugAgentResp>>('/api/agent/submit', formData);
}

/**
 * 获取所有会话列表
 * GET /api/agent/sessions
 */
export function getSessions() {
  if (USE_MOCK) return mockAgentApi.getSessions();
  return request.get<ApiResponse<ChatSession[]>>('/api/agent/sessions');
}

/**
 * 获取会话详情（含消息）
 * GET /api/agent/sessions/{id}
 */
export function getSessionById(id: string) {
  if (USE_MOCK) return mockAgentApi.getSessionById(id);
  return request.get<ApiResponse<ChatSession>>(`/api/agent/sessions/${id}`);
}

/**
 * 创建新会话
 * POST /api/agent/sessions
 */
export function createSession(data: CreateSessionRequest) {
  if (USE_MOCK) return mockAgentApi.createSession(data);
  return request.post<ApiResponse<ChatSession>>('/api/agent/sessions', data);
}

/**
 * 更新会话标题
 * PUT /api/agent/sessions/{id}/title
 */
export function updateSessionTitle(id: string, data: UpdateTitleRequest) {
  if (USE_MOCK) return mockAgentApi.updateSessionTitle(id, data);
  return request.put<ApiResponse<null>>(`/api/agent/sessions/${id}/title`, data);
}

/**
 * 删除会话（软删除）
 * DELETE /api/agent/sessions/{id}
 */
export function deleteSession(id: string) {
  if (USE_MOCK) return mockAgentApi.deleteSession(id);
  return request.delete<ApiResponse<null>>(`/api/agent/sessions/${id}`);
}

/**
 * 搜索会话
 * GET /api/agent/sessions/search?q=关键词
 */
export function searchSessions(q: string) {
  if (USE_MOCK) return mockAgentApi.searchSessions(q);
  return request.get<ApiResponse<ChatSession[]>>('/api/agent/sessions/search', {
    params: { q },
  });
}

/**
 * 获取会话的所有消息
 * GET /api/agent/sessions/{sessionId}/messages
 */
export function getMessages(sessionId: string) {
  if (USE_MOCK) return mockAgentApi.getMessages(sessionId);
  return request.get<ApiResponse<ChatMessage[]>>(
    `/api/agent/sessions/${sessionId}/messages`
  );
}

/**
 * 发送消息并获取AI响应
 * POST /api/agent/sessions/{sessionId}/messages
 */
export function addMessage(
  sessionId: string,
  content: string,
  role: string = 'user'
) {
  if (USE_MOCK) return mockAgentApi.addMessage(sessionId, content, role);
  return request.post<ApiResponse<any>>(
    `/api/agent/sessions/${sessionId}/messages`,
    { content, role }
  );
}

// ========== 知识库文件操作 ==========

export interface KnowledgeFile {
  id: string;
  fileName: string;
  fileSuffix: string;
  fileSize: number;
  contentType: string;
  objectKey: string;
  bizType: string;
  bizId?: string;
  uploadBy?: string;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface OssUploadResp {
  id: string;
  objectKey: string;
  etag: string;
  bucket: string;
  fileName: string;
  fileSize: number;
}

export interface OssListResp {
  list: KnowledgeFile[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 上传知识库文件
 * POST /api/oss/upload
 */
export function uploadKnowledgeFile(file: File, bizType: string = 'KNOWLEDGE', bizId?: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('bizType', bizType);
  if (bizId) formData.append('bizId', bizId);
  return request.post<ApiResponse<OssUploadResp>>('/api/oss/upload', formData);
}

/**
 * 获取知识库文件列表
 * GET /api/oss/files
 */
export function getKnowledgeFiles(bizType: string = 'KNOWLEDGE', page: number = 1, pageSize: number = 50) {
  return request.get<ApiResponse<OssListResp>>('/api/oss/files', {
    params: { bizType, page, pageSize },
  });
}

/**
 * 删除知识库文件
 * DELETE /api/oss/files/{id}
 */
export function deleteKnowledgeFile(id: string) {
  return request.delete<ApiResponse<null>>(`/api/oss/files/${id}`);
}
