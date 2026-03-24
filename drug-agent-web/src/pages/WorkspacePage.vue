<template>
  <div class="h-full flex flex-col bg-gradient-to-b from-slate-50 to-white">
    <!-- Chat Area -->
    <div
      class="flex-1"
      :class="hasActiveSession ? 'overflow-y-auto' : 'flex items-center justify-center px-6 py-10'"
    >
      <!-- Empty State -->
      <div v-if="!hasActiveSession" class="w-full max-w-3xl">
        <!-- Hero Section -->
        <div class="text-center mb-16">
          <div class="relative inline-block mb-8">
            <!-- 专业克制的图标 - 无过度炫光 -->
            <div class="w-20 h-20 rounded-2xl bg-indigo-600 flex items-center justify-center shadow-xl shadow-indigo-500/20">
              <Shield class="w-10 h-10 text-white" />
            </div>
          </div>
          <h1 class="text-4xl font-bold text-slate-800 mb-4 tracking-tight">
            上传标书文件，AI 将自动审查围标风险
          </h1>
          <p class="text-lg text-slate-500 max-w-xl mx-auto">
            当前版本重点识别围标线索，完整语义查重能力持续建设中
          </p>
        </div>

        <!-- Quick Action Cards -->
        <div class="grid grid-cols-3 gap-6">
          <div
            v-for="action in quickActions"
            :key="action.label"
            class="group bg-white rounded-xl p-6 border border-slate-200 hover:border-indigo-200 hover:shadow-lg transition-all duration-300 cursor-pointer"
            @click="handleQuickAction(action)"
          >
            <div
              class="w-12 h-12 rounded-xl flex items-center justify-center mb-4 transition-transform duration-300 group-hover:scale-110"
              :class="[action.bgColor, action.textColor]"
            >
              <component :is="action.icon" class="w-6 h-6" />
            </div>
            <h3 class="font-bold text-slate-800 mb-2">{{ action.label }}</h3>
            <p class="text-sm text-slate-500 leading-relaxed">{{ action.description }}</p>
          </div>
        </div>
      </div>

      <!-- Chat Messages -->
      <div v-else class="max-w-3xl mx-auto px-6 py-8 pb-36">
        <div class="space-y-6">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="animate-fadeIn"
          >
            <!-- User Message -->
            <div v-if="message.role === 'user'" class="flex gap-4 justify-end">
              <div class="max-w-md">
                <div class="bg-indigo-600 text-white rounded-xl rounded-br-md px-5 py-3 shadow-sm">
                  <p class="text-sm">{{ message.content }}</p>
                </div>
                <!-- Attachments -->
                <div v-if="message.attachments?.length" class="flex flex-wrap justify-end gap-2 mt-2">
                  <span
                    v-for="(file, idx) in message.attachments"
                    :key="idx"
                    class="px-2 py-1 bg-slate-100 text-slate-600 text-xs rounded-lg"
                  >
                    {{ file }}
                  </span>
                </div>
              </div>
              <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-slate-600 text-xs font-bold flex-shrink-0">
                U
              </div>
            </div>

            <!-- Agent Message -->
            <div v-else-if="message.isLoading" class="flex gap-4 justify-start">
              <div class="w-8 h-8 rounded-full bg-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                DA
              </div>
              <!-- Skeleton Loading State -->
              <div class="max-w-2xl bg-white border border-slate-200 rounded-xl rounded-bl-md px-5 py-4 shadow-sm">
                <div class="space-y-3">
                  <div class="h-4 bg-slate-100 rounded skeleton-base w-3/4"></div>
                  <div class="h-4 bg-slate-100 rounded skeleton-base w-1/2"></div>
                  <div class="flex items-center gap-2 mt-3">
                    <Loader2 class="w-4 h-4 text-indigo-500 animate-spin" />
                    <span class="text-sm text-slate-500">{{ message.content || '正在分析...' }}</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- Agent Message - with optional result -->
            <div v-else-if="message.role === 'assistant' || message.role === 'agent'" class="flex flex-col gap-4 justify-start">
              <div class="flex gap-4">
                <div class="w-8 h-8 rounded-full bg-indigo-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                  DA
                </div>
                <div class="max-w-2xl bg-white border border-slate-200 rounded-xl rounded-bl-md px-5 py-3 shadow-sm">
                  <div
                    v-if="isMarkdownContent(message.content)"
                    class="text-sm text-slate-700 report-markdown"
                    v-html="renderMarkdown(message.content)"
                  />
                  <p v-else class="text-sm text-slate-700">{{ message.content }}</p>
                </div>
              </div>

              <!-- Message-bound Result Panel -->
              <div v-if="message.result" class="ml-12 animate-fadeIn">
                <div class="bg-white rounded-xl border border-slate-200 shadow-card overflow-hidden">
                  <!-- Risk Header -->
                  <div class="px-6 py-5 bg-slate-50 border-b border-slate-200">
                    <div class="flex items-start justify-between gap-4">
                      <div class="flex items-center gap-4">
                        <div
                          class="px-3 py-1.5 rounded-full text-xs font-bold"
                          :class="{
                            'bg-red-100 text-red-700': isHighRisk(message.result.riskLevel),
                            'bg-amber-100 text-amber-700': isMediumRisk(message.result.riskLevel),
                            'bg-emerald-100 text-emerald-700': isLowRisk(message.result.riskLevel)
                          }"
                        >
                          <component :is="isHighRisk(message.result.riskLevel) ? ShieldAlert : isMediumRisk(message.result.riskLevel) ? AlertCircle : CheckCircle2" class="w-4 h-4 inline mr-1" />
                          {{ translateRiskLabel(message.result.riskLevel) }}
                        </div>
                        <div class="pt-1">
                          <div class="text-xs text-slate-500">{{ getSceneLabel(message.result.scene) }}</div>
                          <div class="text-[11px] text-slate-400 font-mono mt-1">{{ message.result.traceId }}</div>
                        </div>
                      </div>

                      <div class="flex flex-wrap justify-end gap-2">
                        <button
                          v-if="getFullReportMarkdown(message.result)"
                          class="px-3 py-1.5 text-xs font-medium rounded-lg border border-indigo-200 text-indigo-600 bg-indigo-50 hover:bg-indigo-100 transition-colors"
                          @click="openReportDialog(message.result)"
                        >
                          查看完整报告
                        </button>
                      </div>
                    </div>

                    <div class="mt-4 grid grid-cols-2 md:grid-cols-4 gap-3">
                      <div
                        v-for="metric in getSummaryMetrics(message.result)"
                        :key="metric.label"
                        class="rounded-xl border border-slate-200 bg-white/80 px-4 py-3"
                      >
                        <div class="text-[11px] text-slate-500">{{ metric.label }}</div>
                        <div class="text-sm font-semibold text-slate-800 mt-1">{{ metric.value }}</div>
                      </div>
                    </div>
                  </div>

                  <!-- Summary -->
                  <div class="px-6 py-4 border-b border-slate-100">
                    <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">结果摘要</h4>
                    <p class="text-sm text-slate-700 leading-7">{{ normalizeSummary(message.result.summary) }}</p>
                  </div>

                  <!-- Evidence List -->
                  <div v-if="getPreviewEvidenceList(message.result).length" class="px-6 py-4">
                    <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">关键证据</h4>
                    <ul class="space-y-2">
                      <li
                        v-for="(evidence, idx) in getPreviewEvidenceList(message.result)"
                        :key="idx"
                        class="flex gap-2 text-xs text-slate-600"
                      >
                        <span class="text-indigo-400 flex-shrink-0">•</span>
                        <span class="leading-relaxed">{{ formatEvidence(evidence) }}</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area - 提升层级 -->
    <div
      :class="
        hasActiveSession
          ? 'w-full max-w-3xl mx-auto px-6 pb-6'
          : 'w-full max-w-2xl mx-auto px-6 pb-4'
      "
      class="relative z-10"
    >
      <div class="bg-white rounded-xl shadow-xl border border-slate-200 p-4 transition-all duration-300 focus-within:shadow-xl focus-within:border-indigo-200">
        <!-- File Preview -->
        <div v-if="selectedFiles.length > 0" class="flex flex-wrap gap-2 mb-3">
          <div
            v-for="(file, index) in selectedFiles"
            :key="index"
            class="flex items-center gap-2 px-3 py-1.5 bg-slate-100 rounded-lg text-xs text-slate-600"
          >
            <FileIcon class="w-3.5 h-3.5" />
            <span class="max-w-24 truncate">{{ file.name }}</span>
            <button
              class="hover:text-red-500 transition-colors"
              @click="removeFile(index)"
            >
              <X class="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        <!-- Textarea -->
        <textarea
          v-model="inputText"
          :placeholder="hasActiveSession ? '向 Agent 追加要求或提供更多材料...' : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'"
          class="w-full min-h-[48px] max-h-36 border-0 resize-none outline-none text-sm text-slate-800 placeholder:text-slate-400"
          rows="1"
          @keydown.enter.exact.prevent="handleSend"
        />

        <!-- Footer -->
        <div class="flex items-center justify-between pt-2 border-t border-slate-100">
          <div class="flex items-center gap-2">
            <button
              class="flex items-center gap-1.5 px-3 py-1.5 text-xs text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
              @click="triggerFileInput"
            >
              <Upload class="w-4 h-4" />
              上传材料
            </button>
            <input
              ref="fileInput"
              type="file"
              multiple
              class="hidden"
              @change="handleFileChange"
            />
          </div>

          <button
            class="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/30 hover:shadow-indigo-500/40 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="!inputText.trim() && selectedFiles.length === 0"
            @click="handleSend"
          >
            <Send class="w-4 h-4" />
            发送任务
          </button>
        </div>
      </div>

      <p class="text-center text-xs text-slate-400 mt-3">
        AI 生成内容仅供参考，重大决策请人工复核 (横渡智能系统 v1.0)
      </p>
    </div>
  </div>

  <!-- 报告弹窗 -->
  <el-dialog
    v-model="reportDialogVisible"
    title="标书审查报告"
    width="800px"
    :close-on-click-modal="true"
    class="report-dialog"
  >
    <div class="report-content-wrapper">
      <div
        v-if="currentReportContent"
        class="text-sm text-slate-700 report-markdown"
        v-html="renderMarkdown(currentReportContent)"
      />
      <div v-else class="text-center text-slate-400 py-8">暂无报告内容</div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import {
  Sparkles,
  FileText,
  Shield,
  AlertTriangle,
  Send,
  Upload,
  X,
  File as FileIcon,
  ShieldAlert,
  CheckCircle2,
  AlertCircle,
  Loader2
} from 'lucide-vue-next'
import { useSessionStore } from '@/stores/session'
import { chatApi } from '@/services/chatApi'
import { submitDrugAgentTask } from '@/api/drug-agent'

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()

