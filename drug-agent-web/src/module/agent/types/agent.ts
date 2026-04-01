/**
 * Agent 模块类型定义
 */

// Session
export interface Session {
  id: string;
  title: string;
  scene: string;
  updatedAt: string;
  userId?: string;
  messages?: Message[];
}

// Message
export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  type: MessageType;
  content: string;
  createdAt: string;
  status?: MessageStatus;
  attachments?: Attachment[];
  result?: ResultData;
  raw?: any;
}

export type MessageType =
  | 'user_text'
  | 'assistant_text'
  | 'assistant_clarify'
  | 'assistant_result_card'
  | 'system_error'
  | 'uploading';

export type MessageStatus = 'sending' | 'sent' | 'error';

export interface Attachment {
  id: string;
  name: string;
  size: number;
  type: string;
  url?: string;
}

export interface ResultData {
  riskLevel?: string;
  score?: number;
  summary?: string;
  steps?: string[];
  evidenceList?: Evidence[];
  report?: ReviewReport;
  confidence?: number;
  routeReason?: string;
  scene?: string;
  traceId?: string;
  docCount?: number;
  suggestedActions?: string[];
}

export interface Evidence {
  id?: string;
  type?: string;
  content?: string;
  source?: string;
  page?: number;
  similarity?: number;
  description?: string;
}

export interface ReviewReport {
  title?: string;
  overview?: string;
  findings?: Finding[];
  conclusion?: string;
  recommendations?: string[];
  metadata?: Record<string, any>;
}

export interface Finding {
  id?: string;
  title?: string;
  description?: string;
  severity?: 'high' | 'medium' | 'low' | 'info';
  evidence?: Evidence[];
}

// API 请求类型
export interface ChatRequest {
  query?: string;
  sessionId?: string;
  userId?: string;
  sceneHint?: string;
  fileIds?: string[];
  model?: string;
}

export interface SubmitRequest {
  query?: string;
  sceneHint?: string;
  sessionId?: string;
  userId?: string;
  submittedBy?: string;
  files: File[];
}

export interface CreateSessionRequest {
  title?: string;
  scene?: string;
}

export interface UpdateTitleRequest {
  title: string;
}

// API 响应类型
export interface DrugAgentResp {
  sessionId?: string;
  traceId?: string;
  scene?: string;
  routeReason?: string;
  routeSource?: string;
  confidence?: number;
  summary?: string;
  answer?: string;
  riskLevel?: string;
  score?: number;
  docCount?: number;
  managementSummary?: string;
  suggestedActions?: string[];
  caseId?: string;
  documentIds?: string[];
  report?: ReviewReport;
  evidenceList?: Evidence[];
  evidenceGroups?: EvidenceGroup[];
  steps?: string[];
  structuredData?: Record<string, any>;
  requiresClarification?: boolean;
  clarificationQuestion?: string;
  sessionTitle?: string;
  fileIds?: string[];
}

export interface EvidenceGroup {
  id?: string;
  type?: string;
  evidenceList?: Evidence[];
  similarity?: number;
}

// 后端 ChatSession 格式
export interface ChatSession {
  id: string;
  title: string;
  scene: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  messages?: ChatMessage[];
  files?: OssFile[];
}

// OSS 文件格式
export interface OssFile {
  id: string;
  sessionId?: string;
  fileName: string;
  fileSuffix: string;
  fileSize: number;
  ossUrl: string;
  fileType: number;
  uploadStatus: number;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant' | 'system';
  type: string;
  content: string;
  metadata?: Record<string, any>;
  createdAt: string;
}

// API 统一响应格式
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 模型信息
export interface ModelInfo {
  model: string;
  name: string;
  description?: string;
  isDefault?: boolean;
}
