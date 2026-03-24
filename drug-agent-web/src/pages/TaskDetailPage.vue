<template>
  <workspace-layout>
    <section class="detail-page">
      <header class="detail-header">
        <div>
          <button class="back-link" @click="router.push('/agent/tasks')">返回看板</button>
          <h1>任务详情</h1>
          <p>查看标书审查任务的文档列表、解析进度、结构化统计和前端侧审查报告。</p>
        </div>
        <div class="header-actions">
          <span v-if="isAutoRunning" class="auto-run-tip">系统正在自动解析文档...</span>
          <button class="secondary-btn" @click="parseAllDocuments" :disabled="parsingDocIds.length > 0 || !taskDetail">
            批量解析文档
          </button>
        </div>
      </header>

      <!-- 1. 任务概览 -->
      <section v-if="taskDetail" class="overview-grid">
        <article class="overview-card">
          <span class="overview-label">任务 ID</span>
          <strong>{{ taskDetail.caseId }}</strong>
        </article>
        <article class="overview-card">
          <span class="overview-label">任务状态</span>
          <div class="status-row">
            <span class="status-dot" :class="taskStatusClass"></span>
            <strong>{{ taskStatusText }}</strong>
          </div>
        </article>
        <article class="overview-card">
          <span class="overview-label">文档数量</span>
          <strong>{{ documents.length }} 份</strong>
        </article>
        <article class="overview-card">
          <span class="overview-label">提交人</span>
          <strong>{{ taskDetail.submittedBy || 'anonymous' }}</strong>
        </article>
      </section>

      <!-- 2. 阶段指示器 -->
      <section class="stage-indicator">
        <div class="stage" :class="{ active: parseStatus === 'parsing', completed: parseStatus === 'parsed', failed: parseStatus === 'failed' }">
          <span class="stage-icon">
            <span v-if="parseStatus === 'parsed'" class="icon-check">✓</span>
            <span v-else-if="parseStatus === 'failed'" class="icon-x">✕</span>
            <span v-else>1</span>
          </span>
          <span class="stage-label">文档解析</span>
          <span class="stage-status">{{ parseStatusText }}</span>
        </div>
        <div class="stage-arrow">→</div>
        <div class="stage" :class="{ active: reviewStatus === 'reviewing', completed: reviewStatus === 'completed', failed: reviewStatus === 'failed' }">
          <span class="stage-icon">
            <span v-if="reviewStatus === 'completed'" class="icon-check">✓</span>
            <span v-else-if="reviewStatus === 'failed'" class="icon-x">✕</span>
            <span v-else>2</span>
          </span>
          <span class="stage-label">风险审查</span>
          <span class="stage-status">{{ reviewStatusText }}</span>
        </div>
      </section>

      <!-- 3. 风险总览 -->
      <section v-if="taskDetail" class="risk-overview-section">
        <article class="risk-overview-card">
          <div class="panel-head">
            <h2>风险总览</h2>
            <span class="risk-level-badge" :class="riskOverviewClass">{{ riskOverviewLabel }}</span>
          </div>

          <div class="risk-summary">
            <div class="risk-stat">
              <span class="risk-stat-num" :class="highRiskClass">{{ riskStats.high }}</span>
              <span class="risk-stat-label">高风险</span>
            </div>
            <div class="risk-stat">
              <span class="risk-stat-num" :class="mediumRiskClass">{{ riskStats.medium }}</span>
              <span class="risk-stat-label">中风险</span>
            </div>
            <div class="risk-stat">
              <span class="risk-stat-num" :class="lowRiskClass">{{ riskStats.low }}</span>
              <span class="risk-stat-label">低风险</span>
            </div>
            <div class="risk-stat">
              <span class="risk-stat-num">{{ riskStats.pending }}</span>
              <span class="risk-stat-label">待判定</span>
            </div>
          </div>

          <div class="report-section">
            <h3>问题摘要</h3>
            <p class="report-summary">{{ generatedReport.summary }}</p>
          </div>

          <div class="report-section" v-if="generatedReport.risks.length > 0">
            <h3>风险提示</h3>
            <ul class="flat-list">
              <li v-for="risk in generatedReport.risks" :key="risk">{{ risk }}</li>
            </ul>
          </div>

          <div class="report-section" v-if="generatedReport.actions.length > 0">
            <h3>建议动作</h3>
            <ul class="flat-list">
              <li v-for="action in generatedReport.actions" :key="action">{{ action }}</li>
            </ul>
          </div>

          <div class="report-section capability-note">
            <p>当前版本重点识别围标线索，完整语义查重能力持续建设中。风险判定结果仅供参考，重大决策请结合人工复核。</p>
          </div>
        </article>
      </section>

      <!-- 4. 命中证据 -->
      <section class="evidence-section">
        <EvidencePanel :evidences="evidenceItems" />
      </section>

      <!-- 5. 文档列表 -->
      <section v-if="documents.length > 0" class="doc-list-panel">
        <div class="panel-head">
          <h2>文档列表</h2>
          <span class="panel-tip">支持单文档解析和批量解析</span>
        </div>

        <div class="doc-list">
          <article v-for="doc in documents" :key="doc.documentId" class="doc-card">
            <div class="doc-main">
              <div>
                <h3>{{ doc.filename }}</h3>
                <p>文档 ID：{{ doc.documentId }}</p>
              </div>
              <span class="status-chip" :class="statusClassMap[doc.status] || 'status-pending'">
                {{ docStatusText(doc.status) }}
              </span>
            </div>

            <div class="doc-actions">
              <span v-if="parsingDocIds.includes(doc.documentId)" class="parsing-indicator">
                <span class="parsing-dot"></span> 解析中...
              </span>
              <button v-else class="primary-btn" @click="parseDocumentAction(doc)" :disabled="doc.status === 'PARSED'">
                {{ doc.status === 'PARSED' ? '已解析' : '解析文档' }}
              </button>
            </div>

            <div v-if="parseResults[doc.documentId]" class="parse-result">
              <div class="result-stat">
                <span>段落块</span>
                <strong>{{ parseResults[doc.documentId].paragraphCount || 0 }}</strong>
              </div>
              <div class="result-stat">
                <span>表格块</span>
                <strong>{{ parseResults[doc.documentId].tableCount || 0 }}</strong>
              </div>
              <div class="result-stat">
                <span>字段数</span>
                <strong>{{ parseResults[doc.documentId].fieldCount || 0 }}</strong>
              </div>
              <div class="result-summary">
                <span>字段预览</span>
                <p>
                  {{
                    previewFieldNames(parseResults[doc.documentId]).length > 0
                      ? previewFieldNames(parseResults[doc.documentId]).join('、')
                      : '当前文档暂未提取出可展示字段。'
                  }}
                </p>
              </div>
            </div>
          </article>
        </div>
      </section>

      <!-- 6. 执行日志 -->
      <section class="log-section">
        <ExecutionLog :logs="executionLogs" @clear="clearExecutionLogs" />
      </section>

      <section v-if="documents.length === 0" class="empty-panel">
        <h2>暂无文档数据</h2>
        <p>当前任务正在处理中，请稍后刷新查看，或返回工作台发起新的审查任务。</p>
      </section>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../component/layout/WorkspaceLayout.vue'