const inputText = ref('')
const selectedFiles = ref([])
const fileInput = ref(null)
const isLoading = ref(false)
const expandedReports = ref({})
const expandedProcess = ref({})
const reportDialogVisible = ref(false)
const currentReportContent = ref('')
const processStepStates = ref({}) // { messageId: { stepIndex: { status, visibleHighlights } } }
const processPlaybackTimers = ref({}) // { messageId: [] }

// Get active session from store
const activeSession = computed(() => {
  const sessionId = route.query.sessionId
  if (sessionId) {
    return sessionStore.sessions.find(s => s.id === sessionId) || null
  }
  return sessionStore.activeSession
})

const messages = computed(() => {
  return activeSession.value?.messages || []
})

const hasActiveSession = computed(() => {
  return messages.value.length > 0
})

// Functions to compute result-related data from a message's result object
const getFullReportMarkdown = (result) => {
  return result?.report?.markdownContent || result?.answer || ''
}

const getSummaryMetrics = (result) => {
  if (!result) return []
  const overview = result.report?.overview || {}
  return [
    { label: '综合风险', value: translateRiskLabel(result.riskLevel) },
    { label: '参考分值', value: result.score ?? overview.score ?? 0 },
    { label: '比对文件', value: `${overview.documentCount ?? result.docCount ?? 0} 份` },
    { label: '有效风险项', value: `${overview.effectiveHitCount ?? 0} 条` }
  ]
}

