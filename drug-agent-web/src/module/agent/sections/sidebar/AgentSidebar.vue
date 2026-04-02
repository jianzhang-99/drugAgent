<template>
  <aside class="sidebar-shell" :class="{ collapsed: store.isSidebarCollapsed }">
    <div class="brand-card">
      <div class="brand-mark">
        <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
      </div>
      <div class="brand-copy" v-if="!store.isSidebarCollapsed">
        <h1>横渡智能体</h1>
        <p>监管任务统一入口</p>
      </div>
      <button v-if="!store.isSidebarCollapsed" class="brand-action" type="button" @click="handleNewSession">✎</button>
    </div>

    <nav class="nav-block">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        :class="['nav-item', { active: item.id === store.activeView }]"
        @click="store.activeView = item.id"
      >
        <span class="nav-icon" v-html="item.icon"></span>
        <span class="nav-label" v-show="!store.isSidebarCollapsed">{{ item.label }}</span>
      </button>
    </nav>

    <section class="history-block" :class="{ 'is-hidden': store.isSidebarCollapsed }">
      <header class="block-header">
        <span class="block-kicker">历史审查会话</span>
        <button
          v-if="store.sessions.length > 0"
          type="button"
          class="clear-all-btn"
          title="清空所有会话"
          @click="handleClearAllSessions"
        >
          清空全部
        </button>
      </header>

      <div class="history-scroll">
        <div v-for="group in groupedSessions" :key="group.label" class="history-group">
          <div v-if="group.items.length" class="group-title">{{ group.label }}</div>

          <div
            v-for="session in group.items"
            :key="session.id"
            :class="['history-item', { active: session.id === store.activeSessionId }]"
            @click="handleSelectSession(session.id)"
          >
            <template v-if="editingSessionId === session.id">
              <input
                v-model="editingTitle"
                class="title-input"
                @keydown.enter.prevent="saveTitle"
                @keydown.esc.prevent="cancelEdit"
                @blur="saveTitle"
                @click.stop
                ref="titleInputRef"
              />
            </template>
            <template v-else>
              <span class="history-name">{{ session.title }}</span>
              <div class="item-actions">
                <button
                  type="button"
                  class="action-btn"
                  title="修改标题"
                  @click.stop="startEditTitle(session.id, session.title)"
                >
                  <t-icon name="edit" />
                </button>
                <button
                  type="button"
                  class="action-btn delete-btn"
                  title="删除会话"
                  @click.stop="handleDeleteSession(session.id)"
                >
                  <t-icon name="delete" />
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>
    </section>

    <footer class="sidebar-footer">
      <button class="footer-link" type="button" :class="{ collapsed: store.isSidebarCollapsed }" @click="showSettings = true">
        <span class="footer-icon">⚙</span>
        <span v-show="!store.isSidebarCollapsed">偏好与系统配置</span>
      </button>
      <button class="footer-link muted" type="button" @click="store.isSidebarCollapsed = !store.isSidebarCollapsed" :class="{ collapsed: store.isSidebarCollapsed }">
        <span class="footer-icon" v-if="!store.isSidebarCollapsed">☰</span>
        <span class="footer-icon" v-else>▤</span>
        <span v-show="!store.isSidebarCollapsed">收起侧边栏</span>
      </button>
    </footer>

    <SystemSettingsDialog v-model="showSettings" />
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import SystemSettingsDialog from '../../components/SystemSettingsDialog.vue';

type ViewMode = 'WORKSPACE' | 'TASKS' | 'KNOWLEDGE';

const store = useAgentStore();
const editingSessionId = ref<string | null>(null);
const editingTitle = ref('');
const titleInputRef = ref<HTMLInputElement>();
const showSettings = ref(false);