import EvidencePanel from '../module/tender/component/EvidencePanel.vue'
import ExecutionLog from '../module/agent/component/ExecutionLog.vue'
import { listTenderReviewCases, parseTenderDocument } from '../module/agent/api/agent'
import {
  appendAuditLog,
  getAuditLogs,
  getTenderParseResults,
  getTenderTasks,
  getUserPreferences,
  setTenderParseResult,
  updateTenderTask,
  upsertTenderTask
} from '../utils/local-state'

const route = useRoute()
const router = useRouter()
const caseId = route.params.caseId
const preferences = getUserPreferences()
const autoStart = route.query.autostart === '1'

const taskDetail = ref(null)
const parsingDocIds = ref([])
const parseResults = ref(getTenderParseResults()[caseId] || {})
const hasTriggeredAutoParse = ref(false)

const statusClassMap = {
  PENDING: 'status-pending',
  PARSING: 'status-parsing',
  PARSED: 'status-success',
  FAILED: 'status-failed'
}

const previewFieldNames = (result) => {
  const fields = result?.fields || []
  return fields.slice(0, 4).map((field) => field.fieldName || field.fieldType || '未命名字段')
}

const docStatusText = (status) => {
  const textMap = {
    PENDING: '待解析',
    PARSING: '解析中',
    PARSED: '已解析',
    FAILED: '解析失败'
  }
  return textMap[status] || status
}

