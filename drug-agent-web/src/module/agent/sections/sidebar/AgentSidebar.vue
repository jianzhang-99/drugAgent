<template>
  <aside class="sidebar-shell">
    <div class="brand-card">
      <div class="brand-mark">✦</div>
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
        <span class="nav-icon">{{ item.icon }}</span>
        <span v-if="!store.isSidebarCollapsed" class="nav-label">{{ item.label }}</span>
      </button>
    </nav>

    <section class="history-block" v-show="!store.isSidebarCollapsed">
      <header class="block-header">
        <span class="block-kicker">历史审查会话</span>
      </header>

      <div class="history-scroll">
        <div v-for="group in groupedSessions" :key="group.label" class="history-group">
          <div v-if="group.items.length" class="group-title">{{ group.label }}</div>

          <div
            v-for="session in group.items"
            :key="session.id"
            :class="['history-item', { active: session.id === store.activeSessionId }]"
            @click="store.selectSession(session.id)"
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
      <button class="footer-link" type="button" v-if="!store.isSidebarCollapsed">
        <span>⚙</span>
        <span>偏好与系统配置</span>
      </button>
      <button class="footer-link muted" type="button" @click="store.isSidebarCollapsed = !store.isSidebarCollapsed">
        <span>☰</span>
        <span v-if="!store.isSidebarCollapsed">收起侧边栏</span>
        <span v-else>展开</span>
      </button>
    </footer>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useAgentStore } from '../../store/agentStore';

type ViewMode = 'WORKSPACE' | 'TASKS' | 'KNOWLEDGE';

const store = useAgentStore();
const editingSessionId = ref<string | null>(null);
const editingTitle = ref('');
const titleInputRef = ref<HTMLInputElement>();

const navItems = [
  { id: 'TASKS' as ViewMode, label: '全局任务看板', icon: '◎' },
  { id: 'KNOWLEDGE' as ViewMode, label: '合规知识库', icon: '▣' },
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

function handleDeleteSession(id: string) {
  store.removeSession(id);
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
}

.brand-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px 14px;
}

.brand-mark {
  display: flex;
  height: 42px;
  width: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: linear-gradient(135deg, #2e6cf6, #4b54e8);
  color: #fff;
  font-size: 20px;
  box-shadow: 0 14px 30px rgba(47, 108, 246, 0.2);
}

.brand-copy {
  flex: 1;
}

.brand-copy h1 {
  margin: 0;
  font-size: 21px;
  font-weight: 700;
  color: #23324d;
}

.brand-copy p {
  margin: 3px 0 0;
  color: #8292ab;
  font-size: 12px;
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

.nav-item {
  display: flex;
  align-items: center;
  gap: 14px;
  border: none;
  border-radius: 14px;
  padding: 14px 12px;
  background: transparent;
  color: #384b67;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.74);
  color: #20314c;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.5);
}

.nav-icon {
  width: 28px;
  text-align: center;
  color: #5a5df0;
}

.history-block {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.block-header {
  padding: 0 24px 12px;
}

.block-kicker {
  color: #9ba9bf;
  font-size: 12px;
  font-weight: 700;
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
  border-radius: 14px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
}

.history-item.active {
  background: #edf3ff;
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
