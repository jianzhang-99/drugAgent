<template>
  <workspace-layout>
    <div class="workbench-header">
      <div class="header-left">
        <nav class="breadcrumb">
          <span class="parent-node">Agent 审查工作台</span>
          <el-icon class="separator"><ArrowRight /></el-icon>
          <span class="current-node">{{ sessionTitle || '新审查任务' }}</span>
        </nav>
      </div>
      <div class="header-right">
        <div class="active-tasks-badge">
          <el-icon><TrendCharts /></el-icon>
          <span>0 活跃任务</span>
        </div>
        <el-avatar :size="32" class="user-avatar">DA</el-avatar>
      </div>
    </div>

    <section class="chat-page">
      <div ref="scrollContainer" class="chat-scroll">
        <div v-if="!messages.length" class="empty-state">
          <div class="hero-icon">
            <el-icon><MagicStick /></el-icon>
          </div>
          <h1 class="hero-title">有什么我可以帮您分析的?</h1>
          <p class="hero-subtitle">直接描述您的监管需求，Agent 将自动分发到对应的工作流</p>

          <div class="workflow-grid">
            <suggest-card
              v-for="card in workflowCards"
              :key="card.title"
              v-bind="card"
              @click="handleCardClick(card)"
            />
          </div>
        </div>

        <div v-else class="message-stream">
          <message-bubble
            v-for="message in messages"
            :key="message.id"
            :msg="message"
          />
        </div>

        <agent-result-panel v-if="latestResult" :result="latestResult" />
      </div>

      <div class="composer-dock">
        <div class="composer-container">
          <div v-if="selectedFiles.length" class="file-preview-list">
            <div v-for="(file, index) in selectedFiles" :key="getFileKey(file)" class="file-item">
              <el-icon><Document /></el-icon>
              <span class="file-name">{{ file.name }}</span>
              <button class="remove-file" @click="removeFile(index)">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <textarea
            v-model="promptText"
            class="main-prompt"
            placeholder="描述您的监管需求，例如：检测这两份标书文件是否雷同..."
            @keydown.enter.exact.prevent="handleSubmit"
          />

          <div class="composer-footer">
            <div class="footer-actions">
              <input
                ref="fileInput"
                type="file"
                multiple
                style="display: none"
                @change="handleFileChange"
              />
              <button class="action-btn" @click="triggerFileInput">
                <el-icon><Upload /></el-icon>
                <span>上传材料</span>
              </button>
              <button class="action-btn">
                <el-icon><Reading /></el-icon>
                <span>引用知识</span>
              </button>
            </div>

            <button class="submit-btn" :disabled="loading" @click="handleSubmit">
              <span v-if="!loading">发送任务</span>
              <el-icon v-else class="is-loading"><Loading /></el-icon>
              <el-icon v-if="!loading"><Right /></el-icon>
            </button>
          </div>
        </div>
        
        <p class="disclaimer">AI 生成内容仅供参考，重大决策请人工复核 (Drug-Agent Core v0.3)</p>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  Document,
  Upload,
  Close,
  Loading,
  MagicStick,
  TrendCharts,
  Right,
  Reading,
  Checked,
  Warning,
  ArrowRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import SuggestCard from '../components/common/SuggestCard.vue'
import MessageBubble from '../components/MessageBubble.vue'
import AgentResultPanel from '../components/AgentResultPanel.vue'
import { streamDrugAgentChat, submitDrugAgentTask } from '../api/drug-agent'
import { appendAuditLog, getUserPreferences, setRecentTenderTask, upsertTenderTask } from '../utils/local-state'

const preferences = getUserPreferences()
const sessionTitle = ref('年度设备采购标书比对')

const promptText = ref('')
const fileInput = ref(null)
const scrollContainer = ref(null)
const selectedFiles = ref([])
const loading = ref(false)
const lastScene = ref('')
const messages = ref([])
const latestResult = ref(null)

const MAX_FILE_SIZE = 20 * 1024 * 1024
const MIN_TENDER_FILES = 2
const ALLOWED_FILE_EXTENSIONS = ['.doc', '.docx', '.md']

