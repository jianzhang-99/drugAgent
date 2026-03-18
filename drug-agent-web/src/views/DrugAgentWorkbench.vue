<template>
  <workspace-layout>
    <section class="workbench-canvas">
      <div class="hero-section">
        <div class="hero-badge">
          <div class="badge-inner">✦</div>
        </div>
        <h1>有什么我可以帮您分析的？</h1>
        <p>描述您的监管需求，或直接上传附件交由 AI 引擎处理</p>
      </div>

      <div class="input-container">
        <div v-if="selectedFiles.length > 0" class="file-preview-list">
          <div v-for="(file, index) in selectedFiles" :key="index" class="file-item">
            <el-icon><Document /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <el-icon class="remove-file" @click="removeFile(index)"><Close /></el-icon>
          </div>
        </div>
        <textarea
          v-model="promptText"
          class="main-prompt"
          placeholder="例如：帮我审查本周上传的多个标书文件是否有围标嫌疑，也支持上传 doc、docx、md 文件..."
        />
        <div class="input-actions">
          <div class="action-group">
            <input
              ref="fileInput"
              type="file"
              multiple
              style="display: none"
              @change="handleFileChange"
            />
            <button class="icon-tool" title="上传附件" @click="triggerFileInput">
              <el-icon><Upload /></el-icon>
            </button>
            <button class="icon-tool" title="知识库"><el-icon><Notebook /></el-icon></button>
          </div>
          <button class="submit-trigger" :disabled="loading" @click="handleSubmit">
            <el-icon v-if="!loading"><Position /></el-icon>
            <el-icon v-else class="is-loading"><Loading /></el-icon>
            <span>{{ loading ? '正在处理...' : '执行指令' }}</span>
          </button>
        </div>
      </div>

      <div v-if="messages.length > 0" class="text-response-panel">
        <div class="response-header">
          <h2>对话记录</h2>
          <span v-if="lastScene" class="scene-badge">{{ lastScene }}</span>
        </div>
        <div class="message-list">
          <message-bubble
            v-for="message in messages"
            :key="message.id"
            :msg="message"
          />
        </div>
      </div>

      <agent-result-panel v-if="latestResult" :result="latestResult" />

      <div class="quick-starts">
        <header class="section-header">
          <h2>常用监管工作流</h2>
        </header>
        <div class="cards-layout">
          <suggest-card
            v-for="card in workflowCards"
            :key="card.title"
            v-bind="card"
            @click="card.action?.()"
          />
        </div>
      </div>
      
      <footer class="legal-footer">
        Agent 会自动将您的指令解析为 AgentContext，任务执行详情可在看板中追踪。
      </footer>
    </section>
  </workspace-layout>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Files, Notebook, Position, Upload, Warning, Close, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import SuggestCard from '../components/common/SuggestCard.vue'
import MessageBubble from '../components/MessageBubble.vue'
import AgentResultPanel from '../components/AgentResultPanel.vue'
import { streamDrugAgentChat, submitDrugAgentTask } from '../api/drug-agent'
import { appendAuditLog, getUserPreferences, setRecentTenderTask, upsertTenderTask } from '../utils/local-state'

const router = useRouter()
const promptText = ref('')
const fileInput = ref(null)
const selectedFiles = ref([])
const loading = ref(false)
const lastScene = ref('')
const messages = ref([])
const latestResult = ref(null)
const MAX_FILE_SIZE = 20 * 1024 * 1024
const MIN_TENDER_FILES = 2
const preferences = getUserPreferences()
const ALLOWED_FILE_EXTENSIONS = ['.doc', '.docx', '.md']

const isSupportedTenderFile = (file) => {
  const lowerName = file.name.toLowerCase()
  return ALLOWED_FILE_EXTENSIONS.some((extension) => lowerName.endsWith(extension))
}

const getFileKey = (file) => `${file.name}-${file.size}-${file.lastModified}`

const triggerFileInput = () => {
  fileInput.value.click()
}

const handleFileChange = (event) => {
  const files = Array.from(event.target.files)
  const existingKeys = new Set(selectedFiles.value.map(getFileKey))
  const acceptedFiles = []

  files.forEach((file) => {
    if (!isSupportedTenderFile(file)) {
      ElMessage.warning(`仅支持上传 .doc、.docx、.md 文件：${file.name}`)
      return
    }

    if (file.size > MAX_FILE_SIZE) {
      ElMessage.warning(`文件大小不能超过 20MB：${file.name}`)
      return
    }

    const fileKey = getFileKey(file)
    if (existingKeys.has(fileKey)) {
      ElMessage.warning(`已选择相同文件，无需重复上传：${file.name}`)
      return
    }

    existingKeys.add(fileKey)
    acceptedFiles.push(file)
  })

  selectedFiles.value = [...selectedFiles.value, ...acceptedFiles]
  // 重置 input，允许选择相同文件
  event.target.value = ''
}