const getPreviewEvidenceList = (result) => {
  return (result?.evidenceList || []).slice(0, 3)
}

const getProcessTimeline = (result) => {
  if (!result) return []

  const report = result.report || {}
  const overview = report.overview || {}
  const riskItems = report.riskItems || []
  const evidenceGroups = result.evidenceGroups || []

  const explanationEntries = report.explanations
    ? Object.entries(report.explanations).map(([key, value]) => `${translateExplanationKey(key)}：${value}`)
    : []

  const topRiskTitles = riskItems.length
    ? riskItems.slice(0, 3).map(item => `${item.title}（${item.riskLevel}）`)
    : ['未发现保留的高风险命中']

  const fullReportMarkdown = getFullReportMarkdown(result)

  return [
    {
      key: 'scene_route',
      title: '场景路由',
      description: '识别当前请求属于标书审查场景，并进入结构化审查工作流。',
      highlights: [
        `识别场景：${result.scene === 'TENDER_REVIEW' || result.scene === 'TENDER' ? '标书审查' : (result.scene || '标书审查')}`,
        `风险等级输出目标：${translateRiskLabel(result.riskLevel)}`
      ]
    },
    {
      key: 'structured_load',
      title: '结构化加载',
      description: '解析上传文档并提取后续规则扫描需要的结构化数据。',
      highlights: [
        `比对文件数量：${overview.documentCount ?? result.docCount ?? 0} 份`,
        `识别潜在线索数：${overview.rawHitCount ?? 0} 条`
      ]
    },
    {
      key: 'rule_hit',
      title: '规则命中分析',
      description: '执行规则引擎，筛出需要重点关注的异常模式。',
      highlights: topRiskTitles
    },
    {
      key: 'false_positive_exemption',
      title: '误报豁免',
      description: '对模板复用、标准引用等合理重复内容进行降权或豁免。',
      highlights: [
        `系统豁免或降权项：${overview.exemptionCount ?? 0} 条`,
        explanationEntries.find(item => item.startsWith('豁免说明')) || '豁免说明：当前任务未触发明显豁免项。'
      ]
    },
    {
      key: 'risk_fusion',
      title: '风险融合',
      description: '综合规则命中、权重和豁免结果，形成最终风险评级。',
      highlights: [
        `综合风险等级：${translateRiskLabel(result.riskLevel)}`,
        `风险参考分值：${result.score ?? overview.score ?? 0}`,
        explanationEntries.find(item => item.startsWith('综合说明')) || `综合说明：${normalizeSummary(result.summary)}`
      ]
    },
    {
      key: 'evidence_assembly',
      title: '证据组装',
      description: '将零散命中线索聚合为可回溯的证据链条。',
      highlights: evidenceGroups.length
        ? evidenceGroups.slice(0, 4).map(group => `${group.title || group.groupKey}：${group.summary || '已形成证据组'}`)
        : ['当前未形成可展示的证据组。']
    },
    {
      key: 'report_generation',
      title: '报告生成',
      description: '按模板输出结构化审核报告，供页面展示和导出。',
      highlights: [
        `报告章节：${fullReportMarkdown ? '已生成完整模板报告' : '仅生成摘要内容'}`,
        explanationEntries.find(item => item.startsWith('证据说明')) || '证据说明：报告已纳入证据链与交叉印证内容。'
      ]
    }
  ]
}

