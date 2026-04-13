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

// ThinkingStep - 对应后端 ThinkingStep.java
export interface ThinkingStep {
  /** 步骤编码，如 route / prepare_data / rule_analysis */
  code: string;
  /** 步骤标题 */
  title: string;
  /** 步骤说明 */
  detail?: string;
  /** 步骤类型：ROUTE / EXECUTION / CLARIFICATION / ERROR / FINALIZE */
  type: string;
  /** 步骤状态：COMPLETED / FAILED / INFO / PROCESSING */
  status: string;
  /** 展示顺序，从 1 开始 */
  order: number;
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
  thinkingSteps?: ThinkingStep[];
  raw?: any;
}

export type MessageType =
  | 'user_text'
  | 'assistant_text'
  | 'assistant_clarify'
  | 'assistant_result_card'
  | 'assistant_processing'
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
  /** 文档名称列表（优先使用，供报告抽屉展示真实文件名） */
  documentNames?: string[];
  /** 文档ID列表 */
  documentIds?: string[];
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

// 后端 EvidenceItem
export interface EvidenceItem {
  id?: string;
  type?: string;
  content?: string;
  source?: string;
  page?: number;
  similarity?: number;
  description?: string;
}

// 后端 EvidenceGroup
export interface EvidenceGroup {
  id?: string;
  type?: string;
  evidenceList?: EvidenceItem[];
  similarity?: number;
}

// 后端 RiskItem
export interface RiskItem {
  riskType?: string;
  riskLevel?: string;
  title?: string;
  summary?: string;
  reasonCodes?: string[];
  evidenceTitles?: string[];
  recommendations?: string[];
}

// 后端 Overview
export interface ReviewReportOverview {
  documentCount?: number;
  rawHitCount?: number;
  effectiveHitCount?: number;
  exemptionCount?: number;
  evidenceGroupCount?: number;
  evidenceItemCount?: number;
  score?: number;
  riskLevel?: string;
  summary?: string;
}

export interface ReviewReport {
  caseId?: string;
  scene?: string;
  generatedAt?: string;
  markdownContent?: string;
  overview?: ReviewReportOverview;
  riskItems?: RiskItem[];
  managementSummary?: string[];
  recommendedActions?: string[];
  explanations?: Record<string, string>;
  title?: string;
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
  /** 是否使用流式响应 */
  stream?: boolean;
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
  /** 本次审查的文档名称列表 */
  documentNames?: string[];
  report?: ReviewReport;
  evidenceList?: Evidence[];
  evidenceGroups?: EvidenceGroup[];
  steps?: string[];
  /** 结构化思考步骤列表，由后端 AgentResponseService 构建 */
  thinkingSteps?: ThinkingStep[];
  structuredData?: Record<string, any>;
  requiresClarification?: boolean;
  clarificationQuestion?: string;
  sessionTitle?: string;
  /** 本次上传的文件ID列表，供前端会话级持久化用 */
  fileIds?: string[];
  /** 是否为流式响应 */
  streamed?: boolean;
  /** OCR文档解析结果 */
  ocrResult?: OcrResponse;
}

// OSS文件信息（对应后端 OssFile 实体）
export interface OssFileInfo {
  id: string;
  sessionId?: string;
  fileName?: string;
  fileSuffix?: string;
  fileSize?: number;
  ossUrl?: string;
  fileType?: number;
  uploadStatus?: number;
  createdAt?: string;
  updatedAt?: string;
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
  /** 会话关联的附件列表（非数据库字段，用于关联查询） */
  files?: OssFileInfo[];
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

// OCR 文档解析响应
export interface OcrResponse {
  success?: boolean;
  text?: string;
  blocks?: OcrTextBlock[];
  tables?: OcrTableResult[];
  errorCode?: string;
  errorMessage?: string;
  model?: string;
  costMs?: number;
}

export interface OcrTextBlock {
  text?: string;
  page?: number;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
  confidence?: number;
}

export interface OcrTableResult {
  csvContent?: string;
  htmlContent?: string;
  page?: number;
  startRow?: number;
  endRow?: number;
}

// 语音识别请求
export interface SpeechRecognitionRequest {
  format?: string;
  sampleRate?: number;
  language?: string;
  model?: string;
  url?: string;
  audioData?: string;
  verbose?: boolean;
}

// 语音识别响应
export interface SpeechRecognitionResponse {
  success?: boolean;
  text?: string;
  language?: string;
  segments?: SpeechSegment[];
  errorCode?: string;
  errorMessage?: string;
}

export interface SpeechSegment {
  start?: number;
  end?: number;
  text?: string;
  confidence?: number;
}

// 语音合成请求
export interface SpeechSynthesisRequest {
  text: string;
  model?: string;
  voice?: string;
  format?: string;
  sampleRate?: number;
  speed?: number;
  pitch?: number;
  volume?: number;
}

// 语音合成响应
export interface SpeechSynthesisResponse {
  success?: boolean;
  audioData?: string;
  audioUrl?: string;
  format?: string;
  duration?: number;
  errorCode?: string;
  errorMessage?: string;
}
