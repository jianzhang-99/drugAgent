<template>
  <div class="workspace-layout">
    <aside class="workspace-sidebar">
      <div class="sidebar-top">
        <slot name="sidebar-top">
          <section class="sidebar-group">
            <p class="sidebar-label">我的空间</p>
            <sidebar-item
              v-for="item in workspaceItems"
              :key="item.key"
              :icon="item.icon"
              :label="item.label"
              :active="activeMenu === item.key"
              @click="handleMenuClick(item)"
            />
          </section>
        </slot>
      </div>

      <div class="sidebar-bottom">
        <slot name="sidebar-bottom">
          <section class="sidebar-group">
            <p class="sidebar-label">系统设置</p>
            <sidebar-item
              v-for="item in settingItems"
              :key="item.key"
              :icon="item.icon"
              :label="item.label"
            />
          </section>
        </slot>
      </div>
    </aside>

    <main class="workspace-main">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Position,
  List,
  Reading,
  RefreshLeft,
  Setting,
  QuestionFilled
} from '@element-plus/icons-vue'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()

const workspaceItems = [
  { key: 'hub', label: '工作台 (Agent Hub)', icon: Position, path: '/agent' },
  { key: 'tasks', label: '任务看板', icon: List, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading, path: '/agent/knowledge' },
  { key: 'audit', label: '全局审计日志', icon: RefreshLeft, path: '/agent/audit' }
]

const settingItems = [
  { key: 'prefs', label: '偏好与配置', icon: Setting, path: '/agent/settings' },
  { key: 'help', label: '使用帮助', icon: QuestionFilled, path: '/agent/help' }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  if (route.path.startsWith('/agent/knowledge')) return 'knowledge'
  if (route.path.startsWith('/agent/audit')) return 'audit'
  if (route.path.startsWith('/agent/settings')) return 'prefs'
  if (route.path.startsWith('/agent/help')) return 'help'
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
    width: 100%;
    height: auto;
    position: relative;
    border-right: 0;
    border-bottom: 1px solid var(--border-light);
  }
}
</style>
```