const getAnimatedProcessTimeline = (result) => {
  const timeline = getProcessTimeline(result)
  const messageId = result?._msgId || 'default'
  const states = processStepStates.value[messageId] || []
  return timeline.map((item, index) => {
    const state = states[index] || { status: 'pending', visibleHighlights: 0 }
    return {
      ...item,
      status: state.status,
      visibleHighlights: item.highlights.slice(0, state.visibleHighlights)
    }
  })
}

// Toggle functions for per-message expand/collapse
const toggleFullReport = (messageId) => {
  expandedReports.value[messageId] = !expandedReports.value[messageId]
}

const toggleProcessDetails = (messageId) => {
  const current = expandedProcess.value[messageId]
  expandedProcess.value[messageId] = !current
  if (!current) {
    // Starting to show process details, init playback state
    startProcessPlayback(messageId)
  } else {
    resetProcessPlayback(messageId)
  }
}

const clearProcessPlaybackTimers = () => {
  Object.values(processPlaybackTimers.value).forEach(timers => {
    timers.forEach(timer => clearTimeout(timer))
  })
  processPlaybackTimers.value = {}
}

const resetProcessPlayback = (messageId) => {
  if (!messageId) return
  // Clear only this message's timers
  if (processPlaybackTimers.value[messageId]) {
    processPlaybackTimers.value[messageId].forEach(timer => clearTimeout(timer))
    delete processPlaybackTimers.value[messageId]
  }
  const timeline = getProcessTimeline(messages.value.find(m => m.id === messageId)?.result)
  processStepStates.value[messageId] = timeline.map(() => ({
    status: 'pending',
    visibleHighlights: 0
  }))
}

