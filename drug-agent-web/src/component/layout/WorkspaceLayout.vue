<template>
  <div class="workspace-layout">
    <aside class="workspace-sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header" v-if="!isCollapsed">
        <div class="logo-area">
          <div class="logo-box">
            <el-icon><MagicStick /></el-icon>
          </div>
          <span class="logo-text">横渡智能监管</span>
        </div>
        <button class="new-chat-btn" title="新建任务/会话" @click="handleNewChat">
          <el-icon><EditPen /></el-icon>
        </button>
      </div>
      <div class="sidebar-header collapsed" v-else>
        <div class="logo-box" @click="handleNewChat" style="cursor: pointer;">
          <el-icon><MagicStick /></el-icon>
        </div>
      </div>

      <div class="sidebar-scroll">
        <section class="sidebar-group">
          <sidebar-item
            v-for="item in primaryItems"
            :key="item.key"
            :icon="item.icon"
            :label="item.label"
            :active="activeMenu === item.key"
            :collapsed="isCollapsed"
            @click="handleMenuClick(item)"
          />
        </section>

        <section class="sidebar-group history-section" v-if="!isCollapsed">
          <p class="sidebar-label">历史审查会话</p>

          <!-- 今天分组 -->
          <div class="history-block" v-if="todaySessions.length">
            <span class="history-time">今天</span>
            <div class="history-items">
              <button
                v-for="session in todaySessions"
                :key="session.id"
                class="history-item"
                :class="{ active: activeHistoryId === session.id }"
                @click="handleHistoryClick(session.id, session.title)"
                :title="session.title"
              >
                {{ session.title }}
              </button>
            </div>
          </div>

          <!-- 昨天分组 -->
          <div class="history-block" v-if="yesterdaySessions.length">
            <span class="history-time">昨天</span>
            <div class="history-items">
              <button
                v-for="session in yesterdaySessions"
                :key="session.id"
                class="history-item"
                :class="{ active: activeHistoryId === session.id }"
                @click="handleHistoryClick(session.id, session.title)"
                :title="session.title"
              >
                {{ session.title }}
              </button>
            </div>
          </div>

          <!-- 过去7天分组 -->
          <div class="history-block" v-if="weekSessions.length">
            <span class="history-time">过去7天</span>
            <div class="history-items">
              <button
                v-for="session in weekSessions"
                :key="session.id"
                class="history-item"
                :class="{ active: activeHistoryId === session.id }"
                @click="handleHistoryClick(session.id, session.title)"
                :title="session.title"
              >
                {{ session.title }}
              </button>
            </div>
          </div>
        </section>
      </div>

      <div class="sidebar-footer">
        <sidebar-item
          :icon="Setting"
          label="偏好设置"
          :collapsed="isCollapsed"
          @click="showSettings"
        />
        <sidebar-item
          :icon="isCollapsed ? Expand : Fold"
          :label="isCollapsed ? '展开侧边栏' : '收起侧边栏'"
          :collapsed="isCollapsed"
          @click="isCollapsed = !isCollapsed"
        />
      </div>
    </aside>

    <main class="workspace-main" :class="{ 'sidebar-collapsed': isCollapsed }">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Menu,
  Reading,
  Setting,
  Fold,
  Expand,
  MagicStick,
  EditPen
} from '@element-plus/icons-vue'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()

// 历史会话数据
const historySessions = ref([
  { id: 'h1', title: '年度设备采购标书比对', dateGroup: '今天' },
  { id: 'h2', title: '骨科耗材供应商协议预审', dateGroup: '昨天' },
  { id: 'h3', title: '药品采购合规审查', dateGroup: '昨天' },
  { id: 'h4', title: '医疗器械采购审计', dateGroup: '过去7天' },
  { id: 'h5', title: '供应商资质复核', dateGroup: '过去7天' },
  { id: 'h6', title: '药品销售数据分析', dateGroup: '过去7天' }
])

const isCollapsed = ref(false)
const activeHistoryId = ref('h1')

// 按分组过滤历史会话
const todaySessions = computed(() => historySessions.value.filter(s => s.dateGroup === '今天'))
const yesterdaySessions = computed(() => historySessions.value.filter(s => s.dateGroup === '昨天'))
const weekSessions = computed(() => historySessions.value.filter(s => s.dateGroup === '过去7天'))

const primaryItems = [
  { key: 'tasks', label: '全局任务看板', icon: Menu, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading, path: '/agent/knowledge' }
]

const activeMenu = computed(() => {
  if (route.path === '/agent/workbench') return 'hub'
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  if (route.path.startsWith('/agent/knowledge')) return 'knowledge'
  return 'hub'
})

const handleMenuClick = (item) => {
  if (item.path) {
    router.push(item.path)
  }
}

const handleHistoryClick = (historyId, title) => {
  activeHistoryId.value = historyId
  if (route.path !== '/agent/workbench') {
    router.push({ path: '/agent/workbench', query: { history: historyId, title } })
  } else {
    // 如果已经在工作台页面，触发加载历史会话
    window.dispatchEvent(new CustomEvent('load-history', { detail: { historyId, title } }))
  }
}

const handleNewChat = () => {
  activeHistoryId.value = null
  if (route.path === '/agent/workbench') {
    // 触发页面内部重置，传递 new 参数
    window.dispatchEvent(new CustomEvent('reset-workbench', { detail: { new: true } }))
  } else {
    router.push({ path: '/agent/workbench', query: { new: '1' } })
  }
}

const showSettings = () => {
  router.push('/agent/settings')
}
</script>

<style scoped>
.workspace-layout {
  display: flex;
  min-height: 100vh;
  background: #fdfdff;
}

.workspace-sidebar {
  width: 288px;
  background: #fff;
  border-right: 1px solid #f0f2f5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 1000;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.workspace-sidebar.collapsed {
  width: 80px;
}

.sidebar-header {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
}

.sidebar-header.collapsed {
  padding: 24px 0;
  justify-content: center;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4f46e5, #3b82f6);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.2);
  flex-shrink: 0;
}

.logo-text {
  font-size: 20px;
  font-weight: 850;
  color: #1a202c;
  letter-spacing: -0.5px;
  white-space: nowrap;
}

.new-chat-btn {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid #edf2f7;
  background: #fff;
  color: #2563eb;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 18px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.new-chat-btn:hover {
  background: #f8fafc;
  border-color: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
}

.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.sidebar-group {
  margin-bottom: 32px;
}

.history-section {
  padding: 0 8px;
}

.sidebar-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 20px;
  padding-left: 4px;
}

.history-block {
  margin-bottom: 20px;
}

.history-time {
  display: block;
  font-size: 12px;
  color: #cbd5e1;
  margin-bottom: 8px;
  padding-left: 4px;
  font-weight: 600;
}

.history-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
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
  display: block;
  transition: all 0.2s;
}

.history-item:hover {
  background: #f8fafc;
  color: #2563eb;
}

.history-item.active {
  background: #eff6ff;
  color: #2563eb;
  border-color: #3b82f633;
  font-weight: 600;
}

.sidebar-footer {
  padding: 16px 12px;
  border-top: 1px solid #f8fafc;
}

.workspace-main {
  flex: 1;
  margin-left: 288px;
  min-height: 100vh;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.workspace-main.sidebar-collapsed {
  margin-left: 80px;
}

@media (max-width: 1024px) {
  .workspace-sidebar {
    position: fixed;
    z-index: 100;
    left: 0;
    transform: translateX(0);
  }

  .workspace-sidebar.collapsed {
    transform: translateX(-100%);
  }
}
</style>

