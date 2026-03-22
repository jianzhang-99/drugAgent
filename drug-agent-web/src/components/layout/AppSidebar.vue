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
          Drug-Agent
        </span>
      </div>

      <button
        v-if="!collapsed"
        class="w-8 h-8 rounded-lg hover:bg-indigo-50 hover:text-indigo-600 flex items-center justify-center transition-colors"
        title="新建会话"
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
              class="history-item"
              :class="[
                activeSessionId === session.id
                  ? 'bg-indigo-50 text-indigo-800'
                  : 'text-slate-500 hover:bg-slate-100'
              ]"
              :title="collapsed ? session.title : ''"
              @click="$emit('load-session', session.id)"
            >
              <MessageSquare class="w-4 h-4 flex-shrink-0" />
              <span v-if="!collapsed" class="text-sm truncate">{{ session.title }}</span>
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
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Sparkles,
  SquarePen,
  Settings,
  LayoutList,
  BookOpen,
  MessageSquare,
  ChevronLeft,
  ChevronRight
} from 'lucide-vue-next'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  },
  sessions: {
    type: Array,
    default: () => []
  },
  activeSessionId: {
    type: String,
    default: null
  }
})

defineEmits(['new-chat', 'load-session', 'toggle-collapse'])

const route = useRoute()
const router = useRouter()

const navItems = [
  { route: '/tasks', label: '全局任务看板', icon: LayoutList },
  { route: '/knowledge', label: '合规知识库', icon: BookOpen }
]

const historyGroups = computed(() => {
  const now = new Date()
  const today = now.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
  const yesterday = new Date(now.setDate(now.getDate() - 1)).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })

  const groups = {
    today: { label: '今天', sessions: [] },
    yesterday: { label: '昨天', sessions: [] },
    week: { label: '过去7天', sessions: [] }
  }

  props.sessions.forEach(session => {
    const sessionDate = new Date(session.updatedAt || Date.now())
    const sessionDateStr = sessionDate.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })

    if (sessionDateStr === today) {
      groups.today.sessions.push(session)
    } else if (sessionDateStr === yesterday) {
      groups.yesterday.sessions.push(session)
    } else {
      groups.week.sessions.push(session)
    }
  })

  return Object.values(groups).filter(g => g.sessions.length > 0)
})

const isActive = (path) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const navigate = (path) => {
  router.push(path)
}
</script>

<style scoped>
.nav-item {
  @apply flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all duration-200 border;
}
</style>
