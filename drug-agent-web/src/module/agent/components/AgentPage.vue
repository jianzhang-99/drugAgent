<template>
  <div class="agent-page">
    <!-- 顶部操作栏 -->
    <header class="agent-header">
      <div class="header-left">
        <h1 class="title">药品监管AI系统</h1>
      </div>
      <div class="header-right">
        <t-button theme="primary" @click="handleNewSession">新建会话</t-button>
      </div>
    </header>

    <!-- 主体区域 -->
    <div class="agent-body">
      <!-- 左侧会话栏 -->
      <aside class="session-sidebar">
        <SessionSidebar />
      </aside>

      <!-- 中间对话主区域 -->
      <main class="chat-main">
        <ChatPanel />
      </main>
    </div>

    <!-- 结果详情抽屉 -->
    <ResultDrawer />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useAgentStore } from '../store/agentStore';
import SessionSidebar from './SessionSidebar.vue';
import ChatPanel from './ChatPanel.vue';
import ResultDrawer from './ResultDrawer.vue';

const store = useAgentStore();

onMounted(async () => {
  // 加载会话列表
  await store.loadSessions();
});

function handleNewSession() {
  store.createSession();
}
</script>

<style scoped>
.agent-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e7e7e7;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.agent-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.session-sidebar {
  width: 280px;
  background: #fff;
  border-right: 1px solid #e7e7e7;
  overflow-y: auto;
}

.chat-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
</style>
