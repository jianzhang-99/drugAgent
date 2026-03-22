// Mock Data Service for Drug Agent Demo
// 生成模拟数据用于演示

/**
 * 生成随机ID
 */
const generateId = () => {
  return Math.random().toString(36).substring(2, 15) + Date.now().toString(36)
}

/**
 * 生成随机traceId
 */
const generateTraceId = () => {
  return 'TR' + Date.now().toString(36).toUpperCase() + Math.random().toString(36).substring(2, 6).toUpperCase()
}

/**
 * 随机风险等级 (70% 低风险, 30% 高风险)
 */
const getRandomRiskLevel = () => {
  const rand = Math.random()
  if (rand < 0.7) return 'low'
  if (rand < 0.9) return 'medium'
  return 'high'
}

/**
 * 随机评分 (60-95)
 */
const getRandomScore = () => {
  return Math.floor(Math.random() * 36) + 60
}

// 场景配置
const sceneConfig = {
  tender: {
    scene: 'TENDER',
    sceneName: '标书审查',
    summaries: [
      '本次审查共发现3份投标文件存在异常相似段落，累计相似内容超过800字。建议进一步核实各投标方独立性问题。',
      '对比分析完成。发现A公司与B公司的技术方案雷同度达67%，存在围标嫌疑。建议启动专项调查。',
      '标书审查未发现明显违规行为。各投标方资质齐全，报价均在合理区间内。建议进入下一阶段评审。'
    ],
    managementSummary: [
      '已通知相关投标方补充说明',
      '建议增加现场答辩环节',
      '延长公示期至下周五',
      '移交风控部门进一步核查'
    ],
    recommendedActions: [
      '要求相关投标方在3个工作日内提交书面说明',
      '建议评标委员会增加技术方案原创性评审',
      '考虑延长投标有效期以便充分核查',
      '必要时引入第三方专业机构协助调查'
    ]
  },
  contract: {
    scene: 'CONTRACT',
    sceneName: '合同预审',
    summaries: [
      '合同预审发现5处潜在风险条款，其中2处涉及违约责任约定不明确，3处涉及付款条件存在履约风险。',
      '审查完成。合同整体结构规范，但发现价格调整条款存在漏洞，可能导致结算纠纷。建议修改后签署。',
      '未发现重大合规风险。合同条款基本完整，建议关注知识产权归属和保密条款的执行落地。'
    ],
    managementSummary: [
      '法务部门已审核通过',
      '风险条款已标红处理',
      '建议增加第三方担保',
      '合同模板库已更新'
    ],
    recommendedActions: [
      '与供应商协商修改违约条款',
      '增加履约保证金条款',
      '明确价格调整机制和触发条件',
      '完善争议解决条款，建议约定仲裁'
    ]
  },
  compliance: {
    scene: 'COMPLIANCE',
    sceneName: '合规预警',
    summaries: [
      '数据分析显示，近3个月骨科耗材采购量环比增长42%，其中某品牌植入物采购异常突出，建议重点关注。',
      '预警分析完成。发现2家供应商集中度超过安全阈值，存在供应单一化风险。建议优化供应商结构。',
      '合规检查通过。各项指标均在正常波动范围内，未发现明显异常交易行为。'
    ],
    managementSummary: [
      '已生成月度合规报告',
      '异常数据已标记待核查',
      '预警规则库已更新',
      '相关科室已通知自查'
    ],
    recommendedActions: [
      '对异常增长科室进行专项审计',
      '评估引入备选供应商的可行性',
      '完善耗材使用追溯机制',
      '定期监控采购集中度指标'
    ]
  }
}

// 生成执行步骤
const generateExecutionSteps = (scene, riskLevel) => {
  const steps = [
    { title: '数据采集', description: '从采购系统提取相关数据', status: 'completed' },
    { title: '合规比对', description: '对照法规和内部制度进行核查', status: 'completed' },
    { title: '风险识别', description: '运用AI模型识别潜在风险点', status: 'completed' },
    { title: '报告生成', description: '汇总分析结果生成预警报告', status: 'completed' }
  ]

  if (riskLevel === 'high') {
    steps.push({ title: '人工复核', description: '移交风控部门进行人工审核', status: 'pending' })
    steps.push({ title: '整改跟踪', description: '持续跟踪整改进展', status: 'pending' })
  }

  return steps
}

/**
 * 根据场景生成随机结果
 */
