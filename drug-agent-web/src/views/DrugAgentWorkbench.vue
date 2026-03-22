<template>
  <div class="app-container">
    <!-- 左侧侧边栏 -->
    <aside class="sidebar" :class="{ 'sidebar-collapsed': !isSidebarOpen }">
      <div class="sidebar-header">
        <div class="logo-section" @click="handleNewChat">
          <div class="logo">
            <el-icon><Sparkles /></el-icon>
          </div>
          <span v-if="isSidebarOpen" class="logo-text">Drug-Agent</span>
        </div>
        <button
          v-if="isSidebarOpen"
          class="new-chat-btn"
          title="新建任务/会话"
        >
          <el-icon><SquarePen /></el-icon>
        </button>
      </div>

      <nav class="sidebar-nav">
        <div
          v-for="nav in navItems"
          :key="nav.id"
          class="nav-item"
          :class="{ active: activeView === nav.id }"
          @click="handleNavClick(nav.id)"
        >
          <el-icon><component :is="nav.icon" /></el-icon>
          <span v-if="isSidebarOpen" class="nav-label">{{ nav.label }}</span>
        </div>
      </nav>

      <div class="sidebar-history">
        <div v-if="isSidebarOpen" class="history-title">
          <span class="title-text">历史审查会话</span>
        </div>

        <div class="history-groups">
          <div v-for="group in historyGroups" :key="group.date">
            <div v-if="isSidebarOpen" class="group-date">{{ group.date }}</div>
            <div class="group-items">
              <div
                v-for="session in group.sessions"
                :key="session.id"
                class="history-item"
                :class="{ active: activeSession?.id === session.id }"
                @click="loadSession(session)"
              >
                <el-icon><MessageSquare /></el-icon>
                <span v-if="isSidebarOpen" class="session-title">{{ session.title }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="sidebar-footer">
        <div
          v-if="isSidebarOpen"
          class="settings-item"
          :class="{ active: activeView === 'SETTINGS' }"
          @click="handleNavClick('SETTINGS')"
        >
          <el-icon><Settings /></el-icon>
          <span>偏好与系统配置</span>
        </div>
        <button class="collapse-btn" @click="toggleSidebar">
          <el-icon><Menu v-if="isSidebarOpen" /><ChevronLeft v-else /></el-icon>
        </button>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 顶部 Header -->
      <header class="workbench-header">
        <div class="breadcrumb">
          <span class="parent-node">
            {{ getHeaderTitle() }}
          </span>
          <template v-if="activeSession && activeView === 'WORKSPACE'">
            <el-icon class="separator"><ChevronRight /></el-icon>
            <span class="current-node">{{ activeSession.title }}</span>
          </template>
        </div>

        <div class="header-right">
          <div class="task-badge" @click="toggleTaskPane">
            <el-icon v-if="runningTasks.length" class="is-running"><Activity /></el-icon>
            <el-icon v-else><TrendCharts /></el-icon>
            <span>{{ runningTasks.length }} 活跃任务</span>
          </div>

          <el-avatar :size="32" class="user-avatar">DA</el-avatar>
        </div>
      </header>

      <section class="chat-page" :class="{ 'with-drawer': selectedReport }">
      <div ref="scrollContainer" class="chat-scroll">
        <div v-if="!activeSession" class="empty-state">
          <div class="hero-logo">
            <el-icon><Sparkles /></el-icon>
            <div class="hero-logo-glow"></div>
          </div>
          <h1 class="hero-title">有什么我可以帮您分析的？</h1>
          <p class="hero-subtitle">直接描述您的监管需求，Agent 将自动分发到对应的工作流</p>

          <div class="workflow-grid">
            <div
              v-for="(action, idx) in quickActions"
              :key="idx"
              class="quick-action-card"
              @click="handleSubmit(action.prompt)"
            >
              <div class="action-icon" :class="[action.bg, action.color]">
                <el-icon><component :is="action.icon" /></el-icon>
              </div>
              <h4 class="action-label">{{ action.label }}</h4>
              <p class="action-desc">{{ action.prompt }}</p>
            </div>
          </div>
        </div>

        <div v-else class="message-stream">
          <div v-for="(message, idx) in messages" :key="idx" class="message-wrapper">
            <message-bubble :msg="message" />
            <agent-result-panel
              v-if="message.result"
              :result="message.result"
              @view-detail="handleViewDetail(message.result)"
            />
          </div>
        </div>
        <div ref="messagesEndRef"></div>
      </div>

      <div class="composer-dock">
        <div class="composer-container">
          <div v-if="selectedFiles.length" class="file-preview-list">
            <div v-for="(file, index) in selectedFiles" :key="getFileKey(file)" class="file-item">
              <el-icon><FileText /></el-icon>
              <span class="file-name">{{ file.name }}</span>
              <button class="remove-file" @click="removeFile(index)">
                <el-icon><Close /></el-icon>
              </button>
            </div>
          </div>

          <textarea
            v-model="promptText"
            class="main-prompt"
            :placeholder="activeSession ? '向 Agent 追加要求或提供更多材料...' : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'"
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
              <button class="action-btn" @click="showKnowledgeDialog = true">
                <el-icon><BookOpen /></el-icon>
                <span>引用知识</span>
              </button>
            </div>

            <button class="submit-btn" :disabled="loading" @click="handleSubmit">
              <el-icon v-if="loading" class="is-loading"><Loader2 /></el-icon>
              <el-icon v-else><Send /></el-icon>
              <span>发送任务</span>
            </button>
          </div>
        </div>

        <p class="disclaimer">AI 生成内容仅供参考，重大决策请人工复核 (Drug-Agent Core v0.3)</p>
      </div>
    </section>

    <!-- 任务中心抽屉 -->
    <transition name="drawer-transition">
      <div v-if="isTaskPaneOpen" class="task-drawer">
        <div class="task-drawer-header">
          <h3>后台任务中心</h3>
          <button class="close-btn" @click="isTaskPaneOpen = false">
            <el-icon><X /></el-icon>
          </button>
        </div>
        <div class="task-list">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="task-item"
            @click="isTaskPaneOpen = false"
          >
            <div class="task-info">
              <div class="task-name">{{ task.name }}</div>
              <div class="task-meta">
                <span class="task-id">{{ task.id }}</span>
                <span class="task-status">{{ task.status }}</span>
              </div>
            </div>
            <div class="task-status-icon">
              <el-icon v-if="task.status === 'completed'"><CheckCircle2 /></el-icon>
              <span v-else class="task-progress">{{ task.progress }}%</span>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 右侧详情报告抽屉 -->
    <transition name="drawer-transition">
      <aside v-if="selectedReport" class="detail-drawer">
        <div class="drawer-header">
          <div class="drawer-title">
            <button class="back-btn" @click="selectedReport = null">
              <el-icon><ChevronLeft /></el-icon>
            </button>
            <el-icon><FileText /></el-icon>
            <span>结构化分析报告</span>
          </div>
          <button class="download-btn" @click="downloadReport">
            <el-icon><Download /></el-icon>
          </button>
        </div>

        <div class="drawer-content">
          <!-- 风险定级和综合分值 -->
          <div class="info-card risk-card">
            <div class="info-item">
              <p class="info-label">风险定级</p>
              <div class="risk-level" :class="getRiskClass(selectedReport.riskLevel)">
                <component :is="getRiskIcon(selectedReport.riskLevel)" />
                {{ getRiskConfig(selectedReport.riskLevel).label }}
              </div>
            </div>
            <div class="info-item text-right">
              <p class="info-label">综合分值</p>
              <span class="score-value">{{ selectedReport.score || '87' }}</span>
            </div>
          </div>

          <!-- 结果摘要 -->
          <div class="info-card">
            <h4 class="card-title">
              <el-icon><Activity /></el-icon>
              结果摘要 (Agent Output)
            </h4>
            <p class="card-text">{{ selectedReport.summary || '暂无摘要' }}</p>
          </div>

          <!-- 管理摘要 -->
          <div class="info-card">
            <h4 class="card-title">
              <el-icon><Network /></el-icon>
              管理摘要
            </h4>
            <ul class="list-items">
              <li v-for="(point, index) in selectedReport.managementSummary" :key="index">
                • {{ point }}
              </li>
            </ul>
          </div>

          <!-- 建议动作 -->
          <div class="info-card action-card">
            <h4 class="card-title">
              <el-icon><CheckSquare /></el-icon>
              建议动作
            </h4>
            <ul class="list-items">
              <li v-for="(action, index) in selectedReport.suggestedActions" :key="index">
                → {{ action }}
              </li>
            </ul>
          </div>

          <!-- 执行步骤 -->
          <div class="info-card">
            <h4 class="card-title">
              <el-icon><Terminal /></el-icon>
              执行步骤 (Execution Trace)
            </h4>
            <div class="steps-list">
              <div
                v-for="(step, idx) in selectedReport.steps"
                :key="idx"
                class="step-item"
              >
                <div class="step-dot"></div>
                <span class="step-name">{{ step }}</span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </transition>

    <!-- 知识引用对话框 -->
    <el-dialog
      v-model="showKnowledgeDialog"
      title="引用知识库"
      width="600px"
      :close-on-click-modal="false"
      class="knowledge-dialog"
    >
      <div class="knowledge-dialog-content">
        <div class="search-box">
          <el-input
            v-model="knowledgeSearch"
            placeholder="搜索知识库内容..."
            clearable
            @input="filterKnowledge"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="knowledge-categories">
          <el-radio-group v-model="knowledgeCategory" @change="filterKnowledge">
            <el-radio-button label="all">全部</el-radio-button>
            <el-radio-button label="rule">规则引擎</el-radio-button>
            <el-radio-button label="rag">RAG向量库</el-radio-button>
            <el-radio-button label="policy">合规政策</el-radio-button>
          </el-radio-group>
        </div>

        <div class="knowledge-list">
          <div
            v-for="item in filteredKnowledge"
            :key="item.id"
            class="knowledge-item"
            :class="{ selected: selectedKnowledge.includes(item.id) }"
            @click="toggleKnowledge(item)"
          >
            <div class="knowledge-item-header">
              <el-checkbox
                :model-value="selectedKnowledge.includes(item.id)"
                @click.stop
                @change="toggleKnowledge(item)"
              />
              <span class="knowledge-title">{{ item.title }}</span>
              <el-tag :type="getKnowledgeTagType(item.category)" size="small">
                {{ getKnowledgeCategoryLabel(item.category) }}
              </el-tag>
            </div>
            <p class="knowledge-desc">{{ item.description }}</p>
            <div class="knowledge-meta">
              <span class="meta-item">
                <el-icon><Document /></el-icon>
                {{ item.chunks }} 个切片
              </span>
              <span class="meta-item">
                <el-icon><Clock /></el-icon>
                更新于 {{ item.updatedAt }}
              </span>
            </div>
          </div>

          <el-empty v-if="filteredKnowledge.length === 0" description="未找到匹配的知识条目" />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <span class="selected-count">已选择 {{ selectedKnowledge.length }} 项知识</span>
          <div class="dialog-actions">
            <el-button @click="showKnowledgeDialog = false">取消</el-button>
            <el-button type="primary" :disabled="selectedKnowledge.length === 0" @click="applyKnowledge">
              引用到输入框
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Sparkles,
  SquarePen,
  Settings,
  LayoutList,
  BookOpen,
  TrendCharts,
  Activity,
  Loader2,
  X,
  MessageSquare,
  Upload,
  Close,
  Send,
  ChevronLeft,
  ChevronRight,
  Menu,
  CheckCircle2,
  AlertTriangle,
  AlertOctagon,
  Info,
  Network,
  Terminal,
  FileText,
  Download,
  User,
  Search,
  Document,
  Clock
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MessageBubble from '../components/MessageBubble.vue'
import AgentResultPanel from '../components/AgentResultPanel.vue'
import { streamDrugAgentChat, submitDrugAgentTask } from '../api/drug-agent'
import { appendAuditLog, getUserPreferences, setRecentTenderTask, upsertTenderTask } from '../utils/local-state'

const route = useRoute()
const router = useRouter()
const preferences = getUserPreferences()

// 状态管理
const activeView = ref('WORKSPACE')
const isSidebarOpen = ref(true)
const isTaskPaneOpen = ref(false)
const promptText = ref('')
const selectedReport = ref(null)
const messagesEndRef = ref(null)

// 知识库引用相关状态
const showKnowledgeDialog = ref(false)
const knowledgeSearch = ref('')
const knowledgeCategory = ref('all')
const selectedKnowledge = ref([])

// 模拟知识库数据
const knowledgeBase = ref([
  {
    id: 'kb-001',
    title: '围标行为认定规则',
    description: '用于识别投标人之间是否存在围标行为的判定规则，包括价格雷同、文件特征相似度等指标。',
    category: 'rule',
    chunks: 156,
    updatedAt: '2024-01-15'
  },
  {
    id: 'kb-002',
    title: '医疗设备采购合规标准',
    description: '医疗设备采购过程中需要遵守的法律法规、合规要求和标准流程。',
    category: 'policy',
    chunks: 89,
    updatedAt: '2024-02-20'
  },
  {
    id: 'kb-003',
    title: '合同风险条款库',
    description: '常见合同风险条款的识别模板和应对建议，包括付款周期、违约责任、知识产权等。',
    category: 'rag',
    chunks: 234,
    updatedAt: '2024-03-01'
  },
  {
    id: 'kb-004',
    title: '投标人资质审查规则',
    description: '投标人资质审查的标准流程和关键检查点，包括营业执照、资质证书、经营范围等。',
    category: 'rule',
    chunks: 67,
    updatedAt: '2024-01-28'
  },
  {
    id: 'kb-005',
    title: '药品集中采购政策',
    description: '国家药品集中采购相关政策文件、实施细则和操作指南。',
    category: 'policy',
    chunks: 312,
    updatedAt: '2024-02-10'
  },
  {
    id: 'kb-006',
    title: '标书相似度检测模型',
    description: '基于语义分析的标书相似度检测模型，用于识别技术方案雷同情况。',
    category: 'rag',
    chunks: 45,
    updatedAt: '2024-03-05'
  }
])

const filteredKnowledge = ref([...knowledgeBase.value])

const filterKnowledge = () => {
  let result = knowledgeBase.value

  if (knowledgeCategory.value !== 'all') {
    result = result.filter(item => item.category === knowledgeCategory.value)
  }

  if (knowledgeSearch.value) {
    const search = knowledgeSearch.value.toLowerCase()
    result = result.filter(item =>
      item.title.toLowerCase().includes(search) ||
      item.description.toLowerCase().includes(search)
    )
  }

  filteredKnowledge.value = result
}

const toggleKnowledge = (item) => {
  const index = selectedKnowledge.value.indexOf(item.id)
  if (index === -1) {
    selectedKnowledge.value.push(item.id)
  } else {
    selectedKnowledge.value.splice(index, 1)
  }
}

const getKnowledgeTagType = (category) => {
  const types = {
    rule: 'danger',
    rag: 'success',
    policy: 'warning'
  }
  return types[category] || ''
}

const getKnowledgeCategoryLabel = (category) => {
  const labels = {
    rule: '规则引擎',
    rag: 'RAG向量库',
    policy: '合规政策'
  }
  return labels[category] || category
}

const applyKnowledge = () => {
  const selectedItems = knowledgeBase.value.filter(
    item => selectedKnowledge.value.includes(item.id)
  )

  if (selectedItems.length > 0) {
    const knowledgeText = selectedItems.map(item =>
      `【${item.title}】${item.description}`
    ).join('\n\n')

    promptText.value = promptText.value
      ? `${promptText.value}\n\n参考知识：\n${knowledgeText}`
      : `参考知识：\n${knowledgeText}`

    ElMessage.success(`已引用 ${selectedItems.length} 条知识到输入框`)
  }

  // 重置状态
  showKnowledgeDialog.value = false
  selectedKnowledge.value = []
  knowledgeSearch.value = ''
  knowledgeCategory.value = 'all'
}

// 下载报告
const downloadReport = () => {
  if (!selectedReport.value) {
    ElMessage.warning('没有可下载的报告')
    return
  }

  // 显示下载格式选择对话框
  ElMessageBox.confirm(
    '请选择下载格式',
    '导出报告',
    {
      confirmButtonText: 'JSON 格式',
      cancelButtonText: 'PDF 格式',
      distinguishCancelAndClose: true,
      callback: (action) => {
        if (action === 'confirm') {
          downloadAsJson()
        } else if (action === 'cancel') {
          downloadAsText()
        }
      }
    }
  )
}

const downloadAsJson = () => {
  const report = selectedReport.value
  const dataStr = JSON.stringify(report, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `report-${report.traceId || Date.now()}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('报告已导出为 JSON 格式')
}

const downloadAsText = () => {
  const report = selectedReport.value
  const riskConfig = getRiskConfig(report.riskLevel)

  let content = `
=====================================
    结构化分析报告
=====================================

【基本信息】
- 场景类型: ${report.scene}
- 风险定级: ${riskConfig.label}
- 综合分值: ${report.score || 'N/A'}
- 处理文档: ${report.docCount || 0} 份
- 追踪ID: ${report.traceId || 'N/A'}

【结果摘要】
${report.summary || '暂无摘要'}

【管理摘要】
${(report.managementSummary || []).map((point, i) => `${i + 1}. ${point}`).join('\n')}

【建议动作】
${(report.suggestedActions || []).map((action, i) => `${i + 1}. ${action}`).join('\n')}

【执行步骤】
${(report.steps || []).map((step, i) => `${i + 1}. ${step}`).join('\n')}

=====================================
生成时间: ${new Date().toLocaleString('zh-CN')}
Drug-Agent Core v0.3
=====================================
`.trim()

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `report-${report.traceId || Date.now()}.txt`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  ElMessage.success('报告已导出为文本格式')
}

// 任务状态
const tasks = ref([
  {
    id: 'T-001',
    name: '年度设备采购标书分析',
    scene: 'TENDER',
    progress: 100,
    status: 'completed',
    time: '10分钟前',
    riskLevel: 'High',
    findings: '发现 87% 语义重合，疑似围标'
  }
])

// 会话数据
const sessions = ref([
  {
    id: 'sess_10293',
    title: '年度设备采购标书比对',
    dateGroup: '今天',
    scene: 'TENDER',
    messages: [
      {
        role: 'user',
        content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
        attachments: ['样例A_XX医院标书.docx', '样例B_XX药房投标.pdf'],
        time: '14:20'
      },
      {
        role: 'agent',
        content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。',
        time: '14:22',
        result: {
          scene: 'TENDER',
          riskLevel: 'High',
          summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。',
          evidenceList: [
            '片段A：设备的额定功率需满足 1500W-1800W 区间，且外壳需采用医用级 ABS 材质。',
            '片段B：该机器额定功率符合 1500W 至 1800W，外壳材料为医用级 ABS。'
          ],
          steps: ['文档解析与 OCR', '语义块向量化抽取', '相似度比对与规则过滤', 'LLM 生成审查意见'],
          traceId: 'TRC-99281-A'
        }
      }
    ]
  },
  {
    id: 'sess_10290',
    title: '骨科耗材供应商协议预审',
    dateGroup: '昨天',
    scene: 'CONTRACT',
    messages: [
      {
        role: 'user',
        content: '审查一下这份最新的采购合同框架，按知识库提取风险条款。',
        attachments: ['骨科耗材采购合同_v3.pdf'],
        time: '16:05'
      },
      {
        role: 'agent',
        content: '合同预审完毕。整体结构完整，但提取到几处需要关注的潜在风险条款。',
        time: '16:06',
        result: {
          scene: 'CONTRACT',
          riskLevel: 'Medium',
          summary: '发现 3 条倾向于供应商的免责声明及付款周期违规条款。',
          evidenceList: ['条款 4.2: 甲方需在收到发票后 5 个工作日内结清全款 (违背常规 30 天周期)'],
          steps: ['合同条款结构化切分', '规则引擎强匹配', '风险评级测算'],
          traceId: 'TRC-99282-B'
        }
      }
    ]
  }
])

const activeSessionId = ref(null)
const activeSession = computed(() =>
  activeSessionId.value ? sessions.value.find(s => s.id === activeSessionId.value) : null
)

// UI 状态
const loading = ref(false)
const selectedFiles = ref([])
const scrollContainer = ref(null)
const fileInput = ref(null)

// 常量定义
const MAX_FILE_SIZE = 20 * 1024 * 1024
const getFileKey = (file) => `${file.name}-${file.size}-${file.lastModified}`
const createMessageId = (role) => `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

// 导航项
const navItems = [
  { id: 'TASKS', label: '全局任务看板', icon: LayoutList },
  { id: 'KNOWLEDGE', label: '合规知识库', icon: BookOpen }
]

// 历史会话分组
const historyGroups = computed(() => {
  const groups = {}

  sessions.value.forEach(session => {
    if (!groups[session.dateGroup]) {
      groups[session.dateGroup] = []
    }
    groups[session.dateGroup].push(session)
  })

  return Object.entries(groups).map(([date, sessions]) => ({
    date,
    sessions
  }))
})

// 计算属性
const runningTasks = computed(() => tasks.value.filter(t => t.status === 'running'))
const messages = computed(() => activeSession.value?.messages || [])

// 方法定义
const getHeaderTitle = () => {
  switch (activeView.value) {
    case 'TASKS': return '任务调度看板'
    case 'KNOWLEDGE': return '知识大脑'
    case 'SETTINGS': return '系统配置'
    default: return 'Agent 审查工作台'
  }
}

const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}

const toggleTaskPane = () => {
  isTaskPaneOpen.value = !isTaskPaneOpen.value
}

const handleNavClick = (id) => {
  activeView.value = id
}

const handleNewChat = () => {
  activeSessionId.value = null
  activeView.value = 'WORKSPACE'
}

const loadSession = (session) => {
  activeSessionId.value = session.id
  activeView.value = 'WORKSPACE'
}

const getRiskConfig = (level) => {
  switch (level) {
    case 'High':
    case 'HIGH':
      return {
        color: 'text-rose-600',
        bg: 'bg-rose-50',
        border: 'border-rose-200',
        label: '高风险',
        icon: AlertOctagon
      }
    case 'Medium':
    case 'MEDIUM':
      return {
        color: 'text-amber-600',
        bg: 'bg-amber-50',
        border: 'border-amber-200',
        label: '中风险',
        icon: AlertTriangle
      }
    case 'Low':
    case 'LOW':
      return {
        color: 'text-emerald-600',
        bg: 'bg-emerald-50',
        border: 'border-emerald-200',
        label: '低风险',
        icon: CheckCircle2
      }
    default:
      return {
        color: 'text-slate-600',
        bg: 'bg-slate-50',
        border: 'border-slate-200',
        label: '信息',
        icon: Info
      }
  }
}

const getRiskClass = (level) => {
  return getRiskConfig(level).color.replace('text-', 'risk-')
}

const getRiskIcon = (level) => {
  return getRiskConfig(level).icon
}

const addTask = (name, scene) => {
  const newTask = {
    id: `T-${Math.floor(Math.random() * 1000)}`,
    name: name || '未命名任务',
    scene: scene || 'UNKNOWN',
    progress: 0,
    status: 'running',
    time: '刚刚',
    riskLevel: 'Unknown',
    findings: '正在初始化 Agent 工作流...'
  }
  tasks.value = [newTask, ...tasks.value]
  return newTask.id
}

const updateTaskProgress = (id, progress, status = 'running') => {
  tasks.value = tasks.value.map(t =>
    t.id === id ? { ...t, progress, status } : t
  )
}

// 组件挂载
onMounted(() => {
  // 优先处理路由参数
  if (route.query.new) {
    handleReset()
  } else if (route.query.history) {
    const session = sessions.value.find(s => s.id === route.query.history)
    if (session) {
      loadSession(session)
    }
  } else {
    // 默认加载第一个历史会话
    activeSessionId.value = sessions.value[0]?.id
  }

  // 监听自定义事件
  window.addEventListener('reset-workbench', handleReset)
  window.addEventListener('load-history', (e) => {
    const session = sessions.value.find(s => s.id === e.detail.historyId)
    if (session) {
      loadSession(session)
    }
  })
})

onUnmounted(() => {
  window.removeEventListener('reset-workbench', handleReset)
  window.removeEventListener('load-history', () => {})
})

const handleReset = () => {
  activeSessionId.value = null
  selectedFiles.value = []
  promptText.value = ''
}

// 监听消息变化
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

// 监听活动会话变化
watch(
  activeSession,
  () => {
    nextTick(() => {
      if (scrollContainer.value) {
        scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
      }
    })
  }
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

const handleViewDetail = (result) => {
  selectedReport.value = result
}

const translateRiskLevel = (level) => {
  if (level === 'High' || level === 'HIGH' || level === '高风险') return '高风险'
  if (level === 'Medium' || level === 'MEDIUM' || level === '中风险') return '中风险'
  return '低风险'
}

const handleSubmit = async (overrideInput = null) => {
  const textToProcess = overrideInput || promptText.value
  if (!textToProcess.trim()) return

  loading.value = true
  promptText.value = ''
  selectedFiles.value = []
  selectedReport.value = null

  const isNewSession = !activeSessionId.value
  const currentSessionId = activeSessionId.value || `sess_${Date.now()}`
  const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })

  const userFiles = selectedFiles.value.map(f => ({ name: f.name }))
  const newUserMsg = {
    id: createMessageId('user'),
    role: 'user',
    content: textToProcess,
    time: timestamp,
    attachments: userFiles.length > 0 ? userFiles.map(f => f.name) : undefined
  }

  if (isNewSession) {
    const newSess = {
      id: currentSessionId,
      title: textToProcess.substring(0, 15) + '...',
      dateGroup: '今天',
      scene: 'UNKNOWN',
      messages: [newUserMsg]
    }
    sessions.value = [newSess, ...sessions.value]
    activeSessionId.value = currentSessionId
  } else {
    sessions.value = sessions.value.map(s =>
      s.id === currentSessionId
        ? { ...s, messages: [...s.messages, newUserMsg] }
        : s
    )
  }

  try {
    // 添加中转消息
    const assistantId = createMessageId('assistant')
    const isRouting = true

    sessions.value = sessions.value.map(s =>
      s.id === currentSessionId
        ? {
            ...s,
            messages: [
              ...s.messages,
              {
                id: assistantId,
                role: 'assistant',
                content: 'Agent 正在执行深层编排工作流...',
                time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
              }
            ]
          }
        : s
    )

    // 模拟 API 调用
    setTimeout(async () => {
      try {
        const isLowRisk = Math.random() > 0.3
        const scene = textToProcess.includes('标书') ? 'TENDER' : 'CONTRACT'

        const agentReply = {
          id: createMessageId('assistant'),
          role: 'agent',
          content: isLowRisk
            ? '我已经完成了对您上传文档的审查。本次共审查了 2 份文档，未发现保留的高风险命中，建议将结果作为低风险基线。详细报告已生成，请查看下方卡片。'
            : '审查已完成。系统在多份文件中发现了高度雷同的排版与语义特征，已判定为高风险。请务必查看详细报告并进行人工复核。',
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          result: {
            scene: scene,
            riskLevel: isLowRisk ? 'Low' : 'High',
            score: isLowRisk ? 0 : 87,
            docCount: 2,
            summary: isLowRisk
              ? 'No retained high-risk hits after rule scan.'
              : '发现显著的语义及排版雷同，疑似存在围标行为。',
            managementSummary: [
              `本次共审查 2 份文档，综合风险等级为 ${isLowRisk ? 'LOW' : 'HIGH'}，融合分值为 ${isLowRisk ? 0 : 87}。`,
              isLowRisk
                ? '当前未保留高风险命中，建议将结果作为低风险基线，并对关键章节进行抽样复核。'
                : '触发了强制拦截规则，建议立即中止当前流程并启动专项审计调查。'
            ],
            suggestedActions: [
              isLowRisk
                ? '保留当前报告作为初筛结果，并结合业务经验抽样检查重点章节。'
                : '导出证据链报告，并约谈相关供应商。'
            ],
            steps: [
              'scene_route',
              'structured_load',
              'rule_hit',
              'false_positive_exemption',
              'risk_fusion',
              'evidence_assembly',
              'report_generation'
            ],
            traceId: `TRC-${Date.now()}`
          }
        }

        // 更新任务进度
        const taskId = addTask(isLowRisk ? '常规分析任务' : '高风险审查任务', scene)
        setTimeout(() => {
          updateTaskProgress(taskId, 100, 'completed')
        }, 2000)

        // 添加最终回复
        sessions.value = sessions.value.map(s =>
          s.id === currentSessionId
            ? { ...s, messages: [...s.messages.slice(0, -1), agentReply] }
            : s
        )
      } catch (error) {
        const errorReply = {
          id: createMessageId('assistant'),
          role: 'agent',
          content: '解析时发生网络或模型错误，请稍后再试。',
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }

        sessions.value = sessions.value.map(s =>
          s.id === currentSessionId
            ? { ...s, messages: [...s.messages.slice(0, -1), errorReply] }
            : s
        )
      }
    }, 2000)

  } catch (error) {
    ElMessage.error('分析失败')
  } finally {
    loading.value = false
  }
}

// 快捷操作卡片
const quickActions = [
  {
    icon: FileText,
    label: '标书审查',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    color: 'text-indigo-600',
    bg: 'bg-indigo-50'
  },
  {
    icon: Settings,
    label: '合同预审',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    color: 'text-emerald-600',
    bg: 'bg-emerald-50'
  },
  {
    icon: AlertTriangle,
    label: '合规预警',
    prompt: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    color: 'text-amber-600',
    bg: 'bg-amber-50'
  }
]
</script>

<style scoped>
/* 主容器 */
.app-container {
  display: flex;
  height: 100vh;
  background: #f8fafc;
  overflow: hidden;
}

/* 左侧侧边栏 */
.sidebar {
  width: 288px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
  flex-shrink: 0;
  z-index: 20;
}

.sidebar-collapsed {
  width: 80px;
}

.sidebar-header {
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.logo {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.2);
}

.logo-text {
  font-weight: 700;
  font-size: 16px;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.new-chat-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #64748b;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.new-chat-btn:hover {
  background: #dbeafe;
  color: #2563eb;
}

.sidebar-nav {
  padding: 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
  border: 1px solid transparent;
}

.nav-item:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.nav-item.active {
  background: white;
  color: #1e293b;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
  border-color: #e2e8f0;
}

.sidebar-history {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.history-title {
  padding: 4px 12px;
  margin-bottom: 8px;
}

.title-text {
  font-size: 10px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.history-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.group-date {
  font-size: 10px;
  font-weight: 600;
  color: #94a3b8;
  padding: 0 12px;
  margin-bottom: 4px;
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.history-item:hover {
  background: #f1f5f9;
}

.history-item.active {
  background: #dbeafe;
  color: #1e40af;
  font-weight: 500;
}

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #f1f5f9;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 14px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.settings-item:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.settings-item.active {
  background: white;
  color: #2563eb;
  font-weight: 600;
}

.collapse-btn {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  border-radius: 12px;
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
  justify-content: center;
}

.collapse-btn:hover {
  background: #f1f5f9;
  color: #64748b;
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  background: white;
  box-shadow: -8px 0 24px -12px rgba(0, 0, 0, 0.05);
  z-index: 30;
}

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

.chat-page.with-drawer .message-stream {
  width: min(680px, 100%);
}

/* 右侧详情报告抽屉 */
.detail-drawer {
  position: fixed;
  top: 0;
  right: 0;
  width: 33.333333%;
  height: 100vh;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  box-shadow: -12px 0 24px -12px rgba(0, 0, 0, 0.05);
  animation: slideInRight 0.3s ease-out;
}

@keyframes slideInRight {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

.drawer-header {
  height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.back-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #f1f5f9;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.download-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  transition: all 0.2s;
}

.download-btn:hover {
  color: #64748b;
}

.drawer-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
}

.risk-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.text-right {
  text-align: right;
}

.info-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.risk-level {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 18px;
  font-weight: 800;
}

.risk-level.risk-high {
  color: #e53e3e;
}

.risk-level.risk-medium {
  color: #d69e2e;
}

.risk-level.risk-low {
  color: #38a169;
}

.score-value {
  font-size: 24px;
  font-weight: 850;
  color: #1e293b;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 12px;
}

.card-text {
  font-size: 14px;
  color: #475569;
  line-height: 1.6;
}

.list-items {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-items li {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  padding-left: 16px;
  position: relative;
}

.list-items li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #cbd5e1;
  font-weight: bold;
}

.action-card {
  border-left: 4px solid #3b82f6;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
  padding-left: 20px;
}

.steps-list::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: #e2e8f0;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  position: relative;
}

.step-item:last-child {
  padding-bottom: 0;
}

.step-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #cbd5e1;
  border: 2px solid #fff;
  position: absolute;
  left: -20px;
  z-index: 1;
}

.step-item span {
  font-size: 13px;
  font-family: monospace;
  color: #64748b;
}

@media (max-width: 900px) {
  .workflow-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* 任务中心抽屉 */
.task-drawer {
  position: fixed;
  top: 56px;
  right: 12px;
  width: 320px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border: 1px solid #e2e8f0;
  z-index: 100;
  overflow: hidden;
}

.task-drawer-header {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
}

.task-drawer-header h3 {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.task-list {
  max-height: 400px;
  overflow-y: auto;
}

.task-item {
  padding: 16px;
  border-bottom: 1px solid #f8fafc;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-item:hover {
  background: #f8fafc;
}

.task-item:last-child {
  border-bottom: none;
}

.task-info {
  flex: 1;
}

.task-name {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.task-meta {
  display: flex;
  gap: 8px;
  font-size: 10px;
  color: #94a3b8;
}

.task-id {
  font-family: monospace;
}

.task-status {
  font-weight: 600;
}

.task-status-icon {
  color: #10b981;
}

.task-progress {
  font-size: 10px;
  font-weight: 700;
  color: #2563eb;
}

/* 过渡动画 */
.drawer-transition-enter-active,
.drawer-transition-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.drawer-transition-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.drawer-transition-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 快捷操作卡片 */
.quick-action-card {
  background: white;
  border: 1px solid #e2e8f0;
  padding: 16px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-action-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  transition: transform 0.2s;
}

.quick-action-card:hover .action-icon {
  transform: scale(1.1);
}

.action-label {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.action-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 步骤名称样式 */
.step-name {
  font-size: 13px;
  font-family: monospace;
  color: #64748b;
}

/* 风险颜色类 */
.risk-rose-600 {
  color: #e11d48;
}

.risk-amber-600 {
  color: #d97706;
}

.risk-emerald-600 {
  color: #059669;
}

/* 活动指示器 */
.is-running {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 知识库引用对话框样式 */
.knowledge-dialog-content {
  max-height: 500px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-box {
  margin-bottom: 8px;
}

.knowledge-categories {
  display: flex;
  justify-content: center;
}

.knowledge-list {
  flex: 1;
  overflow-y: auto;
  max-height: 320px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.knowledge-item {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}

.knowledge-item:hover {
  border-color: #93c5fd;
  background: #f8fafc;
}

.knowledge-item.selected {
  border-color: #3b82f6;
  background: #eff6ff;
}

.knowledge-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.knowledge-title {
  flex: 1;
  font-weight: 600;
  color: #1e293b;
  font-size: 14px;
}

.knowledge-desc {
  font-size: 12px;
  color: #64748b;
  margin: 0 0 10px;
  line-height: 1.5;
}

.knowledge-meta {
  display: flex;
  gap: 16px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #94a3b8;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selected-count {
  font-size: 13px;
  color: #64748b;
}

.dialog-actions {
  display: flex;
  gap: 8px;
}
</style>
