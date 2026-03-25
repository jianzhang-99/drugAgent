<template>
  <div class="flex h-screen bg-slate-50 text-slate-900 font-sans overflow-hidden">
    <!-- Main Content Area -->
    <main
      :class="[
        'flex-1 flex flex-col relative bg-white z-30',
        agentStore.isReportDrawerOpen ? 'w-2/3' : 'w-full'
      ]"
    >
      <!-- Top Bar -->
      <AgentTopBar
        :active-view="activeView"
        :active-session-title="agentStore.activeSession?.title"
        :tasks="agentStore.tasks"
        :is-task-center-open="agentStore.isTaskCenterOpen"
        @toggle-task-center="agentStore.toggleTaskCenter"
        @close-task-center="agentStore.closeTaskCenter"
        @select-task="handleSelectTask"
      />

      <!-- Chat Area -->
      <div class="flex-1 overflow-y-auto relative bg-[#FDFDFD]">
        <ChatTimeline
          ref="chatTimelineRef"
          :messages="agentStore.messages"
          :has-active-session="agentStore.hasActiveSession"
          :selected-report="agentStore.selectedReport"
          @quick-action="handleQuickAction"
          @select-report="handleSelectReport"
        />
      </div>

      <!-- Composer Bar -->
      <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-white via-white to-transparent pt-12 pb-6 px-4 sm:px-8">
        <ComposerBar
          :is-loading="agentStore.isLoading"
          :has-active-session="agentStore.hasActiveSession"
          @submit="handleSubmit"
        />
        <div class="text-center text-[10px] text-slate-400 mt-3 font-medium">
          AI 生成内容仅供参考，重大决策请人工复核 (Drug-Agent Core v0.3)
        </div>
      </div>

      <!-- Report Drawer (由 store 管理) -->
      <ReportDrawer
        v-if="agentStore.isReportDrawerOpen"
        :report="agentStore.selectedReport"
        @close="agentStore.closeReportDrawer"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import AgentTopBar from './AgentTopBar.vue'
import ChatTimeline from './ChatTimeline.vue'
import ComposerBar from './ComposerBar.vue'
import ReportDrawer from './ReportDrawer.vue'

import { useAgentStore } from '@/module/agent/store/agentStore'
import type { TaskItem, ReportSummary } from '@/module/agent/types/chatMessage'

// ==================== Store ====================

const agentStore = useAgentStore()
const route = useRoute()

// ==================== 本地状态 ====================

const activeView = ref('WORKSPACE')

const chatTimelineRef = ref<InstanceType<typeof ChatTimeline>>()

// ==================== 方法 ====================

const handleNewChat = () => {
  agentStore.setActiveSession(null)
  agentStore.closeReportDrawer()
  activeView.value = 'WORKSPACE'
}

const handleSelectSession = (session: { id: string }) => {
  // 通过 agentStore 加载会话详情
  agentStore.fetchSession(session.id)
  agentStore.closeReportDrawer()
  activeView.value = 'WORKSPACE'
}

const handleSelectTask = (task: TaskItem) => {
  agentStore.closeTaskCenter()
  // TODO: 定位到对应消息或报告
}

const handleSelectReport = (result: ReportSummary) => {
  agentStore.openReportDrawer(result)
}

const handleQuickAction = async (prompt: string) => {
  await handleSubmit(prompt)
}

const handleSubmit = async (text: string) => {
  if (!text.trim() || agentStore.isLoading) return

  agentStore.closeReportDrawer()

  // 如果没有活动会话，创建一个新的
  if (!agentStore.activeSessionId) {
    await agentStore.createSession(text.substring(0, 15) + '...', 'TENDER')
  }

  try {
    await agentStore.sendMessage(text)
    // 滚动到底部
    chatTimelineRef.value?.scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  // 加载初始数据
  agentStore.fetchSessions()
  agentStore.fetchTasks()

  if (route.query.new === '1') {
    handleNewChat()
  }
})

watch(
  () => route.query.new,
  (isNew) => {
    if (isNew === '1') {
      handleNewChat()
    }
  }
)

watch(
  () => route.query.sessionId,
  async (sessionId) => {
    if (typeof sessionId === 'string' && sessionId) {
      await agentStore.fetchSession(sessionId)
      activeView.value = 'WORKSPACE'
    }
  }
)

watch(
  () => route.query.history,
  async (historyId) => {
    if (typeof historyId === 'string' && historyId) {
      await agentStore.fetchSession(historyId)
      activeView.value = 'WORKSPACE'
    }
  }
)
</script>
