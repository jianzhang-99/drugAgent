/**
 * 消息映射工具
 * 将后端响应映射为统一 Message 格式
 */

import type {
  Message,
  MessageType,
  DrugAgentResp,
  ChatMessage,
  ResultData,
  Attachment,
  ThinkingStep,
} from '../types/agent';

/**
 * 从后端 AgentChatResp 映射为前端 Message
 */
export function mapResponseToMessage(
  resp: DrugAgentResp,
  role: 'assistant' = 'assistant'
): Message {
  const messageType = determineMessageType(resp);

  const message: Message = {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role,
    type: messageType,
    content: resp.answer || resp.summary || '',
    createdAt: new Date().toISOString(),
    status: 'sent',
    thinkingSteps: resp.thinkingSteps,
    raw: resp,
  };

  // 如果有结果数据，设置 result
  if (resp.report || resp.riskLevel || resp.score !== undefined) {
    message.result = mapToResultData(resp);
  }

  return message;
}

/**
 * 从后端 ChatMessage 映射为前端 Message
 */
export function mapChatMessageToMessage(chatMsg: ChatMessage): Message {
  const message: Message = {
    id: chatMsg.id,
    role: chatMsg.role as 'user' | 'assistant' | 'system',
    type: chatMsg.type as MessageType,
    content: chatMsg.content,
    createdAt: chatMsg.createdAt,
    status: 'sent',
    raw: chatMsg,
  };
  const metadata = chatMsg.metadata as DrugAgentResp | undefined;

  // 情形1：后端存储的 type 本身已是结果卡片类型（最可靠的判断）
  if (chatMsg.type === 'assistant_result_card') {
    message.type = 'assistant_result_card';
    // 尽量从 metadata 恢复结构化 result，若 metadata 不完整则降级展示内容文本
    if (metadata) {
      message.result = mapToResultData(metadata);
    }
  }
  // 情形2：type 不是卡片类型，但 metadata 里含有结构化数据，说明后端存的 type 有误，强制修正
  else if (metadata && (metadata.report || metadata.riskLevel || metadata.score !== undefined)) {
    message.result = mapToResultData(metadata);
    message.type = 'assistant_result_card';
  }
  // 情形3：content 本身是 markdown 格式的审查报告（如 | 项目 | 内容 | 表格开头），应转为卡片展示
  else if (chatMsg.content && isReportMarkdown(chatMsg.content)) {
    // 从 markdown 中尝试解析关键字段
    const parsed = parseReportFromMarkdown(chatMsg.content);
    message.type = 'assistant_result_card';
    message.content = parsed.summary || extractBriefSummary(chatMsg.content);
    message.result = {
      riskLevel: parsed.riskLevel,
      score: parsed.score,
      summary: parsed.summary || extractBriefSummary(chatMsg.content),
      report: parsed.report,
      documentNames: parsed.documentNames,
      traceId: parsed.traceId,
      scene: 'TENDER_REVIEW',
    };
  }

  // 从 metadata 恢复思考步骤
  if (metadata?.thinkingSteps && metadata.thinkingSteps.length > 0) {
    message.thinkingSteps = metadata.thinkingSteps as ThinkingStep[];
  }
  return message;
}

/**
 * 判断 content 是否为审查报告的 markdown 格式
 */
function isReportMarkdown(content: string): boolean {
  // 标书审查报告特征：包含 | 项目 | 内容 | 表格 或 ## 二、审查结论 等标记
  return (
    content.includes('| 项目 | 内容 |') ||
    content.includes('## 二、审查结论') ||
    content.includes('## 三、重点风险说明') ||
    content.includes('审查编号') ||
    (content.includes('##') && content.includes('风险等级') && content.includes('建议处置意见'))
  );
}

/**
 * 从 markdown 报告内容中解析结构化数据
 */
