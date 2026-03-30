import type {
  ApiResponse,
  Attachment,
  ChatMessage,
  ChatRequest,
  ChatSession,
  CreateSessionRequest,
  DrugAgentResp,
  UpdateTitleRequest,
  ModelInfo,
} from '../types/agent';

type ApiEnvelope<T> = Promise<{ data: ApiResponse<T> }>;

const wait = (ms = 320) => new Promise((resolve) => setTimeout(resolve, ms));
const nowIso = () => new Date().toISOString();
const buildId = (prefix: string) =>
  `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

const sampleReport = {
  title: '医院智慧监管平台项目标书审查报告',
  overview:
    '系统从技术方案雷同、关键字段交叉复用、商务条款一致性三个维度完成审查，发现存在较明显的同源编写痕迹。',
  findings: [
    {
      id: 'finding_1',
      title: '技术方案存在大段结构同源',
      description:
        '两份投标文件在实施步骤、章节顺序与关键术语上高度一致，疑似来源于同一模板或协同编写。',
      severity: 'high' as const,
    },
    {
      id: 'finding_2',
      title: '联系方式和团队信息存在交叉复用',
      description:
        '关键联系人手机号尾号和项目经理履历表述高度接近，建议人工复核企业关联关系。',
      severity: 'medium' as const,
    },
  ],
  conclusion:
    '综合评分 84 分，建议列为高优先级人工复核对象，并结合历史项目库进一步交叉比对。',
  recommendations: [
    '优先核查投标主体之间的工商关联与授权链路',
    '复核技术方案原始编辑来源和交付模板',
    '对商务报价明细做梯度异常复检',
  ],
};

const buildTenderResult = (
  summary: string,
  answer: string,
  score = 84
): DrugAgentResp => ({
  sessionId: '',
  traceId: buildId('trace'),
  scene: 'TENDER_REVIEW',
  routeReason: '识别到用户存在标书审查意图，已在 mock 模式下执行审查流程',
  routeSource: 'mock_tool',
  confidence: 0.96,
  summary,
  answer,
  riskLevel: score >= 80 ? 'high' : score >= 60 ? 'medium' : 'low',
  score,
  managementSummary: '建议进入人工复核流程并保留证据链。',
  suggestedActions: ['查看结构化风险详情', '导出审查报告', '发起人工复核任务'],
  caseId: buildId('case'),
  documentIds: [buildId('doc'), buildId('doc')],
  report: sampleReport,
  evidenceList: [
    {
      id: 'evidence_1',
      type: '文本雷同',
      content: '核心技术路线描述有连续 4 段结构和措辞高度一致。',
      source: '技术方案',
      similarity: 0.92,
    },
    {
      id: 'evidence_2',
      type: '字段交叉',
      content: '联系人电话尾号、邮箱命名规则和地址字段存在异常接近。',
      source: '商务响应表',
    },
    {
      id: 'evidence_3',
      type: '报价异常',
      content: '报价梯度呈现规则性递减，接近历史陪标样本。',
      source: '报价清单',
    },
  ],
  steps: [
    '上传文件并识别任务场景',
    '解析文档与提取章节树',
    '执行规则检查与风险融合',
    '生成结构化报告并输出用户说明',
  ],
});

const sessions: ChatSession[] = [
  {
    id: 'session_mock_1',
    title: '医院智慧平台招投标风险审查',
    scene: 'tender_review',
    userId: 'default_user',
    createdAt: nowIso(),
    updatedAt: nowIso(),
    deleted: false,
  },
  {
    id: 'session_mock_2',
    title: '合同预审与条款风险分析',
    scene: 'contract_precheck',
    userId: 'default_user',
    createdAt: nowIso(),
    updatedAt: nowIso(),
    deleted: false,
  },
];

const messageMap: Record<string, ChatMessage[]> = {
  session_mock_1: [
    {
      id: buildId('msg'),
      sessionId: 'session_mock_1',
      role: 'assistant',
      type: 'assistant_text',
      content:
        '欢迎进入标书审查工作台。你可以直接提问，也可以上传多份标书进行围标、雷同和风险线索分析。',
      createdAt: nowIso(),
    },
    {
      id: buildId('msg'),
      sessionId: 'session_mock_1',
      role: 'assistant',
      type: 'assistant_result_card',
      content: '已为你生成一份 mock 审查结果，可点击查看详情。',
      createdAt: nowIso(),
      metadata: buildTenderResult(
        '两份文件在技术方案、商务响应和联系人字段上存在较强同源迹象。',
        '初步审查显示，这组文件存在较高围标风险，建议优先查看技术方案雷同段落和联系方式交叉证据。'
      ) as Record<string, any>,
    },
  ],
  session_mock_2: [
    {
      id: buildId('msg'),
      sessionId: 'session_mock_2',
      role: 'assistant',
      type: 'assistant_text',
      content: '这里先保留了合同预审的 mock 会话，后续接入真实后端时可以继续扩展。',
      createdAt: nowIso(),
    },
  ],
};

function ok<T>(data: T, message = 'success'): { data: ApiResponse<T> } {
  return { data: { code: 200, message, data } };
}

function touchSession(sessionId: string) {
  const session = sessions.find((item) => item.id === sessionId);
  if (session) session.updatedAt = nowIso();
}

function pushAssistantResult(sessionId: string, resp: DrugAgentResp) {
  const list = messageMap[sessionId] || [];
  list.push({
    id: buildId('msg'),
    sessionId,
    role: 'assistant',
    type: resp.report || resp.riskLevel ? 'assistant_result_card' : 'assistant_text',
    content: resp.answer || resp.summary || '',
    createdAt: nowIso(),
    metadata: resp as Record<string, any>,
  });
  messageMap[sessionId] = list;
  touchSession(sessionId);
}

function buildReply(query = ''): DrugAgentResp {
  const normalized = query.toLowerCase();
  if (
    normalized.includes('审查') ||
    normalized.includes('标书') ||
    normalized.includes('围标') ||
    normalized.includes('雷同') ||
    normalized.includes('查重')
  ) {
    return buildTenderResult(
      '发现技术方案、联系方式和报价结构均出现高风险同源线索。',
      '我已经按 mock 审查流程跑了一遍。这批文件最值得优先关注的是技术方案雷同、联系人字段交叉以及报价梯度异常，建议先打开详情查看证据链。'
    );
  }

  return {
    traceId: buildId('trace'),
    scene: 'GENERAL',
    routeReason: 'mock 对话回复',
    confidence: 0.82,
    summary: '已在 mock 模式下返回一条演示回复。',
    answer:
      '当前前端运行在 mock 模式。我已经把页面做成可演示版本，你后续可以继续问我标书审查、合同预审或上传文件演示流程。',
  };
}

export async function getSessions(): ApiEnvelope<ChatSession[]> {
  await wait();
  return ok([...sessions].sort((a, b) => +new Date(b.updatedAt) - +new Date(a.updatedAt)));
}

export async function getSessionById(id: string): ApiEnvelope<ChatSession> {
  await wait();
  const session = sessions.find((item) => item.id === id) || sessions[0];
  return ok({ ...session, messages: (messageMap[id] || []).map((msg) => ({ ...msg })) });
}

export async function getModels(): ApiEnvelope<ModelInfo[]> {
  await wait(100);
  return ok([
    { model: 'minimax', name: 'MiniMax', isDefault: true, available: true },
    { model: 'dashscope', name: '阿里云百炼', isDefault: false, available: true },
  ]);
}

export async function createSession(data: CreateSessionRequest): ApiEnvelope<ChatSession> {
  await wait(180);
  const session: ChatSession = {
    id: buildId('session'),
    title: data.title || '新的审查任务',
    scene: data.scene || 'general',
    userId: 'default_user',
    createdAt: nowIso(),
    updatedAt: nowIso(),
    deleted: false,
  };
  sessions.unshift(session);
  messageMap[session.id] = [
    {
      id: buildId('msg'),
      sessionId: session.id,
      role: 'assistant',
      type: 'assistant_text',
      content: '新的 mock 会话已创建，可以直接提问或上传标书文件。',
      createdAt: nowIso(),
    },
  ];
  return ok(session);
}

export async function updateSessionTitle(
  id: string,
  data: UpdateTitleRequest
): ApiEnvelope<null> {
  await wait(120);
  const session = sessions.find((item) => item.id === id);
  if (session) {
    session.title = data.title;
    touchSession(id);
  }
  return ok(null);
}

export async function deleteSession(id: string): ApiEnvelope<null> {
  await wait(120);
  const index = sessions.findIndex((item) => item.id === id);
  if (index >= 0) {
    sessions.splice(index, 1);
    delete messageMap[id];
  }
  return ok(null);
}

export async function searchSessions(q: string): ApiEnvelope<ChatSession[]> {
  await wait();
  const keyword = q.trim().toLowerCase();
  return ok(sessions.filter((item) => item.title.toLowerCase().includes(keyword)));
}

export async function getMessages(sessionId: string): ApiEnvelope<ChatMessage[]> {
  await wait();
  return ok((messageMap[sessionId] || []).map((msg) => ({ ...msg })));
}

export async function chat(req: ChatRequest): ApiEnvelope<DrugAgentResp> {
  await wait(700);
  const sessionId = req.sessionId || sessions[0]?.id || 'session_mock_1';
  const list = messageMap[sessionId] || [];
  list.push({
    id: buildId('msg'),
    sessionId,
    role: 'user',
    type: 'user_text',
    content: req.query || '',
    createdAt: nowIso(),
  });
  messageMap[sessionId] = list;
  const resp = buildReply(req.query);
  resp.sessionId = sessionId;
  pushAssistantResult(sessionId, resp);
  return ok(resp);
}

export async function submit(
  query: string | undefined,
  sceneHint: string | undefined,
  sessionId: string | undefined,
  userId: string | undefined,
  submittedBy: string,
  model: string | undefined,
  files: File[]
): ApiEnvelope<DrugAgentResp> {
  await wait(900);
  const targetSessionId = sessionId || sessions[0]?.id || 'session_mock_1';
  const attachments: Attachment[] = files.map((file) => ({
    id: buildId('file'),
    name: file.name,
    size: file.size,
    type: file.type,
  }));
  const list = messageMap[targetSessionId] || [];
  list.push({
    id: buildId('msg'),
    sessionId: targetSessionId,
    role: 'user',
    type: 'user_text',
    content: query || '请审查我上传的文件',
    createdAt: nowIso(),
    metadata: { attachments, sceneHint, userId, submittedBy, model },
  });
  messageMap[targetSessionId] = list;

  const fileSummary = files.map((item) => item.name).join('、');
  const resp = buildTenderResult(
    `已对 ${files.length} 份文件完成 mock 审查，重点风险集中在技术方案和商务字段。`,
    `我已经在 mock 模式下完成对 ${fileSummary} 的审查。当前结果显示存在较高同源风险，建议优先查看技术方案雷同证据和报价异常信号。`,
    86
  );
  resp.sessionId = targetSessionId;
  resp.documentIds = attachments.map((item) => item.id);
  pushAssistantResult(targetSessionId, resp);
  return ok(resp);
}

export async function addMessage(
  sessionId: string,
  content: string,
  role = 'user'
): ApiEnvelope<any> {
  await wait(300);
  const list = messageMap[sessionId] || [];
  list.push({
    id: buildId('msg'),
    sessionId,
    role: role as 'user' | 'assistant' | 'system',
    type: role === 'user' ? 'user_text' : 'assistant_text',
    content,
    createdAt: nowIso(),
  });
  messageMap[sessionId] = list;
  touchSession(sessionId);
  return ok({ message: content });
}