const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

const createMessageId = (role) => `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const formatAgentResult = (result) => {
  const sections = []

  if (result?.summary) {
    sections.push(`## 结果摘要\n${result.summary}`)
  }

  if (result?.answer) {
    sections.push(`## Agent 输出\n${result.answer}`)
  }

  if (result?.report?.managementSummary?.length) {
    sections.push(`## 管理摘要\n${result.report.managementSummary.map((item) => `- ${item}`).join('\n')}`)
  }

  if (result?.report?.recommendedActions?.length) {
    sections.push(`## 建议动作\n${result.report.recommendedActions.map((item) => `- ${item}`).join('\n')}`)
  }

  if (result?.steps?.length) {
    sections.push(`## 执行步骤\n${result.steps.map((item) => `- ${item}`).join('\n')}`)
  }

  if (result?.riskLevel) {
    sections.push(`## 风险等级\n${result.riskLevel}`)
  }

  return sections.filter(Boolean).join('\n\n')
}

const handleSubmit = async () => {
  if (!promptText.value.trim() && selectedFiles.value.length === 0) {
    ElMessage.warning('请输入指令或上传文件')
    return
  }

  loading.value = true
  try {
    if (selectedFiles.value.length > 0) {
      if (selectedFiles.value.length < MIN_TENDER_FILES) {
        ElMessage.warning(`标书审查至少需要上传 ${MIN_TENDER_FILES} 份 doc、docx 或 md 文件`)
        return
      }

      const userMessage = promptText.value.trim() || '请结合我上传的文件判断场景并输出对应结果'
      const userMessageId = createMessageId('user')
      const assistantMessageId = createMessageId('assistant')

      messages.value.push({
        id: userMessageId,
        role: 'user',
        content: `${userMessage}\n\n已上传文件：${selectedFiles.value.map((file) => file.name).join('、')}`
      })
      messages.value.push({
        id: assistantMessageId,
        role: 'assistant',
        content: '正在识别场景并执行对应工作流...'
      })

      const response = await submitDrugAgentTask({
        files: selectedFiles.value,
        query: promptText.value.trim(),
        submittedBy: preferences.submittedBy || 'anonymous'
      })

      latestResult.value = response
      lastScene.value = response?.scene || ''
      const assistantMessage = messages.value.find((message) => message.id === assistantMessageId)
      if (assistantMessage) {
        assistantMessage.content = formatAgentResult(response)
      }

      const taskPayload = {
        caseId: response?.caseId || '',
        status: response?.riskLevel || 'COMPLETED',
        documentIds: response?.documentIds || [],
        filenames: selectedFiles.value.map((file) => file.name),
        createdAt: new Date().toISOString(),
        scene: 'tender',
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
        detail: `${response?.scene || 'UNKNOWN'} 场景已返回结果`,
        createdAt: new Date().toISOString()
      })
      ElMessage.success('Agent 已返回场景结果')
      // 清空文件和输入
      selectedFiles.value = []
      promptText.value = ''

      if (response?.caseId) {
        setTimeout(() => {
          router.push({
            path: `/agent/tasks/${response.caseId}`,
            query: {
              caseId: response.caseId
            }
          })
        }, 1200)
      }
    } else {
      const userMessage = promptText.value.trim()
      const userMessageId = createMessageId('user')
      const assistantMessageId = createMessageId('assistant')

      messages.value.push({
        id: userMessageId,
        role: 'user',
        content: userMessage
      })
      messages.value.push({
        id: assistantMessageId,
        role: 'assistant',
        content: '正在分析中...'
      })

      promptText.value = ''

      let streamedContent = ''
      let streamFailed = false
      let hasReceivedDelta = false

      await streamDrugAgentChat({ query: userMessage }, {
        onMeta(payload) {
          lastScene.value = payload?.scene || ''
          latestResult.value = null
        },
        onDelta(payload) {
          hasReceivedDelta = true
          streamedContent += typeof payload === 'string' ? payload : ''
          const assistantMessage = messages.value.find((message) => message.id === assistantMessageId)
          if (assistantMessage) {
            assistantMessage.content = streamedContent || '正在分析中...'
          }
        },
        onDone() {
          const assistantMessage = messages.value.find((message) => message.id === assistantMessageId)
          if (assistantMessage) {
            assistantMessage.content = streamedContent || '已完成分析，但暂无可展示内容'
          }
        },
        onError(payload) {
          streamFailed = true
          const assistantMessage = messages.value.find((message) => message.id === assistantMessageId)
          if (assistantMessage) {
            assistantMessage.content = `分析失败：${payload || '流式响应中断，请稍后重试'}`
          }
        }
      })

      if (!streamFailed && hasReceivedDelta) {
        appendAuditLog({
          id: `audit-${Date.now()}`,
          type: 'CHAT_COMPLETED',
          title: '完成一次工作台问答',
          detail: userMessage,
          createdAt: new Date().toISOString()
        })
        ElMessage.success('分析完成')
      } else if (!streamFailed) {
        ElMessage.warning('本次未返回有效内容')
      }
    }
  } catch (error) {
    console.error('Submit error:', error)
    const lastAssistantMessage = [...messages.value].reverse().find((message) => message.role === 'assistant')
    if (lastAssistantMessage && lastAssistantMessage.content === '正在分析中...') {
      lastAssistantMessage.content = '分析失败：请求中断或服务暂不可用，请稍后重试'
    }
    // 错误处理已在 request.js 中通过 ElMessage 处理
  } finally {
    loading.value = false
  }
}