const documents = computed(() => {
  const task = taskDetail.value
  if (!task?.documentIds?.length) return []
  const filenames = task.filenames || []
  return task.documentIds.map((documentId, index) => ({
    documentId,
    filename: filenames[index] || `文档 ${index + 1}.docx`,
    status: parseResults.value[documentId]?.extractionMeta?.parseSuccess ? 'PARSED' : 'PENDING'
  }))
})

const parsedDocCount = computed(() => documents.value.filter((doc) => doc.status === 'PARSED').length)

const aggregateStats = computed(() => {
  return Object.values(parseResults.value).reduce((acc, result) => {
    acc.paragraphs += result?.paragraphCount || 0
    acc.tables += result?.tableCount || 0
    acc.fields += result?.fieldCount || 0
    return acc
  }, { paragraphs: 0, tables: 0, fields: 0 })
})

const fieldTypeMap = computed(() => {
  const bucket = {}
  Object.values(parseResults.value).forEach((result) => {
    ;(result?.fields || []).forEach((field) => {
      const key = field.fieldName || field.fieldType || '未知字段'
      bucket[key] = (bucket[key] || 0) + 1
    })
  })
  return bucket
})

const fieldTypeEntries = computed(() => Object.entries(fieldTypeMap.value).sort((a, b) => b[1] - a[1]).slice(0, 8))

const generatedReport = computed(() => {
  if (taskDetail.value?.report) {
    return {
      summary: taskDetail.value.summary || taskDetail.value.report?.overview?.summary || '系统已生成场景报告。',
      actions: taskDetail.value.report?.recommendedActions || ['查看详细报告并继续追问'],
      risks: taskDetail.value.report?.managementSummary || ['已生成结构化报告，请结合证据项继续复核。'],
      riskLabel: taskDetail.value.report?.overview?.riskLevel || taskDetail.value.status || '已完成',
      riskClass: (taskDetail.value.report?.overview?.riskLevel || '').includes('HIGH') ? 'risk-review' : 'risk-safe'
    }
  }

  const risks = []
  const actions = []

  if (parsedDocCount.value === 0) {
    return {
      summary: '当前任务尚未完成文档解析，建议先解析全部文档，再查看结构化审查报告。',
      actions: ['批量解析全部文档', '解析完成后查看字段统计与风险提示'],
      risks: ['当前没有足够结构化数据生成审查结论。'],
      riskLabel: '待处理',
      riskClass: 'risk-wait'
    }
  }

  if (fieldTypeMap.value['联系人电话'] || fieldTypeMap.value['contact_phone']) {
    risks.push('已提取联系方式相关字段，可重点比对不同投标文件中的电话号码和邮箱是否存在异常近邻或重复。')
    actions.push('建议人工复核联系方式跨文档异常')
  }

  if (fieldTypeMap.value['投标报价'] || fieldTypeMap.value['bid_price']) {
    risks.push('已提取报价字段，建议进一步关注报价梯度、分项价格差额和整体报价结构。')
    actions.push('建议人工复核报价梯度异常')
  }

  if (fieldTypeMap.value['团队成员'] || fieldTypeMap.value['team_member']) {
    risks.push('已提取团队成员字段，建议重点核查核心团队成员是否跨文档重合。')
    actions.push('建议重点核查联系人与团队成员交叉复用')
  }

  if (aggregateStats.value.fields === 0) {
    risks.push('文档已解析，但尚未提取出明显结构化字段，可能需要结合原文段落和表格人工复核。')
    actions.push('建议补充第三方证明材料')
  }

  if (!risks.length) {
    risks.push('当前未发现明显高风险提示，但仍建议结合原文段落和规则引擎做进一步核验。')
    actions.push('建议结合规则引擎做后续比对')
  }

  return {
    summary: `已完成 ${parsedDocCount.value} 份文档解析，累计提取 ${aggregateStats.value.fields} 个结构化字段，当前版本重点识别围标线索，完整语义查重能力持续建设中。`,
    actions,
    risks,
    riskLabel: aggregateStats.value.fields > 20 ? '需复核' : '初步完成',
    riskClass: aggregateStats.value.fields > 20 ? 'risk-review' : 'risk-safe'
  }
})

