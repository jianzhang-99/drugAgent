<template>
  <div class="agent-workbench h-full flex bg-slate-50">
    <!-- 左侧区域：导航和历史会话 -->
    <AgentSidebar
      :sessions="groupedSessions"
      :active-session-id="activeSessionId"
      :is-collapsed="isSidebarCollapsed"
      @select-session="handleSelectSession"
      @new-session="handleNewSession"
      @toggle-collapse="isSidebarCollapsed = !isSidebarCollapsed"
    />

    <!-- 中间主工作区 -->
    <div class="flex-1 flex flex-col relative bg-white shadow-[-8px_0_24px_-12px_rgba(0,0,0,0.05)] z-30">
      <!-- 顶部栏 -->
      <AgentTopBar
        :active-session-title="activeSession?.title"
        :active-view="activeView"
        :running-task-count="runningTaskCount"
        :is-task-center-open="isTaskCenterOpen"
        @toggle-task-center="toggleTaskCenter"
      />

      <!-- 任务中心 Popover -->
      <TaskCenterPopover
        v-if="isTaskCenterOpen"
        :tasks="tasks"
        @close="closeTaskCenter"
        @task-click="handleTaskClick"
      />

      <!-- 聊天时间线区域 -->
      <div
        class="flex-1 overflow-y-auto relative bg-[#FDFDFD]"
        :class="hasActiveSession ? '' : 'flex items-center justify-center'"
      >
        <!-- 空状态：快捷入口 -->
        <div v-if="!hasActiveSession" class="w-full max-w-3xl px-6 py-10">
          <div class="text-center mb-16">
            <div class="relative inline-block mb-8">
              <div class="w-20 h-20 rounded-2xl bg-indigo-600 flex items-center justify-center shadow-xl shadow-indigo-500/20">
                <Sparkles class="w-10 h-10 text-white" />
              </div>
            </div>
            <h1 class="text-4xl font-bold text-slate-800 mb-4 tracking-tight">
              有什么我可以帮您分析的？
            </h1>
            <p class="text-lg text-slate-500 max-w-xl mx-auto">
              直接描述您的监管需求，Agent 将自动分发到对应的工作流
            </p>
          </div>

          <!-- 快捷操作卡片 -->
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

        <!-- 聊天消息时间线 -->
        <div v-else class="max-w-3xl mx-auto px-6 py-8 pb-36">
          <div class="space-y-6">
            <MessageRenderer
              v-for="(message, index) in messages"
              :key="index"
              :message="message"
              :is-selected="selectedReport?.traceId === message.result?.traceId"
              @card-click="handleCardClick"
              @view-report="handleViewReport"
            />
          </div>
        </div>

        <!-- 滚动到底部锚点 -->
        <div ref="messagesEndRef" />
      </div>

      <!-- 底部输入区 -->
      <div
        :class="hasActiveSession ? 'w-full max-w-3xl mx-auto px-6 pb-6' : 'w-full max-w-2xl mx-auto px-6 pb-4'"
        class="relative z-10"
      >
        <ComposerBar
          v-model="inputText"
          :placeholder="hasActiveSession ? '向 Agent 追加要求或提供更多材料...' : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'"
          :is-sending="isSending"
          @send="handleSend"
          @upload="handleFileUpload"
        />
      </div>
    </div>

    <!-- 右侧报告抽屉 -->
    <ReportDrawer
      v-if="isReportDrawerOpen"
      :report="selectedReport"
      @close="closeReportDrawer"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { Sparkles, FileText, ShieldCheck, AlertTriangle } from 'lucide-vue-next'
import { useAgentStore } from '@/module/agent/store/agentStore'

// 组件
import AgentSidebar from './AgentSidebar.vue'
import AgentTopBar from './AgentTopBar.vue'
import TaskCenterPopover from './TaskCenterPopover.vue'
import ComposerBar from './ComposerBar.vue'
import MessageRenderer from './MessageRenderer.vue'
import ReportDrawer from './ReportDrawer.vue'

// Store
const agentStore = useAgentStore()

// 状态
const activeView = ref('WORKSPACE')
const isSidebarCollapsed = ref(false)
const inputText = ref('')
const messagesEndRef = ref<HTMLElement | null>(null)

// 快捷操作配置
const quickActions = [
  {
    icon: FileText,
    label: '标书审查',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    bgColor: 'bg-indigo-50',
    textColor: 'text-indigo-600'
  },
  {
    icon: ShieldCheck,
    label: '合同预审',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    bgColor: 'bg-emerald-50',
    textColor: 'text-emerald-600'
  },
  {
    icon: AlertTriangle,
    label: '合规预警',
    prompt: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    bgColor: 'bg-amber-50',
    textColor: 'text-amber-600'
  }
]

