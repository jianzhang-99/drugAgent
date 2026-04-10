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
  if (metadata && (metadata.report || metadata.riskLevel || metadata.score !== undefined)) {
    message.result = mapToResultData(metadata);
  }
  // 从 metadata 恢复思考步骤
  if (metadata?.thinkingSteps && metadata.thinkingSteps.length > 0) {
    message.thinkingSteps = metadata.thinkingSteps as ThinkingStep[];
  }
  return message;
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
