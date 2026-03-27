<template>
  <div class="session-sidebar">
    <template v-for="group in groupedSessions" :key="group.label">
      <div v-if="group.items.length" class="group-block">
        <div class="group-label">{{ group.label }}</div>
        <div
          v-for="session in group.items"
          :key="session.id"
          :class="['session-item', { active: session.id === store.activeSessionId }]"
          @click="handleSelectSession(session.id)"
        >
          <span class="session-title">{{ session.title }}</span>
          <button
            type="button"
            class="delete-btn"
            title="删除会话"
            @click.stop="handleDeleteSession(session.id)"
          >
            <t-icon name="delete" />
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';

const store = useAgentStore();

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

function handleSelectSession(id: string) {
  store.selectSession(id);
}

function handleDeleteSession(id: string) {
  store.removeSession(id);
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
.session-sidebar {
  height: 100%;
  overflow-y: auto;
  padding: 0 8px 12px;
}

.group-block {
  margin-bottom: 24px;
}

.group-label {
  padding: 0 12px 8px;
  font-size: 12px;
  color: #9aa9bf;
  font-weight: 700;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  color: #4b5e7c;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #eef4ff;
  color: #274269;
  font-weight: 700;
}

.session-title {
  font-size: 14px;
  line-height: 1.6;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  opacity: 0;
  padding: 4px 8px;
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

.session-item:hover .delete-btn {
  opacity: 1;
}

.delete-btn:hover {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}
</style>
