<template>
  <div class="session-sidebar">
    <!-- 搜索框 -->
    <div class="search-box">
      <t-input placeholder="搜索会话..." v-model="searchKeyword" @enter="handleSearch">
        <template #prefix-icon>
          <t-icon name="search" />
        </template>
      </t-input>
    </div>

    <!-- 会话列表 -->
    <div class="session-list">
      <div
        v-for="session in filteredSessions"
        :key="session.id"
        :class="['session-item', { active: session.id === store.activeSessionId }]"
        @click="handleSelectSession(session.id)"
      >
        <div class="session-info">
          <span class="session-title">{{ session.title }}</span>
          <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
        </div>
        <t-button
          class="delete-btn"
          theme="default"
          variant="text"
          size="small"
          @click.stop="handleDelete(session.id)"
        >
          <t-icon name="delete" />
        </t-button>
      </div>

      <!-- 空状态 -->
      <div v-if="filteredSessions.length === 0" class="empty-state">
        <span>暂无会话</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useAgentStore } from '../store/agentStore';

const store = useAgentStore();
const searchKeyword = ref('');

const filteredSessions = computed(() => {
  if (!searchKeyword.value) {
    return store.sessions;
  }
  const keyword = searchKeyword.value.toLowerCase();
  return store.sessions.filter(s =>
    s.title.toLowerCase().includes(keyword)
  );
});

function handleSelectSession(id: string) {
  store.selectSession(id);
}

function handleDelete(id: string) {
  store.removeSession(id);
}

function handleSearch() {
  // 搜索功能由 computed 自动处理
}

function formatTime(timeStr: string) {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  } else if (days === 1) {
    return '昨天';
  } else if (days < 7) {
    return `${days}天前`;
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  }
}
</script>

<style scoped>
.session-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
}

.search-box {
  margin-bottom: 16px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #e6f4ff;
}

.session-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.session-title {
  font-size: 14px;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .delete-btn {
  opacity: 1;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: #999;
  font-size: 14px;
}
</style>
