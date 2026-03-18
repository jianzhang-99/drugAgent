<template>
  <div class="workspace-layout">
    <aside class="workspace-sidebar" :class="{ collapsed: isCollapsed }">
      <div class="sidebar-top">
        <slot name="sidebar-top">
          <section class="sidebar-group">
            <p v-if="!isCollapsed" class="sidebar-label">我的空间</p>
            <sidebar-item
              v-for="item in workspaceItems"
              :key="item.key"
              :icon="item.icon"
              :label="item.label"
              :active="activeMenu === item.key"
              :collapsed="isCollapsed"
              @click="handleMenuClick(item)"
              :title="isCollapsed ? item.label : ''"
            />
          </section>
        </slot>
      </div>

      <div class="sidebar-footer">
        <button class="collapse-toggle" @click="isCollapsed = !isCollapsed">
          <el-icon>
            <component :is="isCollapsed ? Expand : Fold" />
          </el-icon>
        </button>
      </div>
    </aside>

    <main class="workspace-main">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Position,
  List,
  Reading,
  RefreshLeft,
  Fold,
  Expand
} from '@element-plus/icons-vue'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)

const workspaceItems = [
  { key: 'hub', label: '工作台 (Agent Hub)', icon: Position, path: '/agent' },
  { key: 'tasks', label: '任务看板', icon: List, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading, path: '/agent/knowledge' },
  { key: 'audit', label: '全局审计日志', icon: RefreshLeft, path: '/agent/audit' }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  if (route.path.startsWith('/agent/knowledge')) return 'knowledge'
  if (route.path.startsWith('/agent/audit')) return 'audit'
  return 'hub'
})

const handleMenuClick = (item) => {
  if (item.path) {
    router.push(item.path)
  }
}
</script>

<style scoped>
.workspace-layout {
  display: flex;
  min-height: 100vh;
}

.workspace-sidebar {
  width: var(--sidebar-width);
  background: var(--card-glass);
  backdrop-filter: var(--glass-blur);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 32px 20px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  transition: var(--transition-smooth);
}

.workspace-sidebar.collapsed {
  width: 80px;
  padding: 32px 12px;
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sidebar-label {
  margin: 0 0 12px 16px;
  font-size: 13px;
  font-weight: 800;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  white-space: nowrap;
}

.sidebar-footer {
  margin-top: auto;
  border-top: 1px solid var(--border-light);
  padding-top: 16px;
}

.workspace-sidebar.collapsed .sidebar-footer {
  border-top: 0;
}

.collapse-toggle {
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  cursor: pointer;
  transition: var(--transition-smooth);
}

.collapse-toggle:hover {
  background: var(--primary-bg);
  color: var(--primary-color);
}

.workspace-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

@media (max-width: 1100px) {
  .workspace-layout {
    flex-direction: column;
  }
  .workspace-sidebar {
    width: 100% !important;
    height: auto;
    position: relative;
    border-right: 0;
    border-bottom: 1px solid var(--border-light);
  }
  .collapse-toggle {
    display: none;
  }
}
</style>
