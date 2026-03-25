<template>
  <aside
    class="h-full bg-slate-50/80 backdrop-blur-sm border-r border-slate-200/60 flex flex-col transition-all duration-300 ease-in-out"
    :class="collapsed ? 'w-20' : 'w-[280px]'"
  >
    <!-- Header -->
    <div class="h-16 px-4 flex items-center justify-between border-b border-slate-200/50">
      <div
        class="flex items-center gap-3 cursor-pointer group"
        @click="$emit('new-chat')"
      >
        <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-lg shadow-indigo-500/20 group-hover:shadow-indigo-500/30 transition-shadow">
          <Sparkles class="w-5 h-5 text-white" />
        </div>
        <span
          v-if="!collapsed"
          class="font-bold text-slate-800 tracking-tight"
        >
          横渡智能系统
        </span>
      </div>

      <button
        v-if="!collapsed"
        class="w-8 h-8 rounded-lg hover:bg-indigo-50 hover:text-indigo-600 flex items-center justify-center transition-colors"
        title="新建会话"
        data-testid="new-chat-button"
        @click="$emit('new-chat')"
      >
        <SquarePen class="w-4 h-4" />
      </button>
    </div>

    <!-- Navigation -->
    <nav class="px-3 py-4 space-y-1">
      <div
        v-for="item in navItems"
        :key="item.route"
        class="nav-item"
        :class="[
          isActive(item.route)
            ? 'bg-white text-indigo-700 shadow-sm border-slate-200'
            : 'text-slate-600 hover:bg-slate-100/80 border-transparent'
        ]"
        @click="navigate(item.route)"
      >
        <component :is="item.icon" class="w-5 h-5 flex-shrink-0" />
        <span v-if="!collapsed" class="font-medium text-sm">{{ item.label }}</span>
      </div>
    </nav>

    <!-- History Sessions -->
    <div class="flex-1 overflow-y-auto px-3 py-4 border-t border-slate-200/50">
      <div v-if="!collapsed" class="px-3 mb-3">
        <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">历史审查会话</span>
      </div>

      <div class="space-y-4">
        <div v-for="group in historyGroups" :key="group.label">
          <div v-if="!collapsed" class="px-3 mb-2">
            <span class="text-[10px] font-semibold text-slate-400 uppercase tracking-wide">{{ group.label }}</span>
          </div>
          <div class="space-y-0.5">
            <div
              v-for="session in group.sessions"
              :key="session.id"
              class="history-item group flex items-center gap-2"
              :class="[
                activeSessionId === session.id
                  ? 'bg-indigo-50 text-indigo-800'
                  : 'text-slate-500 hover:bg-slate-100'
              ]"
              :title="collapsed ? session.title : ''"
              :data-testid="`history-session-${session.id}`"
              @click="$emit('load-session', session.id)"
            >
              <MessageSquare class="w-4 h-4 flex-shrink-0" />
              <span v-if="!collapsed" class="text-sm truncate flex-1">{{ session.title }}</span>
              <button
                v-if="!collapsed"
                class="opacity-0 group-hover:opacity-100 p-1 hover:text-red-500 transition-opacity"
                title="删除会话"
                @click.stop="handleDeleteSession(session.id)"
              >
                <Trash2 class="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="px-3 py-4 border-t border-slate-200/50 space-y-1">
      <div
        class="nav-item"
        :class="[
          isActive('/settings')
            ? 'bg-white text-indigo-700 shadow-sm border-slate-200'
            : 'text-slate-600 hover:bg-slate-100/80 border-transparent'
        ]"
        @click="navigate('/settings')"
      >
        <Settings class="w-5 h-5 flex-shrink-0" />
        <span v-if="!collapsed" class="text-sm font-medium">偏好与系统配置</span>
      </div>

      <button
        class="w-full nav-item border-transparent hover:bg-slate-100/80"
        @click="$emit('toggle-collapse')"
      >
        <component :is="collapsed ? ChevronRight : ChevronLeft" class="w-5 h-5 flex-shrink-0" />
        <span v-if="!collapsed" class="text-sm font-medium">收起侧边栏</span>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Sparkles,
  SquarePen,
  Settings,
  LayoutList,
  BookOpen,
  MessageSquare,
  ChevronLeft,
  ChevronRight,
  Trash2
} from 'lucide-vue-next'
import { useSessionStore } from '@/module/agent/store/session'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

defineEmits(['new-chat', 'load-session', 'toggle-collapse'])

const route = useRoute()
const router = useRouter()
const sessionStore = useSessionStore()

const sessions = computed(() => sessionStore.sessions)
const activeSessionId = computed(() => sessionStore.activeSessionId)

onMounted(() => {
  if (!sessionStore.sessions.length) {
    sessionStore.fetchSessions()
  }
})

const navItems = [
  { route: '/tasks', label: '任务调度看板', icon: LayoutList },
  { route: '/knowledge', label: '合规知识库', icon: BookOpen }
]

// Helper to compute date group from updatedAt timestamp
const getDateGroup = (updatedAt) => {
  if (!updatedAt) return '过去 7 天'
  const date = new Date(updatedAt)
  if (isNaN(date.getTime())) return '过去 7 天'

  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const sessionDate = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const diffDays = Math.floor((today - sessionDate) / 86400000)

  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  return '过去 7 天'
}

const historyGroups = computed(() => {
  // 原型中的分组顺序
  const groupOrder = ['今天', '昨天', '过去 7 天']

  // 按 dateGroup 分组，保持原型中的顺序
  const groupMap = new Map()
  groupOrder.forEach(label => groupMap.set(label, { label, sessions: [] }))

  sessions.value.forEach(session => {
    // Use session.dateGroup if available, otherwise derive from updatedAt
    const group = session.dateGroup || getDateGroup(session.updatedAt)
    if (groupMap.has(group)) {
      groupMap.get(group).sessions.push(session)
    } else {
      // 未识别的分组放入"过去 7 天"
      groupMap.get('过去 7 天').sessions.push(session)
    }
  })

  // 按原型顺序返回有数据的组
  return groupOrder
    .map(label => groupMap.get(label))
    .filter(g => g.sessions.length > 0)
})

const isActive = (path) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const navigate = (path) => {
  router.push(path)
}

const handleDeleteSession = (sessionId) => {
  if (confirm('确定要删除这个会话吗？')) {
    sessionStore.deleteSession(sessionId)
  }
}
</script>

<style scoped>
.nav-item {
  @apply flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all duration-200 border;
}
.history-item {
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  color: #4a5568;
  padding: 8px 12px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: all 0.2s;
}
</style>
