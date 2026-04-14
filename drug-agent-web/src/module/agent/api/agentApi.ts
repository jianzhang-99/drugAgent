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
  SpeechRecognitionResponse,
  SpeechSynthesisResponse,
  ThinkingStep,
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
 * SSE 思考步骤进度事件
 * 与后端 ThinkingStepProgress 对齐
 */
export interface ThinkingStepProgressEvent {
  currentCode: string;
  currentTitle: string;
  currentStatus: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  currentDetail: string;
  completedSteps: ThinkingStep[];
  currentStep: ThinkingStep;
  finalResult: boolean;
  /** WorkflowResult，与后端对齐 */
  result?: {
    traceId?: string;
    scene?: string;
    answer?: string;
    summary?: string;
    riskLevel?: string;
    score?: number;
    report?: any;
    evidenceList?: any[];
    evidenceGroups?: any[];
    thinkingSteps?: ThinkingStep[];
    sessionTitle?: string;
    documentIds?: string[];
    documentNames?: string[];
  };
  /** 会话标题（SSE 流最终结果携带） */
  sessionTitle?: string;
  /** 文档ID列表（SSE 流最终结果携带） */
  documentIds?: string[];
}

/**
 * 文件上传对话（流式版本）
 * POST /api/agent/submit/stream (multipart/form-data)
 * 返回 SSE 流，包含实时思考步骤进度
 */
export function submitStream(
  query: string | undefined,
  sceneHint: string | undefined,
  sessionId: string | undefined,
  userId: string | undefined,
  submittedBy: string,
  model: string | undefined,
  files: File[]
): ReadableStream<ThinkingStepProgressEvent> {
  const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
  const url = BASE_URL + '/api/agent/submit/stream';

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

  const readableStream = new ReadableStream<ThinkingStepProgressEvent>({
    async start(controller) {
      try {
        const response = await fetch(url, {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          controller.close();
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          controller.close();
          return;
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = '';
        let currentData = '';

        const flushEvent = () => {
          if (!currentData || currentData === '[DONE]') {
            currentEvent = '';
            currentData = '';
            return;
          }
          try {
            const parsed = JSON.parse(currentData);
            const eventData = parsed.data || parsed;
            controller.enqueue(eventData as ThinkingStepProgressEvent);
          } catch (e) {
            // Ignore parse errors for incomplete JSON
          }
          currentEvent = '';
          currentData = '';
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.replace(/\r\n/g, '\n').split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              currentData = currentData ? `${currentData}\n${data}` : data;
            } else if (line.trim() === '') {
              if (!currentEvent || currentEvent === 'message') {
                flushEvent();
              } else {
                currentEvent = '';
                currentData = '';
              }
            }
          }
        }

        if (currentData) {
          flushEvent();
        }
      } catch (e) {
        console.error('[submitStream] SSE error:', e);
      } finally {
        controller.close();
      }
    },
  });

  return readableStream;
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

/**
 * 流式对话
 * POST /api/agent/chat/stream
 * 返回 SSE 流，需要使用 ReadableStream 处理
 */
export function streamChat(req: ChatRequest): ReadableStream<DrugAgentResp> {
  const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
  const url = BASE_URL + '/api/agent/chat/stream';

  const readableStream = new ReadableStream<DrugAgentResp>({
    async start(controller) {
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(req),
        });

        if (!response.ok) {
          controller.close();
          return;
        }

        const reader = response.body?.getReader();
        if (!reader) {
          controller.close();
          return;
        }

        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              if (data && data !== '[DONE]') {
                try {
                  // SSE data format: {"event":"message","data":{...}}
                  const parsed = JSON.parse(data);
                  // The actual data is in parsed.data due to ServerSentEvent structure
                  const eventData = parsed.data || parsed;
                  controller.enqueue(eventData as DrugAgentResp);
                } catch (e) {
                  // Ignore parse errors for incomplete JSON
                }
              }
            }
          }
        }
      } catch (e) {
        console.error('[streamChat] SSE error:', e);
      } finally {
        controller.close();
      }
    },
  });

  return readableStream;
}

/**
 * 语音识别（将音频转为文本）
 * POST /agent/speech/recognize
 * @param audioFile 音频文件
 * @param format 音频格式（pcm, wav, mp3, opus）
 * @param sampleRate 采样率（默认16000）
 * @param language 语言（默认zh）
 */
export async function speechRecognize(
  audioFile: File,
  format: string = 'pcm',
  sampleRate: number = 16000,
  language: string = 'zh'
): Promise<SpeechRecognitionResponse> {
  const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
  const url = BASE_URL + '/agent/speech/recognize';

  try {
    const formData = new FormData();
    formData.append('file', audioFile);
    formData.append('format', format);
    formData.append('sampleRate', String(sampleRate));
    formData.append('language', language);

    const response = await fetch(url, {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      return {
        success: false,
        errorCode: 'HTTP_ERROR',
        errorMessage: `请求失败: ${response.status}`,
      };
    }

    const data = await response.json();
    return data as SpeechRecognitionResponse;
  } catch (e) {
    console.error('[speechRecognize] error:', e);
    return {
      success: false,
      errorCode: 'NETWORK_ERROR',
      errorMessage: e instanceof Error ? e.message : '网络错误',
    };
  }
}

/**
 * 语音合成（将文本转为音频）
 * POST /agent/speech/synthesize
 * @param text 要转换的文本
 * @param voice 语音名称（默认friendly）
 * @param format 音频格式（默认mp3）
 * @param speed 语速（默认1.0）
 */
export async function speechSynthesize(
  text: string,
  voice: string = 'friendly',
  format: string = 'mp3',
  speed: number = 1.0
): Promise<SpeechSynthesisResponse> {
  const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
  const url = BASE_URL + '/agent/speech/synthesize';

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ text, voice, format, speed }),
    });

    if (!response.ok) {
      return {
        success: false,
        errorCode: 'HTTP_ERROR',
        errorMessage: `请求失败: ${response.status}`,
      };
    }

    const data = await response.json();
    return data as SpeechSynthesisResponse;
  } catch (e) {
    console.error('[speechSynthesize] error:', e);
    return {
      success: false,
      errorCode: 'NETWORK_ERROR',
      errorMessage: e instanceof Error ? e.message : '网络错误',
    };
  }
}