function parseReportFromMarkdown(content: string): {
  riskLevel?: string;
  score?: number;
  summary?: string;
  report?: any;
  documentNames?: string[];
  traceId?: string;
} {
  const result: ReturnType<typeof parseReportFromMarkdown> = {};

  // 提取风险等级
  const levelMatch = content.match(/\*\*综合风险等级\*\*[:\s]*[:：]?\s*\*\*([^\*]+)\*\*/i);
  if (levelMatch) {
    const level = levelMatch[1].toLowerCase();
    if (level.includes('高风险') || level.includes('high')) result.riskLevel = 'high';
    else if (level.includes('中风险') || level.includes('medium')) result.riskLevel = 'medium';
    else if (level.includes('低风险') || level.includes('low')) result.riskLevel = 'low';
    else if (level.includes('安全') || level.includes('safe')) result.riskLevel = 'safe';
  }

  // 提取风险分数
  const scoreMatch = content.match(/\*\*风险参考分值\*\*[:\s]*[:：]?\s*\*\*(\d+)\*\*/);
  if (scoreMatch) {
    result.score = parseInt(scoreMatch[1], 10);
  }

  // 提取审查编号（作为 traceId）
  const traceMatch = content.match(/\*\*审查编号\*\*[^\n]*\|\s*([a-f0-9-]+)/i);
  if (traceMatch) {
    result.traceId = traceMatch[1];
  }

  // 提取文档名称
  const docMatch = content.match(/\*\*审查对象\*\*[^\n]*\|\s*([^\n]+)/);
  if (docMatch) {
    const docs = docMatch[1].split(/\s*\/\s*/);
    result.documentNames = docs.map((d: string) => d.trim()).filter((d: string) => d.length > 0);
  }

  // 提取结论摘要
  const summaryMatch = content.match(/\*\*结论摘要\*\*[^\n]*\|\s*([^\n]+)/);
  if (summaryMatch) {
    result.summary = summaryMatch[1].trim();
  }

  return result;
}

/**
 * 从 markdown 中提取简短摘要
 */
function extractBriefSummary(content: string): string {
  // 尝试提取 2.2 结论摘要
  const match = content.match(/### 2\.2 结论摘要\s*\n\s*([^\n]+)/);
  if (match) return match[1].trim();

  // 尝试提取第一段非表格内容
  const paragraphs = content.split(/\n\n+/);
  for (const p of paragraphs) {
    const trimmed = p.trim();
    if (trimmed && !trimmed.startsWith('|') && !trimmed.startsWith('#') && trimmed.length > 10) {
      return trimmed.substring(0, 200);
    }
  }
  return '标书审查报告';
}

/**
 * 根据 AgentChatResp 确定消息类型
 */
export function determineMessageType(resp: DrugAgentResp): MessageType {
  if (!resp) {
    return 'assistant_text';
  }
  if (resp.requiresClarification) {
    return 'assistant_clarify';
  }
  if (resp.report || resp.riskLevel) {
    return 'assistant_result_card';
  }
  return 'assistant_text';
}

/**
 * 映射为 ResultData
 */
export function mapToResultData(resp: DrugAgentResp): ResultData {
  return {
    riskLevel: resp.riskLevel,
    score: resp.score,
    summary: resp.summary,
    steps: resp.steps,
    evidenceList: resp.evidenceList,
    report: resp.report,
    confidence: resp.confidence,
    routeReason: resp.routeReason,
    scene: resp.scene,
    traceId: resp.traceId,
    suggestedActions: resp.suggestedActions,
    documentNames: resp.documentNames,
    documentIds: resp.documentIds,
  };
}

/**
 * 创建用户消息
 */
export function createUserMessage(content: string, attachments?: Attachment[]): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'user',
    type: 'user_text',
    content,
    createdAt: new Date().toISOString(),
    status: 'sent',
    attachments,
  };
}

/**
 * 创建上传中的消息
 */
export function createUploadingMessage(attachments: Attachment[]): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'assistant',
    type: 'uploading',
    content: '正在上传文件...',
    createdAt: new Date().toISOString(),
    status: 'sending',
    attachments,
  };
}

/**
 * 创建错误消息
 */
export function createErrorMessage(error: string): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'system',
    type: 'system_error',
    content: error,
    createdAt: new Date().toISOString(),
    status: 'error',
  };
}

/**
 * 创建普通助手消息
 */
export function createAssistantMessage(content: string): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'assistant',
    type: 'assistant_text',
    content,
    createdAt: new Date().toISOString(),
    status: 'sent',
  };
}

/**
 * 创建澄清消息
 */
export function createClarifyMessage(question: string): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'assistant',
    type: 'assistant_clarify',
    content: question,
    createdAt: new Date().toISOString(),
    status: 'sent',
  };
}

/**
 * 创建处理中消息（带思考步骤）
 */
export function createProcessingMessage(thinkingSteps: ThinkingStep[]): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'assistant',
    type: 'assistant_processing',
    content: '正在分析中，请稍候...',
    createdAt: new Date().toISOString(),
    status: 'sending',
    thinkingSteps: thinkingSteps,
  };
}