// ==================== Computed ====================

// Store 状态映射
const groupedSessions = computed(() => agentStore.groupedSessions)
const activeSession = computed(() => agentStore.activeSession)
const activeSessionId = computed(() => agentStore.activeSessionId)
const messages = computed(() => agentStore.messages)
const hasActiveSession = computed(() => agentStore.hasActiveSession)
const tasks = computed(() => agentStore.tasks)
const runningTaskCount = computed(() => agentStore.runningTaskCount)
const isTaskCenterOpen = computed(() => agentStore.isTaskCenterOpen)
const isReportDrawerOpen = computed(() => agentStore.isReportDrawerOpen)
const selectedReport = computed(() => agentStore.selectedReport)
const isSending = computed(() => agentStore.isLoading)

// ==================== 生命周期 ====================

onMounted(async () => {
  // 加载会话列表
  await agentStore.fetchSessions()
  // 加载任务列表
  agentStore.fetchTasks()
})

// 监听消息变化，自动滚动到底部
watch(messages, () => {
  nextTick(() => {
    scrollToBottom()
  })
}, { deep: true })

// ==================== 方法 ====================

/**
 * 滚动到底部
 */
function scrollToBottom() {
  if (messagesEndRef.value) {
    messagesEndRef.value.scrollIntoView({ behavior: 'smooth' })
  }
}

/**
 * 选择会话
 */
async function handleSelectSession(sessionId: string) {
  await agentStore.fetchSession(sessionId)
}

/**
 * 新建会话
 */
async function handleNewSession() {
  await agentStore.createSession('新会话', 'general')
  // 新建会话后自动聚焦输入框
  nextTick(() => {
    const textarea = document.querySelector('.composer-bar textarea') as HTMLTextAreaElement
    textarea?.focus()
  })
}

/**
 * 处理快捷操作
 */
async function handleQuickAction(action: { prompt: string }) {
  // 确保有活跃会话
  if (!activeSession.value) {
    await handleNewSession()
  }
  inputText.value = action.prompt
  await handleSend()
}

/**
 * 发送消息 - 直接使用 store 的 sendMessage action
 */
async function handleSend() {
  if (!inputText.value.trim() || isSending.value) return

  const content = inputText.value.trim()
  inputText.value = ''

  try {
    // 确保有活跃会话
    if (!activeSession.value) {
      await handleNewSession()
    }

    // 直接使用 store 的 sendMessage，它会处理完整的流程：
    // 1. 添加用户消息
    // 2. 添加进度消息
    // 3. 调用 mock 服务获取响应
    // 4. 替换进度消息为最终消息
    await agentStore.sendMessage(content)

    // 如果最终消息是结果卡片，自动打开报告抽屉
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage?.type === 'assistant_result_card') {
      agentStore.openReportDrawer(lastMessage.result)
    }
  } catch (error) {
    console.error('Failed to send message:', error)
  }
}

/**
 * 处理文件上传
 */
function handleFileUpload(files: FileList) {
  // TODO: 实现文件上传逻辑
  console.log('Files uploaded:', files)
}

/**
 * 处理卡片点击
 */
function handleCardClick(message: any) {
  if (message.type === 'assistant_result_card') {
    agentStore.toggleReportDrawer(message.result)
  }
}

/**
 * 处理查看报告
 */
function handleViewReport(message: any) {
  if (message.type === 'assistant_result_card') {
    agentStore.openReportDrawer(message.result)
  }
}

/**
 * 处理任务点击
 */
function handleTaskClick(task: any) {
  // TODO: 实现任务点击定位逻辑
  agentStore.closeTaskCenter()
}

/**
 * 切换任务中心
 */
function toggleTaskCenter() {
  agentStore.toggleTaskCenter()
}

/**
 * 关闭任务中心
 */
function closeTaskCenter() {
  agentStore.closeTaskCenter()
}

/**
 * 关闭报告抽屉
 */
function closeReportDrawer() {
  agentStore.closeReportDrawer()
}
</script>

<style scoped>
/* 空状态样式 */
:deep(.empty-state) {
  @apply flex flex-col items-center justify-center min-h-[60vh];
}

/* 消息区域样式 */
:deep(.message-area) {
  @apply space-y-6;
}
</style>
