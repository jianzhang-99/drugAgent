<template>
  <workspace-layout>
    <div class="workbench-header">
      <div class="header-left">
        <div class="breadcrumb">
          <span class="parent-node">横渡审查工作台</span>
          <template v-if="messages.length">
            <el-icon class="separator"><ArrowRight /></el-icon>
            <span class="current-node">{{ sessionTitle || '新审查任务' }}</span>
          </template>
        </div>
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
          <div class="hero-logo">
            <el-icon><MagicStick /></el-icon>
            <div class="hero-logo-glow"></div>
          </div>
          <h1 class="hero-title">有什么我可以帮您分析的？</h1>
          <p class="hero-subtitle">直接描述您的监管需求，系统将自动分发到对应的工作流</p>

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
          <div v-for="message in messages" :key="message.id" class="message-wrapper">
            <message-bubble :msg="message" />
            <agent-result-panel v-if="message.result" :result="message.result" />
          </div>
        </div>
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
            :placeholder="messages.length ? '向横渡追加要求或提供更多材料...' : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'"
            rows="1"
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
              <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Promotion /></el-icon>
              <span>发送任务</span>
            </button>
          </div>
        </div>
        
        <p class="disclaimer">AI 生成内容仅供参考，重大决策请人工复核 (横渡智能监管核心 v0.3)</p>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, nextTick, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Document,
  Upload,
  Close,
  Loading,
  MagicStick,
  TrendCharts,
  Reading,
  Checked,
  Warning,
  Promotion,
  ArrowRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import SuggestCard from '../components/common/SuggestCard.vue'
import MessageBubble from '../components/MessageBubble.vue'
import AgentResultPanel from '../components/AgentResultPanel.vue'
import { streamDrugAgentChat, submitDrugAgentTask } from '../api/drug-agent'
import { appendAuditLog, getUserPreferences, setRecentTenderTask, upsertTenderTask } from '../utils/local-state'

const route = useRoute()
const router = useRouter()
const preferences = getUserPreferences()
const sessionTitle = ref('年度设备采购标书比对')

const promptText = ref('')
const fileInput = ref(null)
const scrollContainer = ref(null)
const selectedFiles = ref([])
const loading = ref(false)
const messages = ref([])

