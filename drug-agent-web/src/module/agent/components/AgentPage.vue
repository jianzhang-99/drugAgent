<template>
  <div class="agent-page">
    <aside class="global-sidebar">
      <div class="brand-block">
        <div class="brand-left">
          <div class="brand-icon">✦</div>
          <div class="brand-copy">
            <h1>横渡智能体</h1>
          </div>
        </div>
        <button class="icon-btn" type="button" @click="handleNewSession">✎</button>
      </div>

      <div class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.id"
          type="button"
          :class="['nav-item', { active: activeView === item.id }]"
          @click="activeView = item.id"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
        </button>
      </div>

      <div class="sidebar-section-title">历史审查会话</div>
      <div class="sidebar-body">
        <SessionSidebar />
      </div>

      <div class="sidebar-footer">
        <button class="footer-link" type="button" @click="activeView = 'SETTINGS'">
          <span>⚙</span>
          <span>偏好与系统配置</span>
        </button>
        <button class="footer-link muted" type="button">
          <span>☰</span>
          <span>收起侧边栏</span>
        </button>
      </div>
    </aside>

    <main class="main-shell">
      <header class="topbar">
        <div class="topbar-title">
          {{ activeView === 'WORKSPACE' ? '智能体审查工作台' : viewTitleMap[activeView] }}
        </div>
        <div class="topbar-actions">
          <button class="task-pill" type="button">
            <span class="pulse"></span>
            {{ runningTasks }} 活跃任务
          </button>
          <div class="avatar">HD</div>
        </div>
      </header>

      <section class="content-shell">
        <div v-if="activeView === 'WORKSPACE'" class="workspace-shell">
          <ChatPanel />
        </div>
        <div v-else class="placeholder-shell">
          <div class="placeholder-card">
            <h3>{{ viewTitleMap[activeView] }}</h3>
            <p>这一页先保留轻量占位，当前优先把“新对话页面”按原型打磨好。</p>
          </div>
        </div>
      </section>

      <ResultDrawer />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useAgentStore } from '../store/agentStore';
import SessionSidebar from './SessionSidebar.vue';
import ChatPanel from './ChatPanel.vue';
import ResultDrawer from './ResultDrawer.vue';

type ViewMode = 'WORKSPACE' | 'TASKS' | 'KNOWLEDGE' | 'SETTINGS';

const store = useAgentStore();
const activeView = ref<ViewMode>('WORKSPACE');

const navItems = [
  { id: 'TASKS' as ViewMode, label: '全局任务看板', icon: '◎' },
  { id: 'KNOWLEDGE' as ViewMode, label: '合规知识库', icon: '▣' },
];

const viewTitleMap: Record<ViewMode, string> = {
  WORKSPACE: '智能体审查工作台',
  TASKS: '全局任务看板',
  KNOWLEDGE: '合规知识库',
  SETTINGS: '偏好与系统配置',
};

const runningTasks = computed(() => 0);

onMounted(async () => {
  await store.loadSessions();
});

function handleNewSession() {
  activeView.value = 'WORKSPACE';
  store.activeSessionId = null;
}
</script>

<style scoped>
.agent-page {
  display: flex;
  min-height: 100vh;
  background: #f8fafc;
}

.global-sidebar {
  width: 396px;
  min-width: 396px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #fbfcff 0%, #f5f7fb 100%);
  border-right: 1px solid #dbe4f0;
}

.brand-block {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 18px;
}

.brand-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3b82f6, #4f46e5);
  color: #fff;
  font-size: 20px;
  box-shadow: 0 10px 20px rgba(59, 130, 246, 0.22);
}

.brand-copy h1 {
  margin: 0;
  font-size: 22px;
  color: #22324d;
  font-weight: 700;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #7487a6;
  cursor: pointer;
  font-size: 18px;
}

.nav-list {
  padding: 12px 16px 24px;
}

.nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 10px;
  border: none;
  background: transparent;
  color: #30435f;
  cursor: pointer;
  text-align: left;
}

.nav-item.active {
  color: #20314c;
  font-weight: 700;
}

.nav-icon {
  width: 28px;
  text-align: center;
  color: #5b5cf0;
}

.nav-label {
  font-size: 18px;
  font-weight: 600;
}

.sidebar-section-title {
  padding: 0 24px;
  font-size: 12px;
  color: #9aa9bf;
  font-weight: 700;
  margin-bottom: 12px;
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  padding: 0 12px;
}

.sidebar-footer {
  padding: 18px 16px;
  border-top: 1px solid #dbe4f0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.footer-link {
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  color: #5f7391;
  cursor: pointer;
  text-align: left;
}

.footer-link.muted {
  color: #7e92ae;
}

.main-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #ffffff;
}

.topbar {
  height: 74px;
  border-bottom: 1px solid #e7edf5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  background: rgba(255, 255, 255, 0.92);
}

.topbar-title {
  font-size: 18px;
  color: #8ea0b8;
  font-weight: 700;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.task-pill {
  border: 1px solid #e6edf7;
  background: #fff;
  color: #334866;
  border-radius: 999px;
  padding: 10px 18px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 700;
}

.pulse {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid #5c6c80;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4f46e5, #3b82f6);
  color: white;
  font-weight: 800;
}

.content-shell {
  flex: 1;
  min-height: 0;
}

.workspace-shell,
.placeholder-shell {
  height: 100%;
}

.placeholder-shell {
  display: flex;
  align-items: center;
  justify-content: center;
}

.placeholder-card {
  width: min(560px, 90%);
  border: 1px solid #e5ebf4;
  border-radius: 24px;
  padding: 32px;
  background: #fff;
  text-align: center;
}

.placeholder-card h3 {
  margin: 0 0 12px;
  color: #21324e;
}

.placeholder-card p {
  margin: 0;
  color: #7487a6;
  line-height: 1.8;
}

@media (max-width: 1024px) {
  .agent-page {
    flex-direction: column;
  }

  .global-sidebar {
    width: 100%;
    min-width: 0;
  }
}
</style>
