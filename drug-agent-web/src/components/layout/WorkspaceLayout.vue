<template>
  <div class="workspace-layout">
    <aside class="workspace-sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-header" v-if="!isCollapsed">
        <div class="logo-area">
          <div class="logo-icon">
            <el-icon><MagicStick /></el-icon>
          </div>
          <span class="logo-text">Drug-Agent</span>
        </div>
        <button class="new-chat-btn">
          <el-icon><EditPen /></el-icon>
        </button>
      </div>
      <div class="sidebar-header collapsed" v-else>
        <el-icon><MagicStick /></el-icon>
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
          <div class="history-block">
            <span class="history-time">今天</span>
            <button class="history-item">年度设备采购标书比对</button>
          </div>
          <div class="history-block">
            <span class="history-time">昨天</span>
            <button class="history-item">骨科耗材供应商协议预审</button>
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
          label="收起侧边栏"
          :collapsed="isCollapsed"
          @click="isCollapsed = !isCollapsed"
        />
      </div>
    </aside>

    <main class="workspace-main">
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
const isCollapsed = ref(false)

const primaryItems = [
  { key: 'tasks', label: '全局任务看板', icon: Menu, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading, path: '/agent/knowledge' }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  if (route.path.startsWith('/agent/knowledge')) return 'knowledge'
  return 'hub'
})

const handleMenuClick = (item) => {
  if (item.path) {
    router.push(item.path)
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
  background: #f8fafc;
}

.workspace-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
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
}

.sidebar-header.collapsed {
  justify-content: center;
  font-size: 24px;
  color: #2563eb;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 20px;
}

.logo-text {
  font-size: 20px;
  font-weight: 750;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.new-chat-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 18px;
}

.new-chat-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}

.sidebar-group {
  margin-bottom: 24px;
}

.history-section {
  margin-top: 32px;
  padding: 0 8px;
}

.sidebar-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  margin-bottom: 16px;
}

.history-block {
  margin-bottom: 20px;
}

.history-time {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.history-item {
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  font-size: 14px;
  color: #475569;
  padding: 6px 0;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
}

.history-item:hover {
  color: #2563eb;
}

.sidebar-footer {
  padding: 16px 12px;
  border-top: 1px solid #f1f5f9;
}

.workspace-main {
  flex: 1;
  min-width: 0;
  position: relative;
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

