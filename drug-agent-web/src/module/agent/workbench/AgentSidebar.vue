<template>
  <aside
    class="bg-slate-50 border-r border-slate-200 transition-all duration-300 flex flex-col z-20"
    :class="isCollapsed ? 'w-20' : 'w-72'"
  >
    <!-- 品牌区 -->
    <div class="p-4 flex items-center justify-between">
      <div
        class="flex items-center gap-3 cursor-pointer"
        @click="$emit('new-session')"
      >
        <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center flex-shrink-0 shadow-sm">
          <Sparkles class="text-white" size={18} />
        </div>
        <span v-if="!isCollapsed" class="font-bold text-lg tracking-tight text-slate-800 truncate">
          Drug-Agent
        </span>
      </div>

      <button
        v-if="!isCollapsed"
        class="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
        title="新建会话"
        @click="$emit('new-session')"
      >
        <SquarePen size={20} />
      </button>
    </div>

    <!-- 一级导航 -->
    <div class="px-3 pb-4">
      <button
        v-for="item in navItems"
        :key="item.id"
        class="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all mb-1"
        :class="activeNavItem === item.id
          ? 'bg-white shadow-sm border border-slate-200 text-slate-800'
          : 'text-slate-600 hover:bg-slate-200/50 border border-transparent'
        "
      >
        <component
          :is="item.icon"
          size="18"
          :class="item.color"
        />
        <span v-if="!isCollapsed" class="font-medium text-sm whitespace-nowrap">
          {{ item.label }}
        </span>
      </button>
    </div>

    <!-- 历史会话列表 -->
    <div class="flex-1 overflow-y-auto px-3">
      <template v-if="!isCollapsed">
        <p class="text-[10px] font-bold text-slate-400 uppercase tracking-widest px-3 mb-2 pt-2">
          历史审查会话
        </p>
      </template>

      <!-- 分组会话列表 -->
      <div v-for="group in Object.keys(sessions)" :key="group">
        <template v-if="sessions[group]?.length">
          <!-- 折叠状态不显示分组标题 -->
          <div v-if="!isCollapsed" class="text-[10px] font-semibold text-slate-400 px-3 mb-1">
            {{ group }}
          </div>

          <div class="space-y-0.5">
            <button
              v-for="session in sessions[group]"
              :key="session.id"
              class="w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-left"
              :class="[
                activeSessionId === session.id
                  ? 'bg-blue-100 text-blue-800 font-medium'
                  : 'text-slate-600 hover:bg-slate-200/50'
              ]"
              :title="session.title"
              @click="$emit('select-session', session.id)"
            >
              <MessageSquare v-if="isCollapsed" size="16" class="mx-auto" />
              <span v-else class="text-sm truncate pr-2">{{ session.title }}</span>
            </button>
          </div>
        </template>
      </div>
    </div>

    <!-- 底部设置和折叠按钮 -->
    <div class="p-3 border-t border-slate-200 flex flex-col gap-1">
      <button
        v-if="!isCollapsed"
        class="w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-sm text-slate-500 hover:bg-slate-200/50 border border-transparent"
      >
        <Settings size="18" />
        <span>偏好与系统配置</span>
      </button>

      <button
        class="flex items-center gap-3 px-3 py-2 rounded-lg text-slate-400 hover:bg-slate-200/50 transition-all"
        :class="{ 'justify-center': isCollapsed }"
        @click="$emit('toggle-collapse')"
      >
        <Menu size="18" />
        <span v-if="!isCollapsed" class="text-sm">收起侧边栏</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {
  Sparkles,
  SquarePen,
  LayoutList,
  BookOpen,
  Settings,
  MessageSquare,
  Menu
} from 'lucide-vue-next'
import type { SessionSummary } from '@/module/agent/types/chatMessage'

defineProps<{
  sessions: Record<string, SessionSummary[]>
  activeSessionId: string | null
  isCollapsed: boolean
}>()

defineEmits<{
  (e: 'select-session', sessionId: string): void
  (e: 'new-session'): void
  (e: 'toggle-collapse'): void
}>()

// 导航项
const navItems = [
  { id: 'TASKS', label: '全局任务看板', icon: LayoutList, color: 'text-indigo-600' },
  { id: 'KNOWLEDGE', label: '合规知识库', icon: BookOpen, color: 'text-emerald-600' }
]

// 当前激活的导航项
const activeNavItem = 'TASKS'
</script>

<style scoped>
/* 样式已在 Tailwind 中定义 */
</style>
