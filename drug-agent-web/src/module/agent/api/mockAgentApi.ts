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
  overview: {
    documentCount: 2,
    rawHitCount: 11,
    effectiveHitCount: 9,
    exemptionCount: 0,
    evidenceGroupCount: 3,
    evidenceItemCount: 6,
    score: 84,
    riskLevel: 'high',
    summary: '系统从技术方案雷同、关键字段交叉复用、商务条款一致性三个维度完成审查，发现存在较明显的同源编写痕迹。',
  },
  riskItems: [
    {
      riskType: 'collusion',
      riskLevel: 'high',
      title: '技术方案存在大段结构同源',
      summary: '两份投标文件在实施步骤、章节顺序与关键术语上高度一致，疑似来源于同一模板或协同编写。',
      reasonCodes: ['W-P1', 'W-P2'],
      evidenceTitles: ['技术方案第四章'],
      recommendations: ['优先核查投标主体之间的工商关联与授权链路'],
    },
    {
      riskType: 'collusion',
      riskLevel: 'medium',
      title: '联系方式和团队信息存在交叉复用',
      summary: '关键联系人手机号尾号和项目经理履历表述高度接近，建议人工复核企业关联关系。',
      reasonCodes: ['W-M3'],
      evidenceTitles: ['商务响应表联系人信息'],
      recommendations: ['复核技术方案原始编辑来源和交付模板'],
    },
  ],
  managementSummary: ['建议进入人工复核流程并保留证据链'],
  recommendedActions: ['查看结构化风险详情', '导出审查报告', '发起人工复核任务'],
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