const taskStatusLabel = computed(() => {
  if (parsingDocIds.value.length > 0) return 'PARSING'
  if (documents.value.length > 0 && parsedDocCount.value === documents.value.length) return 'PARSED'
  return taskDetail.value?.status || 'PENDING'
})

const taskStatusText = computed(() => {
  const textMap = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    PARSING: '解析中',
    PARSED: '已完成',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return textMap[taskStatusLabel.value] || '待处理'
})

const taskStatusClass = computed(() => {
  const classMap = {
    PENDING: 'dot-pending',
    PROCESSING: 'dot-parsing',
    PARSING: 'dot-parsing',
    PARSED: 'dot-success',
    COMPLETED: 'dot-success',
    FAILED: 'dot-failed'
  }
  return classMap[taskStatusLabel.value] || 'dot-pending'
})

// 解析状态
const parseStatus = computed(() => {
  if (parsingDocIds.value.length > 0) return 'parsing'
  if (documents.value.length === 0) return 'pending'
  if (parsedDocCount.value === 0) return 'pending'
  if (parsedDocCount.value < documents.value.length) return 'partial'
  if (parsedDocCount.value === documents.value.length) return 'parsed'
  return 'pending'
})

const parseStatusText = computed(() => {
  const statusMap = {
    pending: '待解析',
    parsing: '解析中',
    partial: '部分解析',
    parsed: '已解析',
    failed: '解析失败'
  }
  return statusMap[parseStatus.value] || '待解析'
})

const parseStatusClass = computed(() => {
  const classMap = {
    pending: 'dot-pending',
    parsing: 'dot-parsing',
    partial: 'dot-parsing',
    parsed: 'dot-success',
    failed: 'dot-failed'
  }
  return classMap[parseStatus.value] || 'dot-pending'
})

// 审查状态
const reviewStatus = computed(() => {
  if (!taskDetail.value) return 'pending'
  // 如果有报告数据，认为审查已完成
  if (taskDetail.value?.report?.riskItems?.length || taskDetail.value?.report?.explanations) return 'completed'
  // 如果解析未完成，审查也不能开始
  if (parseStatus.value !== 'parsed') return 'pending'
  // 如果任务状态是审查中
  if (taskDetail.value.status === 'REVIEWING') return 'reviewing'
  if (taskDetail.value.status === 'FAILED') return 'failed'
  return 'pending'
})

const reviewStatusText = computed(() => {
  const statusMap = {
    pending: '待审查',
    reviewing: '审查中',
    completed: '已完成',
    failed: '审查失败'
  }
  return statusMap[reviewStatus.value] || '待审查'
})

const reviewStatusClass = computed(() => {
  const classMap = {
    pending: 'dot-pending',
    reviewing: 'dot-parsing',
    completed: 'dot-success',
    failed: 'dot-failed'
  }
  return classMap[reviewStatus.value] || 'dot-pending'
})

