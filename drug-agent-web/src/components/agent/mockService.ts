/**
 * Mock Service - 本地 Mock 驱动完整对话流程
 * 用于阶段一原型演示，不接真实后端
 *
 * 阶段三改动：
 * - 增加 USE_REAL_API 开关，默认 false（使用 mock）
 * - 当 USE_REAL_API 为 true 时，导出真实 API 方法供 fallback 使用
 */

import { ref } from 'vue'
import type { SessionSummary, SessionDetail, ChatMessage, TaskItem, ReportSummary, ReportDetail } from '@/store/agent/types'

// ==================== 配置开关 ====================

/**
 * 是否使用真实 API
 * true: 调用真实后端接口（需要后端服务运行）
 * false: 使用本地 mock 数据（默认，用于演示）
 */
export const USE_REAL_API = true

// ==================== Mock 数据 ====================

const mockSessions = ref<SessionDetail[]>([
  {
    id: 'sess_10293',
    title: '年度设备采购标书比对',
    scene: 'TENDER',
    dateGroup: '今天',
    updatedAt: new Date().toISOString(),
    messages: [
      {
        id: 'msg_001',
        role: 'user',
        type: 'user_text',
        content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
        attachments: ['样例A_XX医院标书.docx', '样例B_XX药房投标.pdf'],
        createdAt: new Date(Date.now() - 1000 * 60 * 2).toISOString()
      },
      {
        id: 'msg_002',
        role: 'assistant',
        type: 'assistant_result_card',
        content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。',
        result: {
          traceId: 'TRC-99281-A',
          scene: 'TENDER',
          riskLevel: 'High',
          score: 87,
          docCount: 2,
          summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。'
        },
        createdAt: new Date(Date.now() - 1000 * 60 * 2 + 1000 * 60 * 2).toISOString()
      }
    ]
  },
  {
    id: 'sess_10290',
    title: '骨科耗材供应商协议预审',
    scene: 'CONTRACT',
    dateGroup: '昨天',
    updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    messages: [
      {
        id: 'msg_003',
        role: 'user',
        type: 'user_text',
        content: '审查一下这份最新的采购合同框架，按知识库提取风险条款。',
        attachments: ['骨科耗材采购合同_v3.pdf'],
        createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString()
      },
      {
        id: 'msg_004',
        role: 'assistant',
        type: 'assistant_result_card',
        content: '合同预审完毕。整体结构完整，但提取到几处需要关注的潜在风险条款。',
        result: {
          traceId: 'TRC-99282-B',
          scene: 'CONTRACT',
          riskLevel: 'Medium',
          score: 65,
          docCount: 1,
          summary: '发现 3 条倾向于供应商的免责声明及付款周期违规条款。'
        },
        createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 + 1000 * 60).toISOString()
      }
    ]
  }
])

const mockTasks = ref<TaskItem[]>([
  {
    id: 'T-001',
    name: '年度设备采购标书分析',
    scene: 'TENDER',
    status: 'completed',
    progress: 100,
    updatedAt: new Date(Date.now() - 1000 * 60 * 10).toISOString(),
    traceId: 'TRC-99281-A',
    riskLevel: 'High',
    findings: '发现 87% 语义重合，疑似围标'
  }
])

// ==================== Mock Service API ====================

let taskIdCounter = 100
let msgIdCounter = 1000

/**
 * 模拟延迟
 */
function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 获取所有会话列表
 */
export function getSessions(): SessionSummary[] {
  return mockSessions.value.map(s => ({
    id: s.id,
    title: s.title,
    scene: s.scene,
    dateGroup: s.dateGroup,
    updatedAt: s.updatedAt
  }))
}

/**
 * 获取会话详情
 */
export function getSession(sessionId: string): SessionDetail | null {
  return mockSessions.value.find(s => s.id === sessionId) || null
}

/**
 * 创建新会话
 */
export function createSession(title: string = '新会话', scene: string = 'general'): SessionDetail {
  const newSession: SessionDetail = {
    id: `sess_${Date.now()}`,
    title,
    scene,
    dateGroup: '今天',
    updatedAt: new Date().toISOString(),
    messages: []
  }
  mockSessions.value.unshift(newSession)
  return newSession
}

/**
 * 获取任务列表
 */
export function getTasks(): TaskItem[] {
  return mockTasks.value
}

/**
 * 添加新任务
 */
export function addTask(name: string, scene: string): TaskItem {
  const newTask: TaskItem = {
    id: `T-${++taskIdCounter}`,
    name,
    scene,
    status: 'running',
    progress: 0,
    updatedAt: new Date().toISOString()
  }
  mockTasks.value.unshift(newTask)
  return newTask
}

/**
 * 更新任务进度
 */