const workflowCards = [
  {
    title: '标投标审查',
    description: '对比多份标单文件，深度识别语义碰撞、串标及异常围标风险。',
    icon: Document,
    theme: 'theme-indigo',
    action: () => {
      promptText.value = '请帮我发起一次标书审查任务，并重点关注相似度、联系方式、报价异常和核心团队重合。'
    }
  },
  {
    title: '合同合规性',
    description: '基于最新行业规范，自动提取合同中的潜在法律风险与待议条款。',
    icon: Files,
    theme: 'theme-green',
    action: () => {
      promptText.value = '请帮我从合规、违约责任、付款条款和排他约定四个维度预审合同风险。'
    }
  },
  {
    title: '价格预警',
    description: '分析特定耗材的历史采购趋势，生成异常价格波动及合规性预警。',
    icon: Warning,
    theme: 'theme-amber',
    action: () => {
      promptText.value = '请分析近三个月耗材采购数据，输出价格异常波动预警和建议复核项。'
    }
  }
]
</script>

<style scoped>
.workbench-canvas {
  max-width: 1100px;
  margin: 0 auto;
  padding: 80px 40px;
}

.hero-section {
  text-align: center;
  margin-bottom: 60px;
}

.hero-badge {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.badge-inner {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-deep));
  border-radius: 20px;
  display: grid;
  place-items: center;
  color: white;
  font-size: 32px;
  box-shadow: var(--shadow-lg);
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

h1 {
  font-size: 38px;
  font-weight: 850;
  letter-spacing: -0.5px;
  margin-bottom: 12px;
}

.hero-section p {
  font-size: 16px;
  color: var(--text-sub);
  font-weight: 500;
}

.input-container {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md);
  margin-bottom: 60px;
  transition: var(--transition-smooth);
}

.input-container:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 4px var(--primary-bg);
}

.text-response-panel {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 48px;
}

.response-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.response-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}

.scene-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--primary-bg);
  color: var(--primary-color);
  font-size: 13px;
  font-weight: 700;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px dashed var(--border-light);
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--primary-bg);
  color: var(--primary-color);
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
}

.file-name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-file {
  cursor: pointer;
  font-size: 14px;
  opacity: 0.7;
  transition: var(--transition-smooth);
}

.remove-file:hover {
  opacity: 1;
  color: var(--danger-color);
}

.main-prompt {
  width: 100%;
  min-height: 140px;
  border: none;
  resize: none;
  outline: none;
  font-size: 18px;
  color: var(--text-main);
  font-family: inherit;
  margin-bottom: 16px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-group {
  display: flex;
  gap: 12px;
}

.icon-tool {
  width: 44px;
  height: 44px;
  border: 0;
  background: var(--primary-bg);
  color: var(--primary-color);
  border-radius: 12px;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.icon-tool:hover {
  background: var(--primary-color);
  color: white;
}

.submit-trigger {
  background: var(--primary-color);
  color: white;
  border: 0;
  padding: 0 28px;
  height: 48px;
  border-radius: 99px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.submit-trigger:hover {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(var(--brand-hull), 85%, 55%, 0.3);
}

.section-header h2 {
  font-size: 14px;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 1.5px;
  font-weight: 800;
  margin-bottom: 24px;
}

.cards-layout {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.legal-footer {
  margin-top: 60px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .cards-layout { grid-template-columns: 1fr; }
  .workbench-canvas { padding: 40px 20px; }
  h1 { font-size: 28px; }
  .input-actions { flex-direction: column; gap: 20px; }
  .submit-trigger { width: 100%; }
}
</style>