// 风险统计
const riskStats = computed(() => {
  const stats = { high: 0, medium: 0, low: 0, pending: 0 }
  if (!taskDetail.value?.report?.riskItems?.length) return stats

  taskDetail.value.report.riskItems.forEach(item => {
    const level = item.riskLevel?.toUpperCase()
    if (level === 'HIGH') stats.high++
    else if (level === 'MEDIUM') stats.medium++
    else if (level === 'LOW') stats.low++
    else stats.pending++
  })
  return stats
})

const riskOverviewLabel = computed(() => {
  const { high, medium, low } = riskStats.value
  if (high > 0) return '高风险'
  if (medium > 0) return '中风险'
  if (low > 0) return '低风险'
  return '待判定'
})

const riskOverviewClass = computed(() => {
  const { high, medium, low } = riskStats.value
  if (high > 0) return 'risk-high'
  if (medium > 0) return 'risk-medium'
  if (low > 0) return 'risk-low'
  return 'risk-pending'
})

const highRiskClass = computed(() => riskStats.value.high > 0 ? 'risk-num-high' : '')
const mediumRiskClass = computed(() => riskStats.value.medium > 0 ? 'risk-num-medium' : '')
const lowRiskClass = computed(() => riskStats.value.low > 0 ? 'risk-num-low' : '')

const isAutoRunning = computed(() => parsingDocIds.value.length > 0)

const executionLogs = computed(() => {
  const logs = getAuditLogs()
  return logs
    .filter(log => !log.caseId || log.caseId === caseId)
    .slice(0, 20)
})

const evidenceItems = computed(() => {
  if (!taskDetail.value?.report?.riskItems?.length) return []
  return taskDetail.value.report.riskItems.map(item => ({
    ruleName: item.title || '未知规则',
    level: item.riskLevel?.toUpperCase() || 'PENDING',
    matchedText: item.matchedText || item.reasonCodes?.[0] || '',
    explanation: item.summary || item.reasonCodes?.join('；') || '该规则用于检测相关风险项。',
    matchCount: 1,
    sourceDocuments: taskDetail.value.filenames || [],
    matchDetails: item.reasonCodes?.map(code => ({
      documentName: taskDetail.value.filenames?.[0] || '文档',
      matchedText: code
    })) || []
  }))
})

const clearExecutionLogs = () => {
  localStorage.removeItem('auditLogs')
}

const syncTaskStatus = () => {
  if (!taskDetail.value) return
  const nextStatus = taskStatusLabel.value
  taskDetail.value = { ...taskDetail.value, status: nextStatus }
  updateTenderTask(taskDetail.value.caseId, { status: nextStatus })
}

const loadTaskDetail = async () => {
  const localTask = getTenderTasks().find((item) => item.caseId === caseId)
  if (localTask) {
    taskDetail.value = localTask
  }

  try {
    const cases = await listTenderReviewCases()
    const matched = Array.isArray(cases) ? cases.find((item) => item.caseId === caseId) : null
    if (matched) {
      const mergedTask = {
        ...localTask,
        ...matched,
        filenames: localTask?.filenames || matched.documentIds?.map((_, index) => `文档 ${index + 1}.docx`) || []
      }
      taskDetail.value = mergedTask
      upsertTenderTask(mergedTask)
    }
  } catch (error) {
    console.error('Load task detail failed:', error)
  } finally {
    syncTaskStatus()
  }
}