const getFileKey = (file) => `${file.name}-${file.size}-${file.lastModified}`
const createMessageId = (role) => `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

watch(
  () => [messages.value.length, latestResult.value],
  async () => {
    await nextTick()
    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
    }
  },
  { deep: true }
)

const isSupportedTenderFile = (file) => {
  const lowerName = file.name.toLowerCase()
  return ALLOWED_FILE_EXTENSIONS.some((extension) => lowerName.endsWith(extension))
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileChange = (event) => {
  const files = Array.from(event.target.files || [])
  const existingKeys = new Set(selectedFiles.value.map(getFileKey))
  const acceptedFiles = []

  files.forEach((file) => {
    if (!isSupportedTenderFile(file)) {
      ElMessage.warning(`仅支持 .doc、.docx、.md 文件：${file.name}`)
      return
    }

    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`文件不能超过 20MB：${file.name}`)
      return
    }

    const fileKey = getFileKey(file)
    if (existingKeys.has(fileKey)) {
      ElMessage.warning(`文件已添加，无需重复上传：${file.name}`)
      return
    }

    existingKeys.add(fileKey)
    acceptedFiles.push(file)
  })

  selectedFiles.value = [...selectedFiles.value, ...acceptedFiles]
  event.target.value = ''
}

const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

const handleCardClick = (card) => {
  promptText.value = card.prompt || ''
}

const formatAgentResult = (result) => {
  const sections = []

  if (result?.summary) sections.push(`## 结果摘要\n${result.summary}`)
  if (result?.answer) sections.push(`## Agent 输出\n${result.answer}`)
  if (result?.report?.managementSummary?.length) {
    sections.push(`## 管理摘要\n${result.report.managementSummary.map((item) => `- ${item}`).join('\n')}`)
  }
  if (result?.report?.recommendedActions?.length) {
    sections.push(`## 建议动作\n${result.report.recommendedActions.map((item) => `- ${item}`).join('\n')}`)
  }
  if (result?.steps?.length) {
    sections.push(`## 执行步骤\n${result.steps.map((item) => `- ${item}`).join('\n')}`)
  }
  if (result?.riskLevel) sections.push(`## 风险等级\n${result.riskLevel}`)

  return sections.join('\n\n') || '已完成分析，但暂无可展示内容。'
}

const appendUserAndAssistantMessages = (userContent, assistantContent, files = []) => {
  const userMessageId = createMessageId('user')
  const assistantMessageId = createMessageId('assistant')

  messages.value.push({ id: userMessageId, role: 'user', content: userContent, files })
  messages.value.push({ id: assistantMessageId, role: 'assistant', content: assistantContent, time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) })

  return assistantMessageId
}

const updateAssistantMessage = (messageId, content) => {
  const assistantMessage = messages.value.find((message) => message.id === messageId)
  if (assistantMessage) {
    assistantMessage.content = content
  }
}