export const generateMockResult = (scene) => {
  const config = sceneConfig[scene] || sceneConfig.tender
  const riskLevel = getRandomRiskLevel()
  const score = getRandomScore()

  const summary = config.summaries[Math.floor(Math.random() * config.summaries.length)]

  return {
    id: generateId(),
    traceId: generateTraceId(),
    scene: config.scene,
    sceneName: config.sceneName,
    riskLevel,
    score,
    summary,
    managementSummary: [...config.managementSummary],
    recommendedActions: [...config.recommendedActions],
    executionSteps: generateExecutionSteps(scene, riskLevel),
    docCount: Math.floor(Math.random() * 10) + 3,
    createdAt: new Date().toISOString()
  }
}

/**
 * 初始会话数据
 */
export const sessions = [
  {
    id: 'session_001',
    title: '第一季度标书审查',
    scene: 'tender',
    createdAt: '2026-03-20T09:30:00Z',
    updatedAt: '2026-03-20T10:15:00Z',
    messages: [
      {
        id: 'msg_001',
        role: 'user',
        content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
        timestamp: '2026-03-20T09:30:00Z',
        attachments: [
          { name: '华瑞医药_投标书.pdf', size: 2.4 * 1024 * 1024 },
          { name: '康德医疗_技术方案.pdf', size: 1.8 * 1024 * 1024 }
        ]
      },
      {
        id: 'msg_002',
        role: 'agent',
        content: '收到您的请求。我现在开始对比分析上传的标书文件，将从技术方案、价格构成、服务承诺等多个维度进行雷同性检测。\n\n分析进行中，请稍候...',
        timestamp: '2026-03-20T09:30:15Z'
      },
      {
        id: 'msg_003',
        role: 'agent',
        content: '分析完成。以下是本次标书审查的主要发现：',
        timestamp: '2026-03-20T09:31:00Z',
        result: {
          id: 'rpt_001',
          traceId: 'TR7K9M2N',
          scene: 'TENDER',
          sceneName: '标书审查',
          riskLevel: 'high',
          score: 68,
          summary: '本次审查共发现3份投标文件存在异常相似段落，累计相似内容超过800字。建议进一步核实各投标方独立性问题。',
          managementSummary: [
            '已通知相关投标方补充说明',
            '建议增加现场答辩环节',
            '延长公示期至下周五',
            '移交风控部门进一步核查'
          ],
          recommendedActions: [
            '要求相关投标方在3个工作日内提交书面说明',
            '建议评标委员会增加技术方案原创性评审',
            '考虑延长投标有效期以便充分核查',
            '必要时引入第三方专业机构协助调查'
          ],
          executionSteps: [
            { title: '数据采集', description: '从采购系统提取相关数据', status: 'completed' },
            { title: '合规比对', description: '对照法规和内部制度进行核查', status: 'completed' },
            { title: '风险识别', description: '运用AI模型识别潜在风险点', status: 'completed' },
            { title: '报告生成', description: '汇总分析结果生成预警报告', status: 'completed' },
            { title: '人工复核', description: '移交风控部门进行人工审核', status: 'pending' },
            { title: '整改跟踪', description: '持续跟踪整改进展', status: 'pending' }
          ],
          docCount: 5,
          createdAt: '2026-03-20T09:31:00Z'
        }
      }
    ]
  },
  {
    id: 'session_002',
    title: '骨科耗材采购合同预审',
    scene: 'contract',
    createdAt: '2026-03-19T14:20:00Z',
    updatedAt: '2026-03-19T14:45:00Z',
    messages: [
      {
        id: 'msg_004',
        role: 'user',
        content: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
        timestamp: '2026-03-19T14:20:00Z',
        attachments: [
          { name: '骨科耗材采购合同_V2.3.docx', size: 856000 }
        ]
      },
      {
        id: 'msg_005',
        role: 'agent',
        content: '正在对合同进行全面审查，将重点关注价格条款、质量标准、交货周期、违约责任等关键风险点。',
        timestamp: '2026-03-19T14:20:30Z'
      },
      {
        id: 'msg_006',
        role: 'agent',
        content: '审查完成。合同整体结构规范，但发现价格调整条款存在漏洞。',
        timestamp: '2026-03-19T14:45:00Z',
        result: {
          id: 'rpt_002',
          traceId: 'TR3F8H1J',
          scene: 'CONTRACT',
          sceneName: '合同预审',
          riskLevel: 'medium',
          score: 78,
          summary: '审查完成。合同整体结构规范，但发现价格调整条款存在漏洞，可能导致结算纠纷。建议修改后签署。',
          managementSummary: [
            '法务部门已审核通过',
            '风险条款已标红处理',
            '建议增加第三方担保',
            '合同模板库已更新'
          ],
          recommendedActions: [
            '与供应商协商修改违约条款',
            '增加履约保证金条款',
            '明确价格调整机制和触发条件',
            '完善争议解决条款，建议约定仲裁'
          ],
          executionSteps: [
            { title: '数据采集', description: '从采购系统提取相关数据', status: 'completed' },
            { title: '合规比对', description: '对照法规和内部制度进行核查', status: 'completed' },
            { title: '风险识别', description: '运用AI模型识别潜在风险点', status: 'completed' },
            { title: '报告生成', description: '汇总分析结果生成预警报告', status: 'completed' }
          ],
          docCount: 3,
          createdAt: '2026-03-19T14:45:00Z'
        }
      }
    ]
  },
  {
    id: 'session_003',
    title: '月度合规预警分析',
    scene: 'compliance',
    createdAt: '2026-03-18T10:00:00Z',
    updatedAt: '2026-03-18T10:30:00Z',
    messages: [
      {
        id: 'msg_007',
        role: 'user',
        content: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
        timestamp: '2026-03-18T10:00:00Z'
      },
      {
        id: 'msg_008',
        role: 'agent',
        content: '开始数据分析，将从采购量异常、价格波动、供应商集中度等多个维度进行监测分析。',
        timestamp: '2026-03-18T10:00:30Z'
      },
      {
        id: 'msg_009',
        role: 'agent',
        content: '数据分析完成。整体合规状况良好，但发现一些需要关注的异常指标。',
        timestamp: '2026-03-18T10:30:00Z',
        result: {
          id: 'rpt_003',
          traceId: 'TR9K2M5P',
          scene: 'COMPLIANCE',
          sceneName: '合规预警',
          riskLevel: 'low',
          score: 88,
          summary: '合规检查通过。各项指标均在正常波动范围内，未发现明显异常交易行为。建议继续保持当前监控策略。',
          managementSummary: [
            '已生成月度合规报告',
            '异常数据已标记待核查',
            '预警规则库已更新',
            '相关科室已通知自查'
          ],
          recommendedActions: [
            '对异常增长科室进行专项审计',
            '评估引入备选供应商的可行性',
            '完善耗材使用追溯机制',
            '定期监控采购集中度指标'
          ],
          executionSteps: [
            { title: '数据采集', description: '从采购系统提取相关数据', status: 'completed' },
            { title: '合规比对', description: '对照法规和内部制度进行核查', status: 'completed' },
            { title: '风险识别', description: '运用AI模型识别潜在风险点', status: 'completed' },
            { title: '报告生成', description: '汇总分析结果生成预警报告', status: 'completed' }
          ],
          docCount: 8,
          createdAt: '2026-03-18T10:30:00Z'
        }
      }
    ]
  }
]

/**
 * 初始任务数据
 */
export const tasks = [
  {
    id: 'task_001',
    title: '第一季度标书雷同性检测',
    scene: 'tender',
    status: 'completed',
    priority: 'high',
    createdAt: '2026-03-20T09:30:00Z',
    completedAt: '2026-03-20T09:31:00Z'
  },
  {
    id: 'task_002',
    title: '骨科耗材合同风险审查',
    scene: 'contract',
    status: 'completed',
    priority: 'medium',
    createdAt: '2026-03-19T14:20:00Z',
    completedAt: '2026-03-19T14:45:00Z'
  },
  {
    id: 'task_003',
    title: '月度采购合规预警',
    scene: 'compliance',
    status: 'completed',
    priority: 'low',
    createdAt: '2026-03-18T10:00:00Z',
    completedAt: '2026-03-18T10:30:00Z'
  },
  {
    id: 'task_004',
    title: '供应商资质更新核查',
    scene: 'contract',
    status: 'pending',
    priority: 'medium',
    createdAt: '2026-03-22T08:00:00Z',
    dueAt: '2026-03-25T18:00:00Z'
  }
]

export default {
  generateMockResult,
  sessions,
  tasks,
  generateId,
  generateTraceId,
  getRandomRiskLevel
}