const parseDocumentAction = async (doc) => {
  if (!taskDetail.value) return
  if (!parsingDocIds.value.includes(doc.documentId)) {
    parsingDocIds.value.push(doc.documentId)
  }
  syncTaskStatus()

  try {
    const result = await parseTenderDocument(taskDetail.value.caseId, doc.documentId)
    setTenderParseResult(taskDetail.value.caseId, doc.documentId, result)
    parseResults.value = getTenderParseResults()[taskDetail.value.caseId] || {}
    appendAuditLog({
      id: `audit-${Date.now()}`,
      type: 'DOC_PARSED',
      title: '完成文档解析',
      detail: `任务 ${taskDetail.value.caseId} 文档 ${doc.documentId} 解析成功`,
      createdAt: new Date().toISOString()
    })
    ElMessage.success(`文档 ${doc.filename} 解析成功`)
  } catch (error) {
    console.error('Parse document failed:', error)
  } finally {
    parsingDocIds.value = parsingDocIds.value.filter((id) => id !== doc.documentId)
    syncTaskStatus()
  }
}

const parseAllDocuments = async () => {
  for (const doc of documents.value) {
    if (doc.status === 'PARSED') continue
    // 顺序解析，避免同时打太多请求
    // eslint-disable-next-line no-await-in-loop
    await parseDocumentAction(doc)
  }
}

onMounted(async () => {
  await loadTaskDetail()
  if (!hasTriggeredAutoParse.value && (autoStart || preferences.autoParseAfterUpload) && parsedDocCount.value < documents.value.length && documents.value.length > 0) {
    hasTriggeredAutoParse.value = true
    parseAllDocuments()
  }
})
</script>

<style scoped>
.detail-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 40px;
}

/* 阶段指示器 */
.stage-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 24px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  margin-bottom: 28px;
}

.stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 32px;
  border-radius: 16px;
  background: #f8fafc;
  border: 2px solid var(--border-light);
  transition: all 0.3s ease;
  min-width: 140px;
}

.stage.active {
  background: #fff7ed;
  border-color: #ea580c;
}

.stage.completed {
  background: #ecfdf3;
  border-color: #15803d;
}

.stage.failed {
  background: #fef2f2;
  border-color: #dc2626;
}

.stage-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: white;
  border: 2px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 18px;
  color: var(--text-muted);
}

.stage.active .stage-icon {
  border-color: #ea580c;
  color: #ea580c;
}

.stage.completed .stage-icon {
  border-color: #15803d;
  color: #15803d;
}

.stage.failed .stage-icon {
  border-color: #dc2626;
  color: #dc2626;
}

.icon-check {
  font-size: 20px;
}

.icon-x {
  font-size: 20px;
}

.stage-label {
  font-weight: 800;
  font-size: 16px;
  color: var(--text-main);
}

.stage-status {
  font-size: 13px;
  color: var(--text-sub);
}

.stage-arrow {
  font-size: 28px;
  color: var(--text-muted);
  font-weight: 300;
}

/* 状态行 */
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.dot-pending {
  background: #4f46e5;
}

.dot-parsing {
  background: #ea580c;
  animation: pulse 1.5s infinite;
}

.dot-success {
  background: #15803d;
}

.dot-failed {
  background: #dc2626;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 32px;
}

.back-link {
  border: 0;
  background: transparent;
  color: var(--primary-color);
  font-weight: 700;
  padding: 0;
  margin-bottom: 10px;
  cursor: pointer;
}

h1 {
  margin: 0 0 8px;
  font-size: 30px;
  font-weight: 850;
}

.detail-header p,
.muted-text {
  margin: 0;
  color: var(--text-sub);
}

.overview-grid,
.report-grid {
  display: grid;
  gap: 20px;
  margin-bottom: 28px;
}

.overview-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.report-grid {
  grid-template-columns: 1.4fr 1fr;
}