const handleSubmit = async () => {
  if (!promptText.value.trim() && selectedFiles.value.length === 0) {
    ElMessage.warning('请输入问题或上传文件')
    return
  }

  loading.value = true

  try {
    if (selectedFiles.value.length > 0) {
      await handleFileTaskSubmit()
      return
    }

    await handleChatSubmit()
  } catch (error) {
    console.error('Submit error:', error)
    ElMessage.error('请求失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleFileTaskSubmit = async () => {
  if (selectedFiles.value.length < MIN_TENDER_FILES) {
    ElMessage.warning(`标书审查至少需要上传 ${MIN_TENDER_FILES} 份文件`)
    return
  }

  const userMessage = promptText.value.trim() || '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。'
  const assistantMessageId = appendUserAndAssistantMessages(
    userMessage,
    '正在识别场景并生成分析结果...',
    selectedFiles.value.map(f => ({ name: f.name }))
  )

  const response = await submitDrugAgentTask({
    files: selectedFiles.value,
    query: promptText.value.trim(),
    submittedBy: preferences.submittedBy || 'anonymous'
  })

  latestResult.value = response
  lastScene.value = response?.scene || ''
  updateAssistantMessage(assistantMessageId, formatAgentResult(response))

  const taskPayload = {
    caseId: response?.caseId || '',
    status: response?.riskLevel || 'COMPLETED',
    documentIds: response?.documentIds || [],
    filenames: selectedFiles.value.map((file) => file.name),
    createdAt: new Date().toISOString(),
    scene: response?.scene || 'tender',
    submittedBy: preferences.submittedBy || 'anonymous',
    summary: response?.summary || '',
    report: response?.report || null
  }

  if (taskPayload.caseId) {
    setRecentTenderTask(taskPayload)
    upsertTenderTask(taskPayload)
  }

  appendAuditLog({
    id: `audit-${Date.now()}`,
    type: 'AGENT_TASK_COMPLETED',
    title: '完成一次场景化任务处理',
    detail: `${response?.scene || 'UNKNOWN'} 场景已返回分析结果`,
    createdAt: new Date().toISOString()
  })

  ElMessage.success('分析完成，可继续在当前对话中追问')
  selectedFiles.value = []
  promptText.value = ''
}

const handleChatSubmit = async () => {
  const userMessage = promptText.value.trim()
  const assistantMessageId = appendUserAndAssistantMessages(userMessage, '正在分析中...')

  promptText.value = ''
  latestResult.value = null

  let streamedContent = ''
  let hasReceivedDelta = false
  let streamFailed = false

  await streamDrugAgentChat(
    { query: userMessage },
    {
      onMeta(payload) {
        lastScene.value = payload?.scene || ''
      },
      onDelta(payload) {
        hasReceivedDelta = true
        streamedContent += typeof payload === 'string' ? payload : ''
        updateAssistantMessage(assistantMessageId, streamedContent || '正在分析中...')
      },
      onDone() {
        updateAssistantMessage(assistantMessageId, streamedContent || '已完成分析，但暂无可展示内容。')
      },
      onError(payload) {
        streamFailed = true
        updateAssistantMessage(assistantMessageId, `分析失败：${payload || '流式响应中断，请稍后重试'}`)
      }
    }
  )

  if (!streamFailed && hasReceivedDelta) {
    appendAuditLog({
      id: `audit-${Date.now()}`,
      type: 'CHAT_COMPLETED',
      title: '完成一次对话分析',
      detail: userMessage,
      createdAt: new Date().toISOString()
    })
    ElMessage.success('分析完成')
  } else if (!streamFailed) {
    ElMessage.warning('本次未返回有效内容')
  }
}

const workflowCards = [
  {
    title: '标书审查',
    description: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    icon: Document,
    theme: 'theme-indigo',
    prompt: '请帮我发起一次标书审查，并重点关注详细比对结果。'
  },
  {
    title: '合同预审',
    description: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    icon: Checked,
    theme: 'theme-green',
    prompt: '请根据合规知识库预审这份合同，提取其中的潜在风险点。'
  },
  {
    title: '合规预警',
    description: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    icon: Warning,
    theme: 'theme-amber',
    prompt: '请分析近三个月耗材采购数据并生成异常预警报告。'
  }
]
</script>

<style scoped>
.workbench-header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.breadcrumb {
  display: flex;
  align-items: center;
}

.parent-node {
  font-size: 14px;
  color: #94a3b8;
}

.separator {
  font-size: 12px;
  color: #cbd5e1;
  margin: 0 10px;
}

.current-node {
  font-size: 14px;
  color: #1e293b;
  font-weight: 700;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.active-tasks-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f1f5f9;
  border-radius: 999px;
  font-size: 13px;
  color: #475569;
  font-weight: 600;
}

.user-avatar {
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  font-size: 12px;
}

.chat-page {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 80px 0 240px;
}

.empty-state {
  max-width: 900px;
  margin: 0 auto;
  text-align: center;
}

.hero-icon {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: #fff;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 32px;
  margin: 0 auto 32px;
  box-shadow: 0 12px 24px rgba(79, 70, 229, 0.25);
}

.hero-title {
  font-size: 48px;
  font-weight: 850;
  color: #1e293b;
  margin-bottom: 12px;
  letter-spacing: -1px;
}

.hero-subtitle {
  font-size: 18px;
  color: #64748b;
  margin-bottom: 64px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  padding: 0 20px;
}

.message-stream,
:deep(.result-panel) {
  width: min(860px, calc(100vw - 64px));
  margin: 0 auto;
}

.composer-dock {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: min(900px, calc(100vw - 32px));
  padding-bottom: 24px;
  z-index: 10;
}

.composer-container {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.08);
  padding: 16px;
}

.file-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f1f5f9;
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
}

.file-name {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file {
  border: 0;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  padding: 2px;
  display: flex;
}

.main-prompt {
  width: 100%;
  min-height: 48px;
  max-height: 200px;
  border: 0;
  resize: none;
  outline: none;
  font-size: 16px;
  color: #1e293b;
  margin-bottom: 16px;
}

.main-prompt::placeholder {
  color: #94a3b8;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 8px;
  height: 36px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.submit-btn {
  background: #cbd5e1;
  color: #fff;
  border: 0;
  border-radius: 10px;
  height: 40px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.submit-btn:not(:disabled) {
  background: #2563eb;
  cursor: pointer;
}

.submit-btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.disclaimer {
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .workflow-grid {
    grid-template-columns: 1fr;
  }
  .hero-title {
    font-size: 36px;
  }
}
</style>
