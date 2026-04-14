<template>
  <AgentWorkbenchLayout>
    <ConversationWorkspace v-if="store.activeView === 'WORKSPACE'" />
    <TaskBoardMock v-else-if="store.activeView === 'TASKS'" />
    <KnowledgeBaseMock v-else-if="store.activeView === 'KNOWLEDGE'" />
    <ModelBenchmark v-else-if="store.activeView === 'BENCHMARK'" />
  </AgentWorkbenchLayout>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useAgentStore } from '../store/agentStore';
import AgentWorkbenchLayout from '../layout/AgentWorkbenchLayout.vue';
import ConversationWorkspace from '../sections/workspace/ConversationWorkspace.vue';
import TaskBoardMock from '../pages/TaskBoardMock.vue';
import KnowledgeBaseMock from '../pages/KnowledgeBaseMock.vue';
import ModelBenchmark from '../pages/ModelBenchmark.vue';

const store = useAgentStore();
const route = useRoute();

onMounted(async () => {
  await store.loadSessions();

  // 根据路由路径设置 activeView
  if (route.path === '/agent/benchmark') {
    store.activeView = 'BENCHMARK';
  } else if (route.path === '/agent/tasks') {
    store.activeView = 'TASKS';
  } else if (route.path === '/agent/knowledge') {
    store.activeView = 'KNOWLEDGE';
  }

  // 默认选中第一个已有会话（如果存在且当前没选中任何会话）
  if (store.sessions.length > 0 && !store.activeSessionId) {
    await store.selectSession(store.sessions[0].id);
  }
});
</script>
