<template>
  <div class="flex h-screen bg-slate-50 text-slate-900 font-sans overflow-hidden">
    <!-- Main Content Area -->
    <main
      :class="[
        'flex-1 flex flex-col relative bg-white z-30 transition-all duration-300',
        agentStore.isReportDrawerOpen ? 'w-2/3' : 'w-full'
      ]"
    >
      <!-- Chat Area -->
      <div class="flex-1 overflow-y-auto relative bg-[#FDFDFD]">
        <ChatTimeline
          ref="chatTimelineRef"
          :messages="agentStore.messages"
          :has-active-session="agentStore.hasActiveSession"
          :selected-report="agentStore.selectedReport"
          :error="agentStore.error"
          @quick-action="handleQuickAction"
          @select-report="handleSelectReport"
          @quick-reply="handleQuickReply"
          @retry="handleRetry"
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
        @close="handleCloseReportDrawer"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Trash2 } from 'lucide-vue-next'
import ChatTimeline from './ChatTimeline.vue'
import ComposerBar from './ComposerBar.vue'
import ReportDrawer from './ReportDrawer.vue'

import { useAgentStore } from '@/module/agent/store/agentStore'
import type { ReportSummary } from '@/module/agent/types/chatMessage'

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

const handleClearAll = async () => {
  if (confirm('确定要清空所有会话吗？此操作不可恢复。')) {
    await agentStore.clearAllSessions()
  }
}

const handleSelectReport = (result: ReportSummary) => {
  // 保存滚动位置后再打开抽屉
  chatTimelineRef.value?.saveScrollPosition()
  agentStore.openReportDrawer(result)
}

const handleCloseReportDrawer = () => {
  // 恢复滚动位置
  chatTimelineRef.value?.restoreScrollPosition()
  agentStore.closeReportDrawer()
}

const handleQuickAction = async (prompt: string) => {
  await handleSubmit(prompt)
}

const handleQuickReply = async (reply: string) => {
  await handleSubmit(reply)
}

const handleSubmit = async (text: string, attachments?: string[]) => {
  if (!text.trim() || agentStore.isLoading) return

  agentStore.closeReportDrawer()

  // 如果没有活动会话，创建一个新的
  if (!agentStore.activeSessionId) {
    await agentStore.createSession(text.substring(0, 15) + '...', 'TENDER')
  }

  try {
    await agentStore.sendMessage(text, attachments)
    // 滚动到底部
    chatTimelineRef.value?.scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
  }
}

const handleRetry = async () => {
  // 重试最后一次发送的消息
  const lastUserMessage = agentStore.messages.filter(m => m.type === 'user_text').pop()
  if (lastUserMessage) {
    try {
      await agentStore.sendMessage(lastUserMessage.content)
      chatTimelineRef.value?.scrollToBottom()
    } catch (error) {
      console.error('Retry failed:', error)
    }
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
      chatTimelineRef.value?.scrollToTop()
      await agentStore.fetchSession(sessionId)
      activeView.value = 'WORKSPACE'
    }
  }
)

watch(
  () => route.query.history,
  async (historyId) => {
    if (typeof historyId === 'string' && historyId) {
      chatTimelineRef.value?.scrollToTop()
      await agentStore.fetchSession(historyId)
      activeView.value = 'WORKSPACE'
    }
  }
)
</script>