const MAX_FILE_SIZE = 20 * 1024 * 1024
const getFileKey = (file) => `${file.name}-${file.size}-${file.lastModified}`
const createMessageId = (role) => `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

onMounted(() => {
  // 优先处理路由参数
  if (route.query.new) {
    handleReset()
  } else if (route.query.history) {
    // 加载历史会话
    loadHistoryById(route.query.history, route.query.title)
  } else {
    // 默认加载第一个历史会话
    loadMockHistory()
  }

  // 监听自定义事件
  window.addEventListener('reset-workbench', handleReset)
  window.addEventListener('load-history', (e) => {
    loadHistoryById(e.detail.historyId, e.detail.title)
  })
})

onUnmounted(() => {
  window.removeEventListener('reset-workbench', handleReset)
  window.removeEventListener('load-history', () => {})
})

const handleReset = () => {
  messages.value = []
  sessionTitle.value = ''
  selectedFiles.value = []
  promptText.value = ''
}

// 根据历史 ID 加载对应的会话内容
const loadHistoryById = (historyId, title) => {
  sessionTitle.value = title || '新审查任务'

  // 清除 URL 查询参数，避免刷新时重复加载
  if (route.query.history || route.query.new) {
    router.replace({ path: '/agent/workbench' })
  }

  // 根据不同历史 ID 加载不同的模拟数据
  if (historyId === 'h1') {
    // 今天的历史会话
    messages.value = [
      {
        id: 'm1',
        role: 'user',
        content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
        time: '14:20',
        files: [
          { name: '样例A_XX医院标书.docx' },
          { name: '样例B_XX药房投标.pdf' }
        ]
      },
      {
        id: 'm2',
        role: 'assistant',
        content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。',
        time: '14:22',
        result: {
          caseId: '99281',
          scene: 'TENDER_REVIEW',
          riskLevel: 'HIGH',
          summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。',
          score: '87',
          documentIds: [1, 2]
        }
      }
    ]
  } else if (historyId === 'h2') {
    // 昨天的历史会话
    messages.value = [
      {
        id: 'm1',
        role: 'user',
        content: '审查这份骨科耗材供应商协议，找出其中的风险条款。',
        time: '10:15',
        files: [
          { name: '骨科耗材供应商协议.docx' }
        ]
      },
      {
        id: 'm2',
        role: 'assistant',
        content: '已为您完成合同预审，发现以下风险条款需要关注：\n1. 付款条款模糊\n2. 违约责任不对称\n3. 知识产权归属不清',
        time: '10:18',
        result: {
          caseId: '99282',
          scene: 'CONTRACT_PRECHECK',
          riskLevel: 'MEDIUM',
          summary: '发现 3 处需要关注的风险条款，建议在签约前与供应商协商修改。',
          score: '65',
          documentIds: [1]
        }
      }
    ]
  } else {
    // 默认加载第一个历史
    loadMockHistory()
  }
}

const loadMockHistory = () => {
  loadHistoryById('h1', '年度设备采购标书比对')
}

watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
    }
  },
  { deep: true }
)

const handleFileChange = (event) => {
  const files = Array.from(event.target.files || [])
  const existingKeys = new Set(selectedFiles.value.map(getFileKey))
  const acceptedFiles = []

  files.forEach((file) => {
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

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleCardClick = (card) => {
  promptText.value = card.prompt || ''
}

const handleSubmit = async () => {
  if (!promptText.value.trim() && selectedFiles.value.length === 0) {
    ElMessage.warning('请输入问题或上传文件')
    return
  }
  loading.value = true
  const userContent = promptText.value.trim() || '帮我对比标书'
  const userFiles = selectedFiles.value.map(f => ({ name: f.name }))
  
  messages.value.push({
    id: createMessageId('user'),
    role: 'user',
    content: userContent,
    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    files: userFiles
  })

  promptText.value = ''
  selectedFiles.value = []

  try {
    const assistantId = createMessageId('assistant')
    messages.value.push({
      id: assistantId,
      role: 'assistant',
      content: '分析中...',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    })

    const response = await submitDrugAgentTask({ query: userContent, files: userFiles })
    const assistantMsg = messages.value.find(m => m.id === assistantId)
    if (assistantMsg) {
      assistantMsg.content = response.answer || '分析完成。'
      assistantMsg.result = response
    }
  } catch (error) {
    ElMessage.error('分析失败')
  } finally {
    loading.value = false
  }
}

const workflowCards = [
  {
    title: '标书审查',
    description: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    icon: Document,
    theme: 'theme-indigo',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。'
  },
  {
    title: '合同预审',
    description: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    icon: Checked,
    theme: 'theme-green',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。'
  },
  {
    title: '合规预警',
    description: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    icon: Warning,
    theme: 'theme-amber',
    prompt: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。'
  }
]
</script>

<style scoped>
.workbench-header {
  height: 64px;
  padding: 0 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f2f5;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  position: sticky;
  top: 0;
  z-index: 50;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
}

.parent-node {
  font-size: 14px;
  color: #a0aec0;
}

.current-node {
  font-size: 14px;
  font-weight: 600;
  color: #1a202c;
}

.separator {
  font-size: 12px;
  color: #cbd5e1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.active-tasks-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #fdfdff;
  border: 1px solid #edf2f7;
  border-radius: 999px;
  font-size: 13px;
  color: #4a5568;
  font-weight: 600;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

.user-avatar {
  background: #4f46e5;
  color: #fff;
  font-weight: 700;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 4px 8px rgba(79, 70, 229, 0.2);
}

.chat-page {
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  position: relative;
  max-width: 1400px;
  margin: 0 auto;
}

.chat-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 40px 20px 240px;
}

.empty-state {
  max-width: 800px;
  margin: 60px auto 0;
  text-align: center;
}

.hero-logo {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #6366f1, #3b82f6);
  color: #fff;
  border-radius: 20px;
  display: grid;
  place-items: center;
  font-size: 36px;
  margin: 0 auto 32px;
  position: relative;
  box-shadow: 0 16px 32px rgba(99, 102, 241, 0.3);
}

.hero-logo-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 120%;
  height: 120%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.4) 0%, transparent 70%);
  filter: blur(10px);
  z-index: -1;
}

.hero-title {
  font-size: 44px;
  font-weight: 850;
  color: #1a202c;
  margin-bottom: 16px;
  letter-spacing: -1.5px;
}

.hero-subtitle {
  font-size: 18px;
  color: #718096;
  margin-bottom: 72px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.message-stream {
  width: min(860px, 100%);
  margin: 0 auto;
}

.message-wrapper {
  margin-bottom: 40px;
}

.composer-dock {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  width: min(900px, calc(100vw - 40px));
  z-index: 100;
}

.composer-container {
  background: #fff;
  border: 1px solid #edf2f7;
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.06);
  padding: 18px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.composer-container:focus-within {
  border-color: #3b82f633;
  box-shadow: 0 15px 50px rgba(59, 130, 246, 0.1);
}

.main-prompt {
  width: 100%;
  min-height: 44px;
  max-height: 180px;
  border: 0;
  resize: none;
  outline: none;
  font-size: 16px;
  color: #1e293b;
  margin-bottom: 12px;
  font-family: inherit;
  line-height: 1.6;
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
  gap: 8px;
}

.action-btn {
  border: 1px solid #f1f5f9;
  background: #fff;
  border-radius: 10px;
  height: 38px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.action-btn:hover {
  background: #f8fafc;
  color: #1e293b;
  border-color: #e2e8f0;
}

.submit-btn {
  background: #cbd5e1;
  color: #fff;
  border: 0;
  border-radius: 12px;
  height: 40px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  transition: all 0.3s;
}

.submit-btn:not(:disabled) {
  background: #2563eb;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}

.submit-btn:hover:not(:disabled) {
  background: #1d4ed8;
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.25);
}

.disclaimer {
  text-align: center;
  font-size: 12px;
  color: #a0aec0;
  margin-top: 20px;
}

.file-preview-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f1f5f9;
  border-radius: 10px;
  font-size: 12px;
  color: #2d3748;
  border: 1px solid #e2e8f0;
}

@media (max-width: 900px) {
  .workflow-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
