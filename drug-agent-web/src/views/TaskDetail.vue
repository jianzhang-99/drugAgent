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
          <span v-if="isAutoRunning" class="auto-run-tip">Agent 正在自动解析文档...</span>
          <button class="secondary-btn" @click="parseAllDocuments" :disabled="parsingDocIds.length > 0 || !taskDetail">
            批量解析文档
          </button>
        </div>
      </header>

      <section v-if="taskDetail" class="overview-grid">
        <article class="overview-card">
          <span class="overview-label">任务 ID</span>
          <strong>{{ taskDetail.caseId }}</strong>
        </article>
        <article class="overview-card">
          <span class="overview-label">当前状态</span>
          <strong>{{ taskStatusLabel }}</strong>
        </article>
        <article class="overview-card">
          <span class="overview-label">提交人</span>
          <strong>{{ taskDetail.submittedBy || 'anonymous' }}</strong>
        </article>
        <article class="overview-card">
          <span class="overview-label">文档数量</span>
          <strong>{{ documents.length }}</strong>
        </article>
      </section>

      <section v-if="documents.length > 0" class="report-grid">
        <article class="summary-panel">
          <div class="panel-head">
            <h2>解析总览</h2>
            <span class="panel-tip">解析结果会自动沉淀为任务摘要</span>
          </div>
          <div class="summary-metrics">
            <div class="summary-tile">
              <span>已解析文档</span>
              <strong>{{ parsedDocCount }}/{{ documents.length }}</strong>
            </div>
            <div class="summary-tile">
              <span>段落块总数</span>
              <strong>{{ aggregateStats.paragraphs }}</strong>
            </div>
            <div class="summary-tile">
              <span>表格块总数</span>
              <strong>{{ aggregateStats.tables }}</strong>
            </div>
            <div class="summary-tile">
              <span>字段总数</span>
              <strong>{{ aggregateStats.fields }}</strong>
            </div>
          </div>

          <div class="report-section">
            <h3>关键字段分布</h3>
            <div v-if="fieldTypeEntries.length > 0" class="chip-list">
              <span v-for="[key, value] in fieldTypeEntries" :key="key" class="data-chip">
                {{ key }} · {{ value }}
              </span>
            </div>
            <p v-else class="muted-text">完成文档解析后，这里会显示联系方式、报价、团队成员等字段分布。</p>
          </div>

          <div class="report-section">
            <h3>自动生成的审查结论</h3>
            <p class="report-summary">{{ generatedReport.summary }}</p>
          </div>

          <div class="report-section">
            <h3>建议动作</h3>
            <ul class="flat-list">
              <li v-for="action in generatedReport.actions" :key="action">{{ action }}</li>
            </ul>
          </div>
        </article>

        <article class="summary-panel">
          <div class="panel-head">
            <h2>风险提示</h2>
            <span class="risk-badge" :class="generatedReport.riskClass">{{ generatedReport.riskLabel }}</span>
          </div>
          <ul class="flat-list">
            <li v-for="risk in generatedReport.risks" :key="risk">{{ risk }}</li>
          </ul>
        </article>
      </section>

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
                {{ doc.status }}
              </span>
            </div>

            <div class="doc-actions">
              <button class="primary-btn" @click="parseDocumentAction(doc)" :disabled="parsingDocIds.includes(doc.documentId)">
                {{ parsingDocIds.includes(doc.documentId) ? '解析中...' : '解析文档' }}
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

      <section v-else class="empty-panel">
        <h2>暂无任务数据</h2>
        <p>当前任务未找到对应文档，请返回工作台重新创建任务。</p>
      </section>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import { listTenderReviewCases, parseTenderDocument } from '../api/drug-agent'
import {
  appendAuditLog,
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
    actions.push('对联系方式字段做跨文档复核')
  }

  if (fieldTypeMap.value['投标报价'] || fieldTypeMap.value['bid_price']) {
    risks.push('已提取报价字段，建议进一步关注报价梯度、分项价格差额和整体报价结构。')
    actions.push('复核报价字段和报价梯度')
  }

  if (fieldTypeMap.value['团队成员'] || fieldTypeMap.value['team_member']) {
    risks.push('已提取团队成员字段，建议重点核查核心团队成员是否跨文档重合。')
    actions.push('检查核心团队重合情况')
  }

  if (aggregateStats.value.fields === 0) {
    risks.push('文档已解析，但尚未提取出明显结构化字段，可能需要结合原文段落和表格人工复核。')
    actions.push('人工查看原文段落和表格内容')
  }

  if (!risks.length) {
    risks.push('当前未发现明显高风险提示，但仍建议结合原文段落和规则引擎做进一步核验。')
    actions.push('结合规则引擎做后续比对')
  }

  return {
    summary: `已完成 ${parsedDocCount.value} 份文档解析，累计提取 ${aggregateStats.value.fields} 个结构化字段，可为后续标书查重、团队重合分析和报价异常识别提供输入。`,
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

const isAutoRunning = computed(() => parsingDocIds.value.length > 0)

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

.flat-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-main);
  line-height: 1.8;
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

.status-pending {
  background: #eef2ff;
  color: #4f46e5;
}

.status-parsing {
  background: #fff7ed;
  color: #ea580c;
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

@media (max-width: 1100px) {
  .overview-grid,
  .summary-metrics,
  .parse-result,
  .report-grid {
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
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