const startProcessPlayback = (messageId) => {
  if (!messageId) return
  const result = messages.value.find(m => m.id === messageId)?.result
  if (!result) return

  resetProcessPlayback(messageId)
  const timeline = getProcessTimeline(result)
  if (!timeline.length) return

  // Initialize timers array for this message
  if (!processPlaybackTimers.value[messageId]) {
    processPlaybackTimers.value[messageId] = []
  }

  let elapsed = 0

  timeline.forEach((step, stepIndex) => {
    const startTimer = setTimeout(() => {
      if (!processStepStates.value[messageId]) {
        processStepStates.value[messageId] = []
      }
      processStepStates.value[messageId][stepIndex] = {
        status: 'running',
        visibleHighlights: 0
      }
    }, elapsed)
    processPlaybackTimers.value[messageId].push(startTimer)

    elapsed += 320

    step.highlights.forEach((_, highlightIndex) => {
      const highlightTimer = setTimeout(() => {
        if (processStepStates.value[messageId]) {
          processStepStates.value[messageId][stepIndex] = {
            status: 'running',
            visibleHighlights: highlightIndex + 1
          }
        }
      }, elapsed)
      processPlaybackTimers.value[messageId].push(highlightTimer)
      elapsed += 240
    })

    const completeTimer = setTimeout(() => {
      if (processStepStates.value[messageId]) {
        processStepStates.value[messageId][stepIndex] = {
          status: 'completed',
          visibleHighlights: step.highlights.length
        }
      }
    }, elapsed)
    processPlaybackTimers.value[messageId].push(completeTimer)
    elapsed += 180
  })
}

onBeforeUnmount(() => {
  clearProcessPlaybackTimers()
})

const quickActions = [
  {
    icon: FileText,
    label: '标书审查',
    description: '上传标书文件，AI 自动筛查围标风险线索',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有围标嫌疑。',
    bgColor: 'bg-indigo-50',
    textColor: 'text-indigo-600'
  },
  {
    icon: Shield,
    label: '合同预审',
    description: '上传采购合同，AI 识别条款风险点',
    prompt: '审查最新版本的采购合同，识别潜在风险条款。',
    bgColor: 'bg-emerald-50',
    textColor: 'text-emerald-600'
  },
  {
    icon: AlertTriangle,
    label: '合规预警',
    description: '监控采购数据波动，识别异常风险信号',
    prompt: '分析近3个月的骨科耗材采购数据，识别异常波动风险。',
    bgColor: 'bg-amber-50',
    textColor: 'text-amber-600'
  }
]

const handleQuickAction = (action) => {
  inputText.value = action.prompt
  handleSend()
}

