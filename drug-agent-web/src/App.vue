<template>
  <div class="flex h-screen bg-slate-100 overflow-hidden">
    <!-- Sidebar -->
    <AppSidebar
      :collapsed="sidebarCollapsed"
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
import { RouterView, useRouter, useRoute } from 'vue-router'
import AppSidebar from './components/layout/AppSidebar.vue'
import AppHeader from './components/layout/AppHeader.vue'
import TaskPane from './components/layout/TaskPane.vue'
import { useSessionStore } from './stores/session'
import { useTaskStore } from './stores/task'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()
const taskStore = useTaskStore()

// Sidebar State
const sidebarCollapsed = ref(false)

// Task Pane State
const taskPaneOpen = ref(false)

// Tasks from task store (single source of truth)
const tasks = computed(() => taskStore.tasks)

const activeTaskCount = computed(() => {
  return taskStore.inProgressTasks.length
})

// Actions
const handleNewChat = () => {
  sessionStore.setActiveSession(null)
  router.push('/workspace')
}

const handleLoadSession = (sessionId) => {
  sessionStore.setActiveSession(sessionId)
  router.push('/workspace?sessionId=' + sessionId)
}

const handleSelectTask = (task) => {
  console.log('Selected task:', task)
  taskPaneOpen.value = false
}
</script>