const sampleReportData: any = {
  page1Summary: {
    riskLevel: 'high',
    conclusion:
      '本次比对发现报价异常、核心团队重复及关键条款高度相似等高置信风险信号，综合判定为高风险，建议立即启动人工复核。',
    recommendedAction:
      '优先核查投标主体关联关系、核心人员归属和报价形成依据，并同步评估是否满足升级处理条件。',
    riskScore: 92,
    coreEvidenceCount: 3,
    ruleHitCount: 11,
    coreRiskTop3: [
      {
        rank: 1,
        riskType: 'pricing',
        title: '报价异常',
        level: 'high',
        summary: '5 个分项报价出现固定价差，异常项分布一致，存在非独立报价形成迹象。',
        action: '优先核查报价编制依据、形成过程和历史报价记录。',
      },
      {
        rank: 2,
        riskType: 'team',
        title: '核心团队重复',
        level: 'high',
        summary: '项目经理、技术负责人等关键岗位多名成员重合，存在人员复用或主体关联风险。',
        action: '核验人员社保、任职证明、授权关系和项目履历。',
      },
      {
        rank: 3,
        riskType: 'text_similarity',
        title: '关键条款相似',
        level: 'high',
        summary: '风险识别与实施方案中多处关键条款表达高度一致，疑似存在内容协同或模板复用。',
        action: '人工对照原文，确认是否超出行业通用表述范围。',
      },
    ],
    riskDistribution: {
      报价风险: 'high',
      团队风险: 'high',
      文本相似风险: 'high',
      模板同源风险: 'medium',
      其他辅助风险: 'medium',
    },
    documents: [
      {
        docId: 'doc_mock_a',
        docName: '投标人A_晟博云创_W-M1测试标书.docx',
        party: '晟博云创',
        role: '对比文档 1',
        internalId: 'UPLOAD-0-8ab2',
      },
      {
        docId: 'doc_mock_b',
        docName: '投标人B_晟拓数科_W-M1测试标书.docx',
        party: '晟拓数科',
        role: '对比文档 2',
        internalId: 'UPLOAD-1-c91d',
      },
    ],
  },
  page2RiskOverview: {
    riskCategories: [
      {
        type: 'pricing',
        categoryName: '报价风险',
        level: 'high',
        hitCount: 2,
        needHumanReview: true,
        explanation: '检测到多个报价项呈现固定差值和高度一致的异常分布，存在协同报价特征。',
        representativeEvidence: '5 个报价项固定差值为 1000，异常项集中出现在实施、培训、测试等模块。',
        action: '核查报价编制依据、编制时间、参与人员和历史报价记录。',
      },
      {
        type: 'team',
        categoryName: '团队风险',
        level: 'high',
        hitCount: 2,
        needHumanReview: true,
        explanation: '发现多名核心成员在两份投标文件中重合，涉及项目经理、技术负责人等关键岗位。',
        representativeEvidence: '重复成员 5 名，关键岗位高度重合。',
        action: '核查社保归属、劳动关系、授权链和任职单位。',
      },
      {
        type: 'text_similarity',
        categoryName: '文本相似风险',
        level: 'high',
        hitCount: 3,
        needHumanReview: true,
        explanation: '技术方案、风险识别和服务承诺中出现高相似表达，疑似存在内容协同。',
        representativeEvidence: '风险识别条款核心句式完全一致，相似度 100%。',
        action: '人工对照原文并与历史投标文件交叉比对。',
      },
      {
        type: 'template',
        categoryName: '模板同源风险',
        level: 'medium',
        hitCount: 2,
        needHumanReview: true,
        explanation: '目录结构、章节顺序和错误表述存在同源特征，需要排查是否来自统一底稿。',
        representativeEvidence: '实施路径和章节编号排列方式高度一致。',
        action: '核查模板来源、编辑底稿和历史投标模板。',
      },
      {
        type: 'auxiliary',
        categoryName: '其他辅助风险',
        level: 'medium',
        hitCount: 2,
        needHumanReview: true,
        explanation: '联系人字段、附件命名和元数据存在辅助异常，可用于增强综合判断。',
        representativeEvidence: '联系人邮箱命名规则与附件元数据出现交叉复用。',
        action: '结合外围信息补强证据链，再决定是否升级处理。',
      },
    ],
  },
  page3CoreEvidence: {
    evidenceList: [
      {
        id: 'E01',
        type: '报价风险',
        level: 'high',
        confidence: '高',
        title: '5 个分项报价存在固定价差',
        explanation: '两份投标文件在多个报价项上呈现一致的固定差值，不符合独立编制的常见特征。',
        keyFindings: {
          异常报价项数量: 5,
          固定差值: 1000,
          涉及模块: '平台实施、培训交付、测试支持等',
        },
        basis: '命中规则 W-M1，且多处报价项呈现规律性固定差值。',
        action: '优先核查报价明细和形成依据。',
      },
      {
        id: 'E02',
        type: '团队风险',
        level: 'high',
        confidence: '高',
        title: '核心团队成员重复',
        explanation: '两份投标文件中发现多名核心成员姓名一致，涉及项目经理、技术负责人等关键角色。',
        keyFindings: {
          重复人数: 5,
          重复角色: '项目经理、技术负责人等',
          风险指向: '人员复用、主体关联、资质共享',
        },
        basis: '命中规则 W-M3，关键岗位重复度高。',
        action: '核查任职单位、社保归属和授权关系。',
      },
      {
        id: 'E03',
        type: '文本相似风险',
        level: 'high',
        confidence: '高',
        title: '关键风险条款高度相似',
        explanation: '风险识别与应对条款出现连续高相似表达，疑似存在内容协同或模板复用。',
        keyFindings: {
          高相似片段: 3,
          最高相似度: '100%',
          涉及章节: '风险识别、实施方案',
        },
        basis: '命中规则 W-P4/W-P5，多个关键句式和业务含义完全一致。',
        action: '人工对照原文并比对历史投标文件。',
      },
    ],
  },
  page4DetailComparison: {
    priceComparison: {
      headers: ['项目', '文档 A', '文档 B', '差异/说明', '判定'],
      rows: [
        {
          item: '平台软件开发与配置实施',
          docA: '820000',
          docB: '821000',
          diff: '固定差值 1000',
          verdict: '高度异常',
        },
        {
          item: '培训与交付文档服务',
          docA: '80000',
          docB: '81000',
          diff: '固定差值 1000',
          verdict: '高度异常',
        },
      ],
    },
    teamComparison: {
      headers: ['角色/字段', '文档 A', '文档 B', '判定'],
      rows: [
        {
          role: '项目经理',
          docA: '周明',
          docB: '周明',
          verdict: '异常',
        },
        {
          role: '技术负责人',
          docA: '李思源',
          docB: '李思源',
          verdict: '异常',
        },
      ],
    },
    textHighlights: [
      {
        category: '风险识别条款',
        textA: '患者主数据口径不统一，影响跨系统汇总和报表准确性……',
        textB: '患者主数据口径不统一，影响跨系统汇总和报表准确性……',
        similarity: '100%',
        verdict: '高度相似',
        analysis: '核心句式与业务含义完全一致，属于高风险相似表达。',
      },
      {
        category: '实施路径描述',
        textA: '通过统一接口网关实现主数据、接口监控和异常告警联动……',
        textB: '通过统一接口网关实现主数据、接口监控和异常告警联动……',
        similarity: '96%',
        verdict: '高度相似',
        analysis: '段落结构和关键术语完全重合，疑似共享底稿。',
      },
    ],
  },
  page5ActionSuggestions: {
    level1: {
      title: '一级动作｜立即执行',
      objective: '快速判断是否需要升级处理',
      actions: [
        {
          action: '人工复核核心证据 3 项：报价异常、团队重复、关键条款相似。',
          role: '评标专家',
          priority: '高',
        },
        {
          action: '复核两份文件是否由独立团队编制，并判断是否达到升级处理条件。',
          role: '风控专员',
          priority: '高',
        },
      ],
    },
    level2: {
      title: '二级动作｜进一步核验',
      objective: '补强证据链',
      actions: [
        {
          action: '核查主体工商关联关系、核心人员社保归属、授权链和联系方式。',
          role: '风控专员',
          priority: '高',
        },
        {
          action: '调取历史投标记录、模板底稿和异常样本做交叉比对。',
          role: '招采管理员',
          priority: '中',
        },
      ],
    },
    level3: {
      title: '三级动作｜必要时追溯',
      objective: '形成完整判断依据',
      actions: [
        {
          action: '必要时纳入专项审查或合规留档流程。',
          role: '合规负责人',
          priority: '中',
        },
      ],
    },
    retentionAdvice: [
      '保留审查报告、原始证据片段和人工复核记录。',
      '对升级事项同步留存外围核验材料和历史投标记录。',
    ],
  },
  page6Appendix: {
    ruleList: [
      {
        ruleId: 'R001',
        ruleCode: 'W-M1',
        description: '报价梯度异常',
      },
      {
        ruleId: 'R002',
        ruleCode: 'W-M3',
        description: '核心团队重叠',
      },
      {
        ruleId: 'R003',
        ruleCode: 'W-P4',
        description: '风险识别异常',
      },
    ],
    evidenceFragments: [
      {
        fragmentId: 'F01',
        content: '平台软件开发与配置实施：820000 vs 821000，固定差值 1000',
        source: '报价清单',
      },
      {
        fragmentId: 'F02',
        content: '项目经理：周明 / 周明',
        source: '核心团队表',
      },
      {
        fragmentId: 'F03',
        content: '患者主数据口径不统一，影响跨系统汇总和报表准确性……',
        source: '风险识别章节',
      },
    ],
    taskInfo: {
      taskId: 'TRACE-MOCK-20260414',
      reviewTime: '2026/4/14 10:30:00',
      modelVersion: 'mock-report-v2',
    },
  },
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
  reasoningContent:
    '我先比对两份标书的关键字段，再按相似度、联系信息和报价结构拆分风险点，最后整理成可直接展示的审查结论。',
  managementSummary: '建议进入人工复核流程并保留证据链。',
  suggestedActions: ['查看结构化风险详情', '导出审查报告', '发起人工复核任务'],
  caseId: buildId('case'),
  documentIds: [buildId('doc'), buildId('doc')],
  documentNames: sampleReportData.page1Summary.documents.map((item: any) => item.docName),
  report: sampleReport,
  reportData: sampleReportData,
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
  evidenceGroups: [
    {
      id: 'group_pricing',
      title: '报价异常',
      summary: '5 个报价项存在固定价差 1000，异常项分布一致。',
      similarity: 0.94,
      items: [
        { content: '820000', source: '文档A报价清单' },
        { content: '821000', source: '文档B报价清单' },
      ],
    },
    {
      id: 'group_team',
      title: '核心团队重复',
      summary: '项目经理、技术负责人等关键岗位成员重合。',
      similarity: 0.91,
      items: [
        { content: '项目经理：周明', source: '文档A核心团队表' },
        { content: '项目经理：周明', source: '文档B核心团队表' },
      ],
    },
    {
      id: 'group_text',
      title: '关键条款相似',
      summary: '风险识别与实施路径描述高度一致。',
      similarity: 1,
      contentA: '患者主数据口径不统一，影响跨系统汇总和报表准确性……',
      contentB: '患者主数据口径不统一，影响跨系统汇总和报表准确性……',
      items: [
        { content: '患者主数据口径不统一，影响跨系统汇总和报表准确性……', source: '文档A风险识别章节' },
        { content: '患者主数据口径不统一，影响跨系统汇总和报表准确性……', source: '文档B风险识别章节' },
      ],
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
        '欢迎进入标书审查工作台。你可以直接提问，也可以上传多份标书进行雷同和风险线索分析。',
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
        '初步审查显示，这组文件存在较高风险，建议优先查看技术方案雷同段落和联系方式交叉证据。'
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

function buildReasoningContent(query: string | undefined, isTenderReview: boolean): string {
  if (isTenderReview) {
    return '我先在 mock 数据里比对两份标书的关键字段，再把相似度、联系人和报价异常拆成可展示的结论。';
  }
  const normalized = (query || '').trim().toLowerCase();
  if (!normalized) {
    return '我先确认当前输入为空，再按通用问答流程组织一个简短回复。';
  }
  return '我先确认这是通用对话场景，再结合最近上下文组织回答，不会把标书审查的结论混到普通问答里。';
}

export function streamChat(req: ChatRequest): ReadableStream<DrugAgentResp> {
  const resp = buildReply(req.query);
  const isTenderReview =
    !!req.query &&
    (req.query.includes('瀹℃煡') ||
      req.query.includes('鏍囦功') ||
      req.query.includes('鍥存爣') ||
      req.query.includes('闆峰悓') ||
      req.query.includes('鏌ラ噸'));
  resp.reasoningContent = buildReasoningContent(req.query, isTenderReview);

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

  return new ReadableStream<DrugAgentResp>({
    async start(controller) {
      const reasoningChunks = resp.reasoningContent ? [resp.reasoningContent] : [];
      const answerText = resp.answer || resp.summary || '';
      const answerChunks = answerText.match(/.{1,24}/g) || [answerText];

      for (const chunk of reasoningChunks) {
        controller.enqueue({
          sessionId,
          traceId: resp.traceId,
          scene: resp.scene,
          reasoningContent: chunk,
          streamed: true,
        });
        await wait(60);
      }

      for (const chunk of answerChunks) {
        if (!chunk) continue;
        controller.enqueue({
          sessionId,
          traceId: resp.traceId,
          scene: resp.scene,
          answer: chunk,
          streamed: true,
        });
        await wait(60);
      }

      pushAssistantResult(sessionId, resp);
      controller.close();
    },
  });
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
    { model: 'dashscope', name: '横渡大模型', isDefault: false, available: true },
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
  await wait(100);
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
  resp.reasoningContent = buildReasoningContent(req.query, false);
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
  resp.reasoningContent = buildReasoningContent(query, true);
  resp.documentIds = attachments.map((item) => item.id);
  pushAssistantResult(targetSessionId, resp);
  return ok(resp);
}

export async function addMessage(
  sessionId: string,
  content: string,
  role = 'user'
): ApiEnvelope<{ message: string }> {
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