const handleSend = async () => {
  if (!inputText.value.trim() && selectedFiles.value.length === 0) return

  // Create new session if needed
  let sessionId = activeSession.value?.id
  if (!sessionId) {
    const newSessionRes = await chatApi.createSession({
      title: '新对话',
      scene: 'general'
    })
    const newSession = newSessionRes.data
    const existingSession = sessionStore.sessions.find(s => s.id === newSession.id)
    if (!existingSession) {
      sessionStore.sessions.unshift({
        ...newSession,
        messages: newSession.messages || []
      })
    }
    sessionStore.setActiveSession(newSession.id)
    sessionId = newSession.id
  }

  // Store user input for API call
  const userContent = inputText.value
  const files = selectedFiles.value
  const fileNames = files.map(f => f.name)

  // Add user message to store (for non-file case, file case handled by backend)
  if (files.length === 0) {
    sessionStore.addMessage(sessionId, {
      role: 'user',
      content: userContent,
      attachments: undefined
    })
  }

  // Clear input
  inputText.value = ''
  selectedFiles.value = []

  // Add loading indicator
  const loadingMsgId = Date.now().toString()
  sessionStore.addMessage(sessionId, {
    id: loadingMsgId,
    role: 'agent',
    content: '正在分析您的请求，请稍候...',
    isLoading: true
  })

  try {
    let response
    if (files.length > 0) {
      // 有文件上传时，调用 submit 接口触发 workflow
      // 后端会保存用户消息和 AI 响应，前端只更新 loading 消息
      response = await submitDrugAgentTask({
        query: userContent,
        sessionId: sessionId,
        userId: 'user',
        submittedBy: 'user',
        files: files
      })
    } else {
      // 无文件时，调用普通聊天接口
      response = await chatApi.sendMessage(sessionId, {
        role: 'user',
        content: userContent,
        metadata: null
      })
    }

    // Remove loading indicator and add actual response
    // 对于有文件的情况，后端会创建新 session 并返回 sessionId
    let actualSessionId = sessionId
    if (files.length > 0 && response?.sessionId) {
      actualSessionId = response.sessionId
    }
    let session = sessionStore.sessions.find(s => s.id === actualSessionId)

    // 如果找不到 session（后端创建的新 session），从后端拉取会话数据
    if (!session && response?.sessionId) {
      try {
        const sessionRes = await chatApi.getSession(response.sessionId)
        if (sessionRes?.data) {
          const backendSessionData = sessionRes.data
          // 使用后端返回的 session 数据更新或创建本地 session
          const existingIdx = sessionStore.sessions.findIndex(s => s.id === response.sessionId)
          if (existingIdx !== -1) {
            sessionStore.sessions[existingIdx] = backendSessionData
          } else {
            sessionStore.sessions.unshift(backendSessionData)
          }
          session = backendSessionData
          sessionStore.setActiveSession(session.id)
        }
      } catch (e) {
        console.warn('Failed to fetch session from backend:', e)
      }
    }

    if (!session) {
      session = sessionStore.sessions.find(s => s.id === sessionId)
    }

    if (session) {
      const msgs = session.messages
      // Remove loading message
      const loadingIdx = msgs.findIndex(m => m.id === loadingMsgId)
      if (loadingIdx !== -1) msgs.splice(loadingIdx, 1)

      // 构建响应内容
      let responseContent = '处理完成'
      let resultData = null
      if (files.length > 0) {
        // 有文件时，submitDrugAgentTask 返回 DrugAgentResp 对象本身（经 request 拦截器处理）
        resultData = response
        if (response?.summary) {
          responseContent = response.summary
        } else if (response?.answer) {
          responseContent = response.answer
        } else if (response?.markdownContent) {
          responseContent = response.markdownContent
        }

        // 保存任务上下文到 session（caseId/docId/场景信息）
        if (response?.caseId || response?.docId || response?.scene) {
          const taskContext = {
            caseId: response.caseId,
            docId: response.docId,
            scene: response.scene,
            createdAt: new Date().toISOString()
          }
          sessionStore.updateSession(actualSessionId, {
            taskContext,
            title: response.caseName || response.title || session.title
          })
        }
      } else if (response?.data) {
        // 无文件时，chatApi.sendMessage 返回 { data: { aiResponse: string } }
        const data = response.data
        resultData = data
        if (data.aiResponse) {
          responseContent = data.aiResponse
        }
      }

      // 如果后端 session 有消息列表，使用后端的消息；否则添加响应到当前 session
      if (session.messages && session.messages.length > 0) {
        // 已有后端消息，不需要额外添加
      } else {
        msgs.push({
          id: Date.now().toString(),
          role: 'agent',
          content: responseContent,
          timestamp: new Date().toISOString(),
          result: resultData
        })
      }
      session.updatedAt = new Date().toISOString()
    }
  } catch (error) {
    console.error('API call failed:', error)
    // Replace loading message with error
    const session = sessionStore.sessions.find(s => s.id === sessionId)
    if (session) {
      const msgs = session.messages
      const loadingIdx = msgs.findIndex(m => m.id === loadingMsgId)
      if (loadingIdx !== -1) {
        msgs[loadingIdx] = {
          id: loadingMsgId,
          role: 'agent',
          content: '抱歉，服务器繁忙，请稍后重试。',
          timestamp: new Date().toISOString(),
          isLoading: false
        }
      } else {
        msgs.push({
          id: Date.now().toString(),
          role: 'agent',
          content: '抱歉，服务器繁忙，请稍后重试。',
          timestamp: new Date().toISOString(),
          isLoading: false
        })
      }
    }
  }
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

// 文件上传校验配置
const FILE_LIMIT = {
  maxCount: 10,
  maxSize: 50 * 1024 * 1024, // 50MB
  allowedTypes: ['.pdf', '.doc', '.docx', '.md']
}

// 文件校验
const validateFile = (file) => {
  const errors = []

  // 文件格式校验
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!FILE_LIMIT.allowedTypes.includes(ext)) {
    errors.push(`文件 ${file.name} 格式不支持，仅支持：${FILE_LIMIT.allowedTypes.join('、')}`)
  }

  // 文件大小校验
  if (file.size > FILE_LIMIT.maxSize) {
    errors.push(`文件 ${file.name} 超过大小限制（最大 ${FILE_LIMIT.maxSize / 1024 / 1024}MB）`)
  }

  return errors
}

