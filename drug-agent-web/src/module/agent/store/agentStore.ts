/**
 * Agent Store
 * 管理 sessions, activeSessionId, messagesBySession, loading, sending, uploading, currentResult
 */

import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type {
  Message,
  DrugAgentResp,
  CreateSessionRequest,
  Attachment,
  ChatSession,
  ModelInfo,
  ChatRequest,
  ThinkingStep,
} from '../types/agent';
import * as agentApi from '../api/agentApi';
import {
  mapChatMessageToMessage,
  mapResponseToMessage,
  createUserMessage,
  createErrorMessage,
  createAssistantMessage,
  createProcessingMessage,
} from '../utils/messageMapper';
import {
  createInitialTenderReviewThinkingSteps,
  mergeNormalizedThinkingSteps,
  normalizeThinkingSteps,
} from '../utils/thinkingStepNormalizer';

export const useAgentStore = defineStore('agent', () => {
  // ==================== 状态定义 ====================

  /** 会话列表 */
  const sessions = ref<ChatSession[]>([]);

  /** 当前激活的会话 ID */
  const activeSessionId = ref<string | null>(null);

  /** 侧边栏及主视图状态 */
  const activeView = ref<'WORKSPACE' | 'TASKS' | 'KNOWLEDGE' | 'BENCHMARK'>('WORKSPACE');
  const isSidebarCollapsed = ref(false);

  /** 按会话 ID 存储的消息映射 */
  const messagesBySession = ref<Record<string, Message[]>>({});

  /** 全局加载状态 */
  const loading = ref(false);

  /** 发送消息状态 */
  const sending = ref(false);

  /** 流式输出状态 */
  const streaming = ref(false);
  const streamingContent = ref('');
  const streamingMessageId = ref<string | null>(null);

  /** 语音状态 */
  const speechRecognizing = ref(false);
  const speechSynthesizing = ref(false);

  /** 上传文件状态 */
  const uploading = ref(false);

  /** 当前结果数据（用于详情抽屉） */
  const currentResult = ref<DrugAgentResp | null>(null);

  /** 当前上传的文件列表 */
  const pendingFiles = ref<Attachment[]>([]);

  /** 进行中的思考步骤（实时更新） */
  const thinkingStepsInProgress = ref<ThinkingStep[]>([]);

  /** 当前处理状态 */
  const processingStatus = ref<'idle' | 'uploading' | 'processing' | 'done'>('idle');

  /** 可用模型列表 */
  const availableModels = ref<ModelInfo[]>([]);

  /** 当前选中的模型 */
  const currentModel = ref<string>('minimax');

  /** 评测历史记录列表 */
  const benchmarkRecords = ref<any[]>([]);

  /** 评测加载状态 */
  const benchmarkLoading = ref(false);

  /**
   * 会话级已上传文件ID列表。
   * key: sessionId, value: 该会话上传过的所有 fileId 数组（去重）
   * 作用：让后续 sendMessage 发文字时，后端知道这个会话有哪些历史文件可审查
   */
  const sessionFileIds = ref<Record<string, string[]>>({});

  // ==================== 计算属性 ====================

  /** 当前会话 */
  const activeSession = computed(() => {
    return sessions.value.find((s) => s.id === activeSessionId.value) || null;
  });

  /** 当前会话的消息列表 */
  const activeMessages = computed(() => {
    if (!activeSessionId.value) return [];
    return messagesBySession.value[activeSessionId.value] || [];
  });

  /** 当前会话的附件列表（去重） */
  const activeFiles = computed(() => {
    if (!activeSessionId.value) return [];
    const messages = messagesBySession.value[activeSessionId.value] || [];
    const fileIds = new Set<string>();
    const files: Attachment[] = [];
    for (const msg of messages) {
      if (msg.attachments) {
        for (const file of msg.attachments) {
          if (!fileIds.has(file.id)) {
            fileIds.add(file.id);
            files.push(file);
          }
        }
      }
    }
    return files;
  });

  // ==================== Actions ====================

  /**
   * 加载所有会话
   */
  async function loadSessions() {
    loading.value = true;
    try {
      const res = await agentApi.getSessions();
      if (res.data.code === 200 || res.data.code === 0) {
        sessions.value = res.data.data || [];
      }
    } catch (error) {
      console.error('加载会话列表失败:', error);
    } finally {
      loading.value = false;
    }
  }

  /**
   * 创建新会话
   */
  async function createSession(data?: CreateSessionRequest) {
    loading.value = true;
    try {
      const res = await agentApi.createSession({
        title: data?.title || '新对话',
        scene: data?.scene || 'general',
      });
      if (res.data.code === 200 || res.data.code === 0) {
        const newSession = res.data.data;
        if (newSession) {
          sessions.value.unshift(newSession);
          await selectSession(newSession.id);
          return newSession;
        }
      }
    } catch (error) {
      console.error('创建会话失败:', error);
    } finally {
      loading.value = false;
    }
    return null;
  }

  /**
   * 选择会话
   */
  async function selectSession(sessionId: string) {
    activeSessionId.value = sessionId;

    // 报告抽屉绑定的是全局 currentResult，切换会话时必须清空，
    // 否则会一直显示上一会话的比对结果（文件名与当前上传不一致）。
    currentResult.value = null;

    // 如果没有该会话的消息，则加载
    if (!messagesBySession.value[sessionId]) {
      await loadMessages(sessionId);
    }
  }

  /**
   * 加载指定会话的消息
   */
  async function loadMessages(sessionId: string) {
    try {
      const res = await agentApi.getSessionById(sessionId);
      if (res.data.code === 200 || res.data.code === 0) {
        const session = res.data.data;
        if (session?.messages) {
          messagesBySession.value[sessionId] = session.messages.map(
            mapChatMessageToMessage
          );

          // 从历史消息的 metadata 中恢复 fileIds
          const restoredFileIds = new Set<string>();
          for (const msg of session.messages) {
            if (msg.role === 'assistant' && msg.metadata) {
              try {
                const meta = typeof msg.metadata === 'string' ? JSON.parse(msg.metadata) : msg.metadata;
                if (meta.fileIds && Array.isArray(meta.fileIds)) {
                  meta.fileIds.forEach((id: string) => restoredFileIds.add(id));
                }
              } catch (e) {
                // ignore parsing errors
              }
            }
          }
          // 补充从 session 级附件列表恢复（覆盖 metadata 解析失败的情况）
          // session.files 是 getSessionById 返回的 oss_file 列表
          if (session.files && Array.isArray(session.files)) {
            for (const file of session.files) {
              if (file.id) restoredFileIds.add(file.id);
            }
          }
          if (restoredFileIds.size > 0) {
            sessionFileIds.value[sessionId] = Array.from(restoredFileIds);
          }
        }
      }
    } catch (error) {
      console.error('加载消息失败:', error);
    }
  }

  /**
   * 删除会话
   */
  async function removeSession(sessionId: string) {
    try {
      const res = await agentApi.deleteSession(sessionId);
      if (res.data.code === 200 || res.data.code === 0) {
        // 从列表中移除
        sessions.value = sessions.value.filter((s) => s.id !== sessionId);
        // 清除消息
        delete messagesBySession.value[sessionId];
        // 清除会话级文件ID列表
        delete sessionFileIds.value[sessionId];
        // 如果删除的是当前会话，选中第一个
        if (activeSessionId.value === sessionId) {
          activeSessionId.value = sessions.value[0]?.id || null;
        }
      }
    } catch (error) {
      console.error('删除会话失败:', error);
    }
  }

  /**
   * 清空所有会话
   */
  async function clearAllSessions() {
    const sessionIds = sessions.value.map((s) => s.id);
    for (const id of sessionIds) {
      await removeSession(id);
    }
  }

  /**
   * 更新会话标题
   */
  async function updateSessionTitle(sessionId: string, title: string) {
    try {
      const res = await agentApi.updateSessionTitle(sessionId, { title });
      if (res.data.code === 200 || res.data.code === 0) {
        const session = sessions.value.find((s) => s.id === sessionId);
        if (session) {
          session.title = title;
        }
      }
    } catch (error) {
      console.error('更新会话标题失败:', error);
    }
  }

  /**
   * 发送文本消息
   */
  async function sendMessage(content: string) {
    if (!activeSessionId.value) {
      // 如果没有活动会话，先创建一个
      await createSession();
    }

    if (!activeSessionId.value) return;

    sending.value = true;

    // 添加用户消息
    const userMsg = createUserMessage(content);
    addMessage(userMsg);

    try {
      // 取出当前会话的历史文件ID，追加到请求中
      const currentFileIds = sessionFileIds.value[activeSessionId.value!] || [];
      console.log('[agentStore] sendMessage fileIds, sessionId=' + activeSessionId.value + ', count=' + currentFileIds.length + ', ids=' + JSON.stringify(currentFileIds));

      // 根据是否有文件判断是否使用流式（复杂场景如标书审查不使用流式）
      const useStream = currentFileIds.length === 0 && !activeSession.value?.scene;

      if (useStream) {
        // 使用流式输出
        await sendMessageStream({
          query: content,
          sessionId: activeSessionId.value,
          userId: 'default_user',
          sceneHint: activeSession.value?.scene,
          model: currentModel.value,
          stream: true,
        });
      } else {
        // 使用同步输出
        const res = await agentApi.chat({
          query: content,
          sessionId: activeSessionId.value,
          userId: 'default_user',
          sceneHint: activeSession.value?.scene,
          model: currentModel.value,
          fileIds: currentFileIds.length > 0 ? currentFileIds : undefined,
        });

        if (res.data.code === 200 || res.data.code === 0) {
          const aiMsg = mapResponseToMessage(res.data.data);
          addMessage(aiMsg);

          // 如果是澄清消息，设置澄清问题
          if (aiMsg.type === 'assistant_clarify') {
            // 澄清消息已包含内容
          }

          // 实时更新会话标题
          if (res.data.data?.sessionTitle) {
            const session = sessions.value.find(s => s.id === activeSessionId.value);
            if (session) {
              session.title = res.data.data.sessionTitle;
            }
          }
        } else {
          const errorMsg = createErrorMessage(res.data.message || '请求失败');
          addMessage(errorMsg);
        }
      }
    } catch (error: unknown) {
      console.error('发送消息失败:', error);
      const errorMsg = createErrorMessage(
        error instanceof Error ? error.message : '网络错误，请稍后重试'
      );
      addMessage(errorMsg);
    } finally {
      sending.value = false;
    }
  }

  /**
   * 流式发送消息
   */
  async function sendMessageStream(req: ChatRequest) {
    if (!activeSessionId.value) return;

    // 重置流式状态
    streaming.value = true;
    streamingContent.value = '';
    streamingMessageId.value = null;

    // 创建初始助手消息
    const assistantMsg = createAssistantMessage('');
    assistantMsg.id = `stream_${Date.now()}`;
    streamingMessageId.value = assistantMsg.id;
    addMessage(assistantMsg);

    try {
      const stream = agentApi.streamChat(req);
      const reader = stream.getReader();

      let fullContent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        if (value?.answer) {
          fullContent += value.answer;
          streamingContent.value = fullContent;

          // 更新消息内容
          if (streamingMessageId.value) {
            const messages = messagesBySession.value[activeSessionId.value!];
            const msg = messages?.find(m => m.id === streamingMessageId.value);
            if (msg) {
              msg.content = fullContent;
            }
          }
        }

        // 如果收到完整响应（含结构化数据），处理最终结果
        if (value?.sessionTitle || value?.report || value?.riskLevel) {
          // 实时更新会话标题
          if (value?.sessionTitle) {
            const session = sessions.value.find(s => s.id === activeSessionId.value);
            if (session) {
              session.title = value.sessionTitle;
            }
          }
        }
      }

      // 流式结束，将最终内容的消息转换为完整消息
      if (streamingMessageId.value) {
        const messages = messagesBySession.value[activeSessionId.value!];
        const msg = messages?.find(m => m.id === streamingMessageId.value);
        if (msg && fullContent) {
          // 保留流式内容作为最终消息
          msg.content = fullContent;
        }
      }
    } catch (error: unknown) {
      console.error('[agentStore] 流式发送消息失败:', error);
      const errorMsg = createErrorMessage(
        error instanceof Error ? error.message : '网络错误，请稍后重试'
      );
      // 移除流式消息，添加错误消息
      if (streamingMessageId.value) {
        removeMessage(streamingMessageId.value);
      }
      addMessage(errorMsg);
    } finally {
      streaming.value = false;
      streamingContent.value = '';
      streamingMessageId.value = null;
    }
  }

  /**
   * 初始化标书审查思考步骤
   */
  function initTenderReviewThinkingSteps() {
    thinkingStepsInProgress.value = createInitialTenderReviewThinkingSteps();
  }

  /**
   * 上传文件
   */
  async function uploadFiles(
    files: File[],
    query?: string,
    submittedBy: string = 'anonymous'
  ) {
    if (!activeSessionId.value) {
      await createSession();
    }

    if (!activeSessionId.value || files.length === 0) return;

    uploading.value = true;
    processingStatus.value = 'uploading';

    const uploadedAttachments = files.map((f) => ({
      id: `file_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      name: f.name,
      size: f.size,
      type: f.type,
    }));
    const userContent = query?.trim() || '请审查这些文件';

    // 上传型对话也需要先展示用户气泡，避免界面看起来只有助手在回复。
    addMessage(createUserMessage(userContent, uploadedAttachments));

    // 初始化思考步骤
    initTenderReviewThinkingSteps();

    // 仅保留一个助手处理中态，避免同一次提交堆出多个回复框。
    const processingMsg = createProcessingMessage([...thinkingStepsInProgress.value]);
    processingMsg.content = '文件上传并分析中，请稍候...';
    addMessage(processingMsg);

    try {
      processingStatus.value = 'processing';

      // 尝试使用 SSE 流式接口
      const stream = agentApi.submitStream(
        query,
        activeSession.value?.scene,
        activeSessionId.value,
        'default_user',
        submittedBy,
        currentModel.value,
        files
      );
      const reader = stream.getReader();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // 只展示已推进到的步骤，并把后端步骤名归一化为前端展示口径
        if (value.completedSteps?.length || value.currentStep) {
          thinkingStepsInProgress.value = mergeNormalizedThinkingSteps(
            thinkingStepsInProgress.value,
            [...(value.completedSteps || []), value.currentStep]
          );

          // 同步更新 UI 绑定的 processingMsg
          if (activeSessionId.value) {
            const messages = messagesBySession.value[activeSessionId.value];
            const targetMsg = messages?.find(m => m.id === processingMsg.id);
            if (targetMsg) {
              targetMsg.thinkingSteps = [...thinkingStepsInProgress.value];
            }
          }

          // 更新 processingStatus 的 detail 为当前步骤的 currentDetail
          processingStatus.value = 'processing';
        }

        // 收到 finalResult=true 时，用 result 构建最终响应
        if (value.finalResult && value.result) {
          const result = value.result;
          result.thinkingSteps = normalizeThinkingSteps(result.thinkingSteps);

          removeMessage(processingMsg.id);

          // 用 mapResponseToMessage 将结果转换为消息
          const aiMsg = mapResponseToMessage(result);
          addMessage(aiMsg);

          // 实时更新会话标题（优先从 SSE 顶层获取，其次从 result 获取）
          const sessionTitle = value.sessionTitle || result.sessionTitle;
          if (sessionTitle) {
            const session = sessions.value.find(s => s.id === activeSessionId.value);
            if (session) {
              session.title = sessionTitle;
            }
          }

          // 从 SSE 事件顶层提取 documentIds 追加到 sessionFileIds
          const docIds = value.documentIds || result.documentIds;
          if (docIds && docIds.length > 0 && activeSessionId.value) {
            const existing = sessionFileIds.value[activeSessionId.value] || [];
            const merged = Array.from(new Set([...existing, ...docIds]));
            sessionFileIds.value[activeSessionId.value] = merged;
            console.log('[agentStore] 追加会话文件ID, sessionId=' + activeSessionId.value + ', 本次=' + docIds.length + '个, 累计=' + merged.length + '个');
          }

          // 停止 thinkingStepsInProgress 的更新
          thinkingStepsInProgress.value = [];
          return result;
        }
      }
    } catch (error: unknown) {
      // SSE 出错，降级为同步接口
      console.error('[agentStore] submitStream 出错，降级为同步接口:', error);

      try {
        const res = await agentApi.submit(
          query,
          activeSession.value?.scene,
          activeSessionId.value,
          'default_user',
          submittedBy,
          currentModel.value,
          files
        );

        if (res.data.code === 200 || res.data.code === 0) {
          removeMessage(processingMsg.id);

          const aiMsg = mapResponseToMessage(res.data.data);
          addMessage(aiMsg);

          if (res.data.data?.sessionTitle) {
            const session = sessions.value.find(s => s.id === activeSessionId.value);
            if (session) {
              session.title = res.data.data.sessionTitle;
            }
          }

          const returnedFileIds: string[] = res.data.data?.fileIds || [];
          if (returnedFileIds.length > 0 && activeSessionId.value) {
            const existing = sessionFileIds.value[activeSessionId.value] || [];
            const merged = Array.from(new Set([...existing, ...returnedFileIds]));
            sessionFileIds.value[activeSessionId.value] = merged;
            console.log('[agentStore] 追加会话文件ID, sessionId=' + activeSessionId.value + ', 本次=' + returnedFileIds.length + '个, 累计=' + merged.length + '个');
          }

          return res.data.data;
        } else {
          // 移除上传中消息和处理中消息，添加错误消息
        removeMessage(uploadingMsg.id);
        removeMessage(processingMsg.id);
        removeMessage(processingMsg.id);
        const errorMsg = createErrorMessage(res.data.message || '上传失败');
        addMessage(errorMsg);
      }
    } catch (syncError: unknown) {
        removeMessage(processingMsg.id);
        const errorMsg = createErrorMessage(
          syncError instanceof Error ? syncError.message : '网络错误，请稍后重试'
        );
        addMessage(errorMsg);
      }
    } finally {
      uploading.value = false;
      processingStatus.value = 'idle';
      pendingFiles.value = [];
      // 清空进行中的思考步骤
      setTimeout(() => {
        thinkingStepsInProgress.value = [];
      }, 3000);
    }
    return null;
  }

  /**
   * 添加消息到当前会话
   */
  function addMessage(message: Message) {
    if (!activeSessionId.value) return;

    if (!messagesBySession.value[activeSessionId.value]) {
      messagesBySession.value[activeSessionId.value] = [];
    }
    messagesBySession.value[activeSessionId.value].push(message);
  }

  /**
   * 移除消息
   */
  function removeMessage(messageId: string) {
    if (!activeSessionId.value) return;

    const messages = messagesBySession.value[activeSessionId.value];
    if (messages) {
      const index = messages.findIndex((m) => m.id === messageId);
      if (index > -1) {
        messages.splice(index, 1);
      }
    }
  }

  /**
   * 设置当前结果
   */
  function setCurrentResult(result: DrugAgentResp | null) {
    currentResult.value = result;
  }

  /**
   * 添加上传文件到列表
   */
  function addUploadFile(file: Attachment) {
    pendingFiles.value.push(file);
  }

  /**
   * 移除上传文件
   */
  function removeUploadFile(fileId: string) {
    pendingFiles.value = pendingFiles.value.filter((f) => f.id !== fileId);
  }

  /**
   * 清空上传文件列表
   */
  function clearUploadFiles() {
    pendingFiles.value = [];
  }

  /**
   * 加载可用模型列表
   */
  async function loadModels() {
    try {
      const res = await agentApi.getModels();
      if (res.data.code === 200 || res.data.code === 0) {
        availableModels.value = res.data.data || [];
        const defaultModel = availableModels.value.find((m) => m.isDefault);
        if (defaultModel) {
          currentModel.value = defaultModel.model;
        } else if (availableModels.value.length > 0) {
          currentModel.value = availableModels.value[0].model;
        }
      }
    } catch (error) {
      console.error('加载模型列表失败:', error);
    }
  }

  /**
   * 切换当前模型
   */
  function setCurrentModel(model: string) {
    currentModel.value = model;
  }

  return {
    // 状态
    sessions,
    activeSessionId,
    activeView,
    isSidebarCollapsed,
    messagesBySession,
    loading,
    sending,
    streaming,
    streamingContent,
    streamingMessageId,
    speechRecognizing,
    speechSynthesizing,
    uploading,
    currentResult,
    pendingFiles,
    availableModels,
    currentModel,
    sessionFileIds,
    benchmarkRecords,
    benchmarkLoading,

    // 计算属性
    activeSession,
    activeMessages,
    activeFiles,

    // Actions
    loadSessions,
    createSession,
    selectSession,
    loadMessages,
    removeSession,
    clearAllSessions,
    updateSessionTitle,
    sendMessage,
    sendMessageStream,
    uploadFiles,
    addMessage,
    removeMessage,
    setCurrentResult,
    addUploadFile,
    removeUploadFile,
    clearUploadFiles,
    loadModels,
    setCurrentModel,
  };
});