.overview-card,
.doc-list-panel,
.empty-panel,
.summary-panel {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.overview-card,
.summary-panel {
  padding: 22px;
}

.overview-label {
  display: block;
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.panel-head h2,
.empty-panel h2,
.report-section h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}

.panel-tip,
.empty-panel p,
.report-summary {
  color: var(--text-sub);
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-tile,
.result-stat,
.result-summary {
  background: #fbfcff;
  border: 1px solid var(--border-light);
  border-radius: 14px;
  padding: 14px;
}

.summary-tile span,
.result-stat span {
  display: block;
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 8px;
}

.summary-tile strong {
  font-size: 22px;
}

.report-section {
  margin-top: 20px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.data-chip,
.risk-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.data-chip {
  background: #eef2ff;
  color: #4f46e5;
}

.risk-badge.risk-wait {
  background: #fff7ed;
  color: #ea580c;
}

.risk-badge.risk-review {
  background: #fef3c7;
  color: #b45309;
}

.risk-badge.risk-safe {
  background: #ecfdf3;
  color: #15803d;
}

/* 风险总览区块 */
.risk-overview-section {
  margin-bottom: 28px;
}

.risk-overview-card {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 22px;
}

.risk-level-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.risk-level-badge.risk-high {
  background: #fef2f2;
  color: #dc2626;
}

.risk-level-badge.risk-medium {
  background: #fff7ed;
  color: #b45309;
}

.risk-level-badge.risk-low {
  background: #ecfdf3;
  color: #15803d;
}

.risk-level-badge.risk-pending {
  background: #f3f4f6;
  color: #6b7280;
}

.risk-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  padding: 20px 0;
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 20px;
}

.risk-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.risk-stat-num {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-main);
}

.risk-stat-num.risk-num-high {
  color: #dc2626;
}

.risk-stat-num.risk-num-medium {
  color: #b45309;
}

.risk-stat-num.risk-num-low {
  color: #15803d;
}

.risk-stat-label {
  font-size: 13px;
  color: var(--text-muted);
}

.flat-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-main);
  line-height: 1.8;
}

.capability-note {
  margin-top: 20px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid var(--border-light);
}

.capability-note p {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.6;
}

.risk-item-list {
  display: grid;
  gap: 12px;
}

.risk-item-card {
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 16px;
  background: #fbfcff;
}

.risk-item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.risk-item-card p {
  margin: 0;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.doc-card {
  border: 1px solid var(--border-light);
  border-radius: 18px;
  padding: 20px;
  background: #fbfcff;
}

.doc-main,
.doc-actions,
.result-stat {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doc-main h3 {
  margin: 0 0 6px;
  font-size: 18px;
}

.doc-main p {
  margin: 0;
  color: var(--text-sub);
}

.doc-actions {
  margin-top: 18px;
}

.primary-btn,
.secondary-btn {
  border: 0;
  border-radius: 12px;
  padding: 12px 18px;
  font-weight: 700;
  cursor: pointer;
}

.auto-run-tip {
  color: var(--primary-color);
  font-weight: 700;
}

.primary-btn {
  background: var(--primary-color);
  color: white;
}

.primary-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.parsing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  color: #ea580c;
  font-weight: 700;
}

.parsing-dot {
  width: 8px;
  height: 8px;
  background: #ea580c;
  border-radius: 50%;
  animation: pulse 1s infinite;
}

.secondary-btn {
  background: white;
  border: 1px solid var(--border-light);
}

.status-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

/* 文档状态 - 统一状态体系 */
.status-pending {
  background: #f3f4f6;
  color: #6b7280;
}

.status-parsing {
  background: #fff7ed;
  color: #b45309;
}

.status-success {
  background: #ecfdf3;
  color: #15803d;
}

.status-failed {
  background: #fef2f2;
  color: #dc2626;
}

.parse-result {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.result-summary p {
  margin: 8px 0 0;
  color: var(--text-sub);
  line-height: 1.7;
}

.empty-panel {
  padding: 28px;
}

.evidence-section,
.log-section {
  margin-bottom: 28px;
}

@media (max-width: 1100px) {
  .overview-grid,
  .summary-metrics,
  .parse-result,
  .report-grid,
  .risk-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .detail-page {
    padding: 24px;
  }

  .detail-header,
  .doc-main,
  .doc-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .overview-grid,
  .summary-metrics,
  .parse-result,
  .report-grid,
  .risk-summary {
    grid-template-columns: 1fr;
  }
}
</style>