// 重复文件校验
const checkDuplicateFile = (file) => {
  return selectedFiles.value.some(f =>
    f.name === file.name && f.size === file.size
  )
}

// beforeUpload 钩子
const beforeUpload = (event) => {
  const files = Array.from(event.target.files || [])

  // 文件数量校验
  if (selectedFiles.value.length + files.length > FILE_LIMIT.maxCount) {
    ElMessage.warning(`最多只能上传 ${FILE_LIMIT.maxCount} 个文件`)
    event.target.value = ''
    return false
  }

  // 逐个校验文件
  for (const file of files) {
    // 重复文件校验
    if (checkDuplicateFile(file)) {
      ElMessage.warning(`文件 ${file.name} 已存在`)
      continue
    }

    // 格式和大小校验
    const errors = validateFile(file)
    if (errors.length > 0) {
      errors.forEach(err => ElMessage.error(err))
      continue
    }

    selectedFiles.value.push(file)
  }

  event.target.value = ''
  return false // 阻止默认上传
}

const handleFileChange = (event) => {
  beforeUpload(event)
}

const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}

const stepLabelMap = {
  scene_route: '场景路由',
  generic_review: '通用审查',
  structured_load: '结构化加载',
  rule_hit: '规则命中分析',
  false_positive_exemption: '误报豁免',
  risk_fusion: '风险融合',
  evidence_assembly: '证据组装',
  report_generation: '报告生成'
}

const evidenceTitleMap = {
  fusion_score: '融合评分',
  rule_scan_result: '规则扫描结果',
  quote_gradient: '报价梯度异常',
  contact_nearby: '联系人近邻',
  contact_proximity: '联系人近邻',
  team_overlap: '核心团队重叠',
  core_team_overlap: '核心团队重叠',
  proposal_copy: '方案内容雷同',
  proposal_plagiarism: '方案内容雷同',
  template_homology: '模板同源',
  rare_typo_cooccurrence: '罕见错误共现',
  error_replication: '错误复现',
  service_commitment: '服务承诺雷同',
  implementation_method: '实施方法雷同',
  case_data_plagiarism: '案例数据复用',
  risk_identification: '风险识别异常'
}

const translateStep = (step) => {
  return stepLabelMap[step] || step || '未命名步骤'
}

const translateEvidenceTitle = (title) => {
  return evidenceTitleMap[title] || title || '未命名证据'
}

