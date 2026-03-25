<template>
  <aside
    :class="[
      'bg-slate-50 border-r border-slate-200 transition-all duration-300 flex flex-col z-20',
      isCollapsed ? 'w-20' : 'w-72'
    ]"
  >
    <!-- Header: Logo & New Chat -->
    <div class="p-4 flex items-center justify-between">
      <div
        class="flex items-center gap-3 cursor-pointer"
        @click="$emit('navigate', 'WORKSPACE')"
      >
        <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center flex-shrink-0 shadow-sm">
          <Sparkles class="text-white" size="18" />
        </div>
        <span v-if="!isCollapsed" class="font-bold text-lg tracking-tight text-slate-800 truncate">Drug-Agent</span>
      </div>
      <button
        v-if="!isCollapsed"
        @click="$emit('new-chat')"
        class="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
        title="新建任务/会话"
      >
        <SquarePen size="20" />
      </button>
    </div>

    <!-- Nav Items -->
    <div class="px-3 pb-4">
      <button
        v-for="item in navItems"
        :key="item.id"
        @click="$emit('navigate', item.id)"
        :class="[
          'w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all mb-1',
          activeView === item.id
            ? 'bg-white shadow-sm border border-slate-200 text-slate-800'
            : 'text-slate-600 hover:bg-slate-200/50 border border-transparent'
        ]"
      >
        <component :is="item.icon" size="18" :class="item.color" />
        <span v-if="!isCollapsed" class="font-medium text-sm whitespace-nowrap">{{ item.label }}</span>
      </button>
    </div>

    <!-- Session History -->
    <div class="flex-1 overflow-y-auto px-3">
      <p v-if="!isCollapsed" class="text-[10px] font-bold text-slate-400 uppercase tracking-widest px-3 mb-2 pt-2">
        历史审查会话
      </p>

      <!-- Date Groups -->
      <div v-for="group in sessionGroups" :key="group.label">
        <template v-if="!isCollapsed || group.sessions.length > 0">
          <div v-if="!isCollapsed && group.sessions.length > 0" class="text-[10px] font-semibold text-slate-400 px-3 mb-1">
            {{ group.label }}
          </div>
          <div class="space-y-0.5">
            <button
              v-for="session in group.sessions"
              :key="session.id"
              @click="$emit('select-session', session)"
              :class="[
                'w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-left',
                activeView === 'WORKSPACE' && activeSessionId === session.id
                  ? 'bg-blue-100 text-blue-800 font-medium'
                  : 'text-slate-600 hover:bg-slate-200/50'
              ]"
              :title="session.title"
            >
              <MessageSquare v-if="isCollapsed" size="16" class="mx-auto" />
              <span v-else class="text-sm truncate pr-2">{{ session.title }}</span>
            </button>
          </div>
        </template>
      </div>

      <!-- Empty state when collapsed -->
      <div v-if="isCollapsed" class="text-center py-4">
        <MessageSquare size="16" class="mx-auto text-slate-400" />
      </div>
    </div>

    <!-- Footer: Settings & Collapse -->
    <div class="p-3 border-t border-slate-200 flex flex-col gap-1">
      <button
        v-if="!isCollapsed"
        @click="$emit('navigate', 'SETTINGS')"
        :class="[
          'w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-sm',
          activeView === 'SETTINGS'
            ? 'bg-white shadow-sm border border-slate-200 text-blue-600 font-semibold'
            : 'text-slate-500 hover:bg-slate-200/50 border border-transparent'
        ]"
      >
        <Settings size="18" />
        偏好与系统配置
      </button>
      <button
        @click="$emit('toggle-collapse')"
        :class="[
          'flex items-center gap-3 px-3 py-2 rounded-lg text-slate-400 hover:bg-slate-200/50 transition-all',
          isCollapsed && 'justify-center'
        ]"
      >
        <Menu v-if="isCollapsed" size="18" />
        <template v-else>
          <Menu size="18" />
          <span class="text-sm">收起侧边栏</span>
        </template>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Sparkles,
  SquarePen,
  Settings,
  Menu,
  MessageSquare,
  LayoutList,
  BookOpen
} from 'lucide-vue-next'
import type { SessionSummary } from '@/store/agent/types'

const props = defineProps<{
  isCollapsed: boolean
  activeView: string
  activeSessionId?: string
  sessions: SessionSummary[]
}>()

defineEmits<{
  'navigate': [view: string]
  'new-chat': []
  'select-session': [session: SessionSummary]
  'toggle-collapse': []
}>()

const navItems = [
  { id: 'TASKS', label: '全局任务看板', icon: LayoutList, color: 'text-indigo-600' },
  { id: 'KNOWLEDGE', label: '合规知识库', icon: BookOpen, color: 'text-emerald-600' }
]

const sessionGroups = computed(() => {
  const groups: Record<string, SessionSummary[]> = {
    '今天': [],
    '昨天': [],
    '过去7天': [],
    '更早': []
  }

  props.sessions.forEach(session => {
    const dateGroup = session.dateGroup || '更早'
    if (groups[dateGroup]) {
      groups[dateGroup].push(session)
    } else {
      groups['更早'].push(session)
    }
  })

  return Object.entries(groups)
    .filter(([_, sessions]) => sessions.length > 0)
    .map(([label, sessions]) => ({ label, sessions }))
})
</script>
