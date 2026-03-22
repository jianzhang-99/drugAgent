<template>
  <div class="flex h-screen bg-slate-100 overflow-hidden">
    <!-- Sidebar -->
    <AppSidebar
      :collapsed="sidebarCollapsed"
      :sessions="sessions"
      :active-session-id="activeSessionId"
      @new-chat="handleNewChat"
      @load-session="handleLoadSession"
      @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- Main Content Area -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Header -->
      <AppHeader
        :active-task-count="activeTaskCount"
        @toggle-task-pane="taskPaneOpen = !taskPaneOpen"
      />

      <!-- Page Content -->
      <main class="flex-1 overflow-hidden bg-white">
        <RouterView />
      </main>
    </div>

    <!-- Task Pane -->
    <TaskPane
      :is-open="taskPaneOpen"
      :tasks="tasks"
      @close="taskPaneOpen = false"
      @select-task="handleSelectTask"
    />

    <!-- Click Overlay to close TaskPane -->
    <div
      v-if="taskPaneOpen"
      class="fixed inset-0 z-40"
      @click="taskPaneOpen = false"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { RouterView } from 'vue-router'
import AppSidebar from './components/layout/AppSidebar.vue'
import AppHeader from './components/layout/AppHeader.vue'
import TaskPane from './components/layout/TaskPane.vue'

// Sidebar State
const sidebarCollapsed = ref(false)

// Task Pane State
const taskPaneOpen = ref(false)

// Sessions & Tasks (global state)
const sessions = ref([
  {
    id: 'sess_001',
    title: '年度设备采购标书比对',
    updatedAt: new Date().toISOString()
  },
  {
    id: 'sess_002',
    title: '骨科耗材供应商协议预审',
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  },
  {
    id: 'sess_003',
    title: '药品集中采购合规检查',
    updatedAt: new Date(Date.now() - 86400000 * 3).toISOString()
  }
])

const activeSessionId = ref(null)

const tasks = ref([
  {
    id: 'T-001',
    name: '年度设备采购标书分析',
    progress: 100,
    status: 'completed'
  },
  {
    id: 'T-002',
    name: '合同条款风险提取',
    progress: 65,
    status: 'running'
  }
])

const activeTaskCount = computed(() => {
  return tasks.value.filter(t => t.status === 'running').length
})

// Actions
const handleNewChat = () => {
  activeSessionId.value = null
}

const handleLoadSession = (sessionId) => {
  activeSessionId.value = sessionId
}

const handleSelectTask = (task) => {
  console.log('Selected task:', task)
  taskPaneOpen.value = false
}
</script>
