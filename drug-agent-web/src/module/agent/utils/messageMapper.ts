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
import { normalizeThinkingSteps } from './thinkingStepNormalizer';

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
    thinkingSteps: normalizeThinkingSteps(resp.thinkingSteps),
    reasoningContent: resp.reasoningContent,
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
  let metadata = chatMsg.metadata as any;
  if (typeof metadata === 'string') {
    try {
      metadata = JSON.parse(metadata);
    } catch (e) {
      console.warn('Failed to parse metadata:', e);
    }
  }

  // 如果有 markdown 内容且能解析出数据，用 markdown 数据补充/覆盖 metadata
  if (chatMsg.content && isReportMarkdown(chatMsg.content)) {
    const markdownData = parseReportFromMarkdown(chatMsg.content);
    // 用 markdown 数据补充 metadata
    if (!metadata) metadata = {};
    metadata.riskLevel = metadata.riskLevel || markdownData.riskLevel;
    metadata.score = metadata.score || markdownData.score;
    metadata.summary = metadata.summary || markdownData.summary;
    metadata.report = metadata.report || markdownData.report;
    metadata.documentNames = metadata.documentNames || markdownData.documentNames;
    metadata.traceId = metadata.traceId || markdownData.traceId;
  }

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
    message.thinkingSteps = normalizeThinkingSteps(metadata.thinkingSteps as ThinkingStep[]);
  }
  if (metadata?.reasoningContent && typeof metadata.reasoningContent === 'string') {
    message.reasoningContent = metadata.reasoningContent;
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
    content.includes('重点风险研判') ||
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

  // 提取风险等级 - 匹配 **综合风险等级**:** 高风险 ** 或 类似格式
  const levelMatch = content.match(/\*\*综合风险等级\*\*[^\n]*?\*\*([^\*]+)\*\*/i) ||
                      content.match(/综合风险等级[^\n]*?[:：]\s*([^\n,，]+)/i);
  if (levelMatch) {
    const level = levelMatch[1].toLowerCase();
    if (level.includes('高风险') || level.includes('high')) result.riskLevel = 'high';
    else if (level.includes('中风险') || level.includes('medium')) result.riskLevel = 'medium';
    else if (level.includes('低风险') || level.includes('low')) result.riskLevel = 'low';
    else if (level.includes('安全') || level.includes('safe')) result.riskLevel = 'safe';
  }

  // 提取风险分数 - 匹配 风险融合分值=100 或 风险参考分值: 100
  const scoreMatch = content.match(/风险融合分值[=\s:：]*(\d+)/i) ||
                     content.match(/风险参考分值[=\s:：]*(\d+)/i) ||
                     content.match(/\*\*(\d+)\s*\/ 100\*\*/);
  if (scoreMatch) {
    result.score = parseInt(scoreMatch[1], 10);
  }

  // 提取审查编号（作为 traceId）
  const traceMatch = content.match(/\*\*审查编号\*\*[^\n]*\|\s*([a-f0-9-]+)/i) ||
                     content.match(/审查编号[^\n]*\|\s*([a-f0-9-]+)/i);
  if (traceMatch) {
    result.traceId = traceMatch[1];
  }

  // 提取文档名称 - 匹配 审查对象 | 投标人A...md / 投标人B...md
  const docMatch = content.match(/\*\*审查对象\*\*[^\n]*\|\s*([^\n]+)/i) ||
                   content.match(/审查对象[^\n]*\|\s*([^\n]+)/i);
  if (docMatch) {
    const docs = docMatch[1].split(/\s*\/\s*/);
    result.documentNames = docs.map((d: string) => d.trim()).filter((d: string) => d.length > 0);
  }

  // 提取结论摘要 - 匹配 ### 2.2 结论摘要 后的内容 或 **结论摘要**
  const summarySectionMatch = content.match(/###\s*2\.2\s*结论摘要[^\n]*\n([^\n#]+)/i);
  if (summarySectionMatch) {
    result.summary = summarySectionMatch[1].trim();
  }

  // 从结论摘要中提取 rawHitCount（如"有效命中=10"）
  let rawHitCount = 0;
  const hitCountMatch = result.summary?.match(/有效命中[=\s]*(\d+)/i);
  if (hitCountMatch) {
    rawHitCount = parseInt(hitCountMatch[1], 10);
  }

  // 尝试从 markdown 中解析 riskItems（重点风险说明部分）
  const riskItems: any[] = [];
  const riskSectionMatch = content.match(/###\s*(?:重点风险说明|重点风险研判)\s*\n([\s\S]*?)(?=###|\n\n##|$)/i);
  if (riskSectionMatch) {
    const riskSection = riskSectionMatch[1];
    // 匹配表格行格式：| 序号 | 风险类型 | 风险等级 | 规则名称 |
    // cells[0]=序号, cells[1]=风险类型, cells[2]=风险等级, cells[3]=规则名称, ...
    const tableRows = riskSection.matchAll(/\|\s*\d+\s*\|[^\|]+\|[^\|]+\|[^\|]+\|/g);
    for (const rowMatch of tableRows) {
      const row = rowMatch[0];
      const cells = row.split('|').map(c => c.trim());
      if (cells.length >= 4) {
        const riskLevelStr = cells[2].toLowerCase(); // cells[2] 是风险等级
        const riskLevel = riskLevelStr.includes('高风险') ? 'high' :
                         riskLevelStr.includes('中风险') ? 'medium' :
                         riskLevelStr.includes('低风险') ? 'low' : 'unknown';
        riskItems.push({
          riskType: cells[1] || 'collusion', // cells[1] 是风险类型
          riskLevel,
          title: cells[3] || cells[1] || '风险项', // cells[3] 是规则名称
          summary: '',
        });
      }
    }
  }

  // 如果从摘要中解析到了 rawHitCount 但 riskItems 为空，用摘要中的数据构建 riskItems
  if (rawHitCount > 0 && riskItems.length === 0) {
    const summaryText = result.summary || '';
    // 从摘要中提取规则类型数
    const ruleTypeMatch = summaryText.match(/规则类型数[=\s]*(\d+)/i);
    const ruleTypeCount = ruleTypeMatch ? parseInt(ruleTypeMatch[1], 10) : 1;
    // 根据有效命中数构建风险项
    for (let i = 0; i < Math.min(rawHitCount, ruleTypeCount); i++) {
      riskItems.push({
        riskType: 'collusion',
        riskLevel: result.riskLevel || 'high',
        title: `风险特征${i + 1}`,
        summary: '',
      });
    }
  }

  // 构建 report 对象
  if (riskItems.length > 0 || result.score !== undefined || rawHitCount > 0) {
    result.report = {
      overview: {
        documentCount: result.documentNames?.length || 2,
        rawHitCount: rawHitCount || riskItems.length,
        effectiveHitCount: result.score !== undefined ? Math.round(result.score / 10) : (rawHitCount || 0),
        score: result.score,
        riskLevel: result.riskLevel,
      },
      riskItems,
    };
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
    evidenceGroups: resp.evidenceGroups,
    report: resp.report,
    reportData: resp.reportData,
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
export function createAssistantMessage(content: string, reasoningContent?: string): Message {
  return {
    id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
    role: 'assistant',
    type: 'assistant_text',
    content,
    createdAt: new Date().toISOString(),
    status: 'sent',
    reasoningContent,
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