const formatEvidence = (evidence) => {
  if (!evidence) return '暂无证据'
  if (typeof evidence === 'string') return evidence
  const title = translateEvidenceTitle(evidence.title)
  return title && evidence.content ? `${title}：${evidence.content}` : (title || evidence.content || '暂无证据')
}

const normalizeSummary = (summary) => {
  if (summary === 'No retained high-risk hits after rule scan.') {
    return '规则扫描后未保留高风险命中。'
  }
  return summary || '暂无摘要'
}

const isHighRisk = (riskLevel) => (riskLevel || '').toUpperCase() === 'HIGH'
const isMediumRisk = (riskLevel) => (riskLevel || '').toUpperCase() === 'MEDIUM'
const isLowRisk = (riskLevel) => ['LOW', 'NONE'].includes((riskLevel || '').toUpperCase())

const translateRiskLabel = (riskLevel) => {
  switch ((riskLevel || '').toUpperCase()) {
    case 'HIGH':
      return '高风险'
    case 'MEDIUM':
      return '中风险'
    case 'LOW':
    case 'NONE':
      return '低风险'
    default:
      return riskLevel || '未知'
  }
}

const translateExplanationKey = (key) => {
  const map = {
    overall: '综合说明',
    evidence: '证据说明',
    exemption: '豁免说明',
    focus: '关注重点'
  }
  return map[key] || key
}

const isMarkdownContent = (content) => {
  if (!content) return false
  return content.includes('# ') || content.includes('## ') || content.includes('| :--- |')
}

const renderMarkdown = (content) => {
  if (!content) return ''
  return marked.parse(content, { breaks: true })
}

const getSceneLabel = (scene) => {
  if (scene === 'TENDER' || scene === 'TENDER_REVIEW') return '标书审查'
  if (scene === 'CONTRACT' || scene === 'CONTRACT_PRECHECK') return '合同预审'
  if (scene === 'RISK_ALERT') return '合规预警'
  return '智能审查'
}

const openReportDialog = (result) => {
  currentReportContent.value = getFullReportMarkdown(result) || '暂无报告内容'
  reportDialogVisible.value = true
}
</script>

<style scoped>
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fadeIn {
  animation: fadeIn 0.3s ease-out;
}

.process-pulse {
  animation: processPulse 1.2s ease-in-out infinite;
}

@keyframes processPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(79, 70, 229, 0.18);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(79, 70, 229, 0);
  }
}

.report-markdown :deep(h1) {
  font-size: 1.6rem;
  font-weight: 800;
  color: #1e293b;
  margin: 0 0 1rem;
}

.report-markdown :deep(h2) {
  font-size: 1.2rem;
  font-weight: 700;
  color: #334155;
  margin: 1.5rem 0 0.75rem;
}

.report-markdown :deep(h3),
.report-markdown :deep(h4) {
  font-size: 1rem;
  font-weight: 700;
  color: #334155;
  margin: 1rem 0 0.5rem;
}

.report-markdown :deep(p),
.report-markdown :deep(li),
.report-markdown :deep(blockquote) {
  color: #475569;
  line-height: 1.8;
}

.report-markdown :deep(ul),
.report-markdown :deep(ol) {
  padding-left: 1.25rem;
  margin: 0.5rem 0;
}

.report-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
  overflow: hidden;
  border-radius: 0.75rem;
  font-size: 0.9rem;
}

.report-markdown :deep(th),
.report-markdown :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 0.75rem;
  text-align: left;
  vertical-align: top;
}

.report-markdown :deep(th) {
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

.report-markdown :deep(code) {
  background: #f1f5f9;
  color: #334155;
  padding: 0.1rem 0.35rem;
  border-radius: 0.35rem;
}

.report-markdown :deep(hr) {
  border: none;
  border-top: 1px solid #e2e8f0;
  margin: 1.25rem 0;
}

/* 报告弹窗样式 */
.report-content-wrapper {
  max-height: 60vh;
  overflow-y: auto;
  padding: 8px;
}

.report-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #e5e7eb;
  padding: 16px 20px;
  margin-right: 0;
}

.report-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  font-size: 16px;
  color: #1f2937;
}

.report-dialog :deep(.el-dialog__body) {
  padding: 20px;
}
</style>