export function updateTaskProgress(taskId: string, progress: number, status?: TaskItem['status']): void {
  const task = mockTasks.value.find(t => t.id === taskId)
  if (task) {
    task.progress = progress
    if (status) {
      task.status = status
    }
    task.updatedAt = new Date().toISOString()
  }
}

/**
 * 模拟 AI 消息生成（不操作 session.messages）
 * 返回最终消息类型，供 agentStore 添加到 session
 */
export async function simulateAIResponse(
  sessionId: string,
  content: string
): Promise<ChatMessage> {
  // 模拟任务执行
  await delay(1500)

  // 随机概率生成不同类型的回复
  const rand = Math.random()

  if (rand < 0.15) {
    // 15% 概率：澄清消息
    return {
      id: `msg_${++msgIdCounter}`,
      role: 'assistant',
      type: 'assistant_clarify',
      content: '为了更准确地完成分析，请确认以下几点：\n1. 您要对比的标书是否已经排版完成？\n2. 是否需要按照特定的评分标准进行比对？\n3. 发现疑似雷同时，您希望系统自动标记还是仅做提示？',
      createdAt: new Date().toISOString()
    }
  } else if (rand < 0.30) {
    // 15% 概率：带结果卡片的回复
    const isLowRisk = Math.random() > 0.5
    return {
      id: `msg_${++msgIdCounter}`,
      role: 'assistant',
      type: 'assistant_result_card',
      content: isLowRisk
        ? '审查完成。系统已完成对您上传文档的深度分析，未发现显著风险。'
        : '审查已完成。系统在多份文件中发现了高度雷同的排版与语义特征，已判定为高风险。请务必查看详细报告并进行人工复核。',
      result: {
        traceId: `TRC-${Date.now()}`,
        scene: 'TENDER',
        riskLevel: isLowRisk ? 'Low' : 'High',
        score: isLowRisk ? 15 : 87,
        docCount: 2,
        summary: isLowRisk
          ? '未发现显著语义重合，建议作为低风险基线。'
          : '发现显著的语义及排版雷同，疑似存在围标行为。'
      },
      createdAt: new Date().toISOString()
    }
  } else {
    // 70% 概率：普通文本回复
    const isLowRisk = Math.random() > 0.5
    return {
      id: `msg_${++msgIdCounter}`,
      role: 'assistant',
      type: 'assistant_text',
      content: isLowRisk
        ? '我已经完成了对您上传文档的审查。本次共审查了 2 份文档，未发现保留的高风险命中，建议将结果作为低风险基线。如需查看详细报告，请点击下方的结果卡片。'
        : '审查已完成。系统在多份文件中发现了高度雷同的排版与语义特征，已判定为高风险。请务必查看详细报告并进行人工复核。',
      createdAt: new Date().toISOString()
    }
  }
}

/**
 * 获取报告详情
 */
export function getReportDetail(traceId: string): ReportDetail | null {
  // 查找包含此 traceId 的消息
  for (const session of mockSessions.value) {
    for (const msg of session.messages) {
      if (msg.type === 'assistant_result_card' && msg.result.traceId === traceId) {
        const result = msg.result
        return {
          ...result,
          managementSummary: [
            `本次共审查 ${result.docCount} 份文档，综合风险等级为 ${result.riskLevel === 'High' ? 'HIGH' : result.riskLevel === 'Medium' ? 'MEDIUM' : 'LOW'}，融合分值为 ${result.score}。`,
            result.riskLevel === 'High'
              ? '触发了强制拦截规则，建议立即中止当前流程并启动专项审计调查。'
              : '当前未保留高风险命中，建议将结果作为低风险基线，并对关键章节进行抽样复核。'
          ],
          suggestedActions: [
            result.riskLevel === 'High'
              ? '导出证据链报告，并约谈相关供应商。'
              : '保留当前报告作为初筛结果，并结合业务经验抽样检查重点章节。'
          ],
          steps: ['scene_route', 'structured_load', 'rule_hit', 'false_positive_exemption', 'risk_fusion', 'evidence_assembly', 'report_generation'],
          evidenceList: result.riskLevel === 'High'
            ? [
                '片段A：设备的额定功率需满足 1500W-1800W 区间，且外壳需采用医用级 ABS 材质。',
                '片段B：该机器额定功率符合 1500W 至 1800W，外壳材料为医用级 ABS。'
              ]
            : []
        }
      }
    }
  }
  return null
}

// ==================== 导出方法供 fallback 使用 ====================

/**
 * 保留 simulateAIResponse 作为 fallback
 * 当真实 API 调用失败时，agentStore 会回退到此方法
 */

export const mockService = {
  getSessions,
  getSession,
  createSession,
  getTasks,
  addTask,
  updateTaskProgress,
  simulateAIResponse,
  getReportDetail,

  // 标记是否为真实 API 模式
  isRealApi: USE_REAL_API
}

export default mockService