const navItems = [
  { id: 'TASKS' as ViewMode, label: '全局任务看板', icon: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>' },
  { id: 'KNOWLEDGE' as ViewMode, label: '合规知识大脑', icon: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>' },
];

const groupedSessions = computed(() => {
  const today: typeof store.sessions = [];
  const yesterday: typeof store.sessions = [];
  const week: typeof store.sessions = [];

  store.sessions.forEach((session) => {
    const label = formatTime(session.updatedAt);
    if (label === '今天') {
      today.push(session);
    } else if (label === '昨天') {
      yesterday.push(session);
    } else {
      week.push(session);
    }
  });

  return [
    { label: '今天', items: today },
    { label: '昨天', items: yesterday },
    { label: '过去 7 天', items: week },
  ];
});

onMounted(async () => {
  await store.loadSessions();
});

function handleNewSession() {
  store.activeView = 'WORKSPACE';
  store.activeSessionId = null;
}

/**
 * 选择会话处理（异步）
 * 等待selectSession完成以确保状态同步
 */
async function handleSelectSession(id: string) {
  store.activeView = 'WORKSPACE';
  await store.selectSession(id);
}

function handleDeleteSession(id: string) {
  store.removeSession(id);
}

function handleClearAllSessions() {
  if (store.sessions.length === 0) return;
  if (confirm(`确定要清空所有 ${store.sessions.length} 个会话吗？此操作不可恢复。`)) {
    store.clearAllSessions();
  }
}

function startEditTitle(id: string, title: string) {
  editingSessionId.value = id;
  editingTitle.value = title;
  nextTick(() => {
    titleInputRef.value?.focus();
    titleInputRef.value?.select();
  });
}

function saveTitle() {
  if (editingSessionId.value && editingTitle.value.trim()) {
    store.updateSessionTitle(editingSessionId.value, editingTitle.value.trim());
  }
  cancelEdit();
}

function cancelEdit() {
  editingSessionId.value = null;
  editingTitle.value = '';
}

function formatTime(timeStr: string) {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  if (days === 0) return '今天';
  if (days === 1) return '昨天';
  return '过去 7 天';
}
</script>

<style scoped>
.sidebar-shell {
  display: flex;
  height: 100%;
  min-height: 100vh;
  flex-direction: column;
  border-right: 1px solid #dfe7f1;
  background: linear-gradient(180deg, #f8fbff 0%, #f5f7fb 100%);
  width: 100%;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.brand-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px 14px;
}

.sidebar-shell.collapsed .brand-card {
  padding: 18px 20px;
  justify-content: center;
}

.brand-mark {
  display: flex;
  height: 38px;
  width: 38px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #10b981, #0ea5e9);
  color: #fff;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.brand-copy {
  flex: 1;
  overflow: hidden;
}

.brand-copy h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  color: #0f172a;
  white-space: nowrap;
}

.brand-copy p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.brand-action {
  border: none;
  background: transparent;
  color: #7788a5;
  font-size: 18px;
  cursor: pointer;
}

.nav-block {
  display: grid;
  gap: 6px;
  padding: 10px 16px 28px;
}

.sidebar-shell.collapsed .nav-block {
  padding: 10px 14px 28px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: none;
  border-radius: 12px;
  padding: 12px 14px;
  background: transparent;
  color: #475569;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.sidebar-shell.collapsed .nav-item {
  padding: 12px;
  justify-content: center;
}

.nav-item.active {
  background: #fff;
  color: #0f172a;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.nav-item:hover:not(.active) {
  background: rgba(255, 255, 255, 0.5);
}

.nav-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
}

.nav-item.active .nav-icon {
  color: #10b981;
}

.history-block {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  transition: opacity 0.2s cubic-bezier(0.4, 0, 0.2, 1), visibility 0.2s;
  opacity: 1;
  visibility: visible;
}

.history-block.is-hidden {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
}

.block-header {
  display: flex;
  align-items: center;
  padding: 0 24px 12px;
}

.block-kicker {
  color: #9ba9bf;
  font-size: 12px;
  font-weight: 700;
}

.clear-all-btn {
  margin-left: auto;
  border: none;
  background: transparent;
  color: #ff4d4f;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}

.clear-all-btn:hover {
  background: rgba(255, 77, 79, 0.1);
}

.history-scroll {
  overflow-y: auto;
  padding: 0 12px 18px;
}

.history-group {
  margin-bottom: 22px;
}

.group-title {
  padding: 0 12px 10px;
  color: #8ea0bb;
  font-size: 12px;
  font-weight: 700;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s;
}

.history-item:hover {
  background: rgba(255, 255, 255, 0.6);
}

.history-item.active {
  background: #f1f5f9;
}

.history-name {
  color: #425673;
  font-size: 15px;
  line-height: 1.6;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item.active .history-name {
  color: #274269;
  font-weight: 700;
}

.title-input {
  flex: 1;
  border: 1px solid #3b82f6;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 14px;
  outline: none;
  background: #fff;
  color: #31435f;
}

.title-input:focus {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}

.item-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}

.history-item:hover .item-actions {
  opacity: 1;
}

.history-item:not(:hover) .item-actions {
  opacity: 0;
}

.action-btn {
  padding: 4px 6px;
  border: none;
  background: transparent;
  color: #9aa9bf;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background: #f0f3f7;
  color: #3b82f6;
}

.delete-btn:hover {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.sidebar-footer {
  display: grid;
  gap: 12px;
  border-top: 1px solid #dfe7f1;
  padding: 18px 18px 24px;
  margin-top: auto;
}

.footer-link {
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  color: #60748f;
  font-size: 15px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.footer-link:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.footer-link.collapsed {
  justify-content: center;
  padding: 8px;
}

.footer-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  font-size: 16px;
}

.footer-link.muted {
  color: #8394ad;
}

@media (max-width: 900px) {
  .sidebar-shell {
    min-height: auto;
  }
}
</style>
