<template>
  <AgentWorkbenchLayout>
    <ConversationWorkspace />
  </AgentWorkbenchLayout>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useAgentStore } from '../store/agentStore';
import AgentWorkbenchLayout from '../layout/AgentWorkbenchLayout.vue';
import ConversationWorkspace from '../sections/workspace/ConversationWorkspace.vue';

const store = useAgentStore();

onMounted(async () => {
  await store.loadSessions();

  // 默认选中第一个已有会话（如果存在且当前没选中任何会话）
  if (store.sessions.length > 0 && !store.activeSessionId) {
    await store.selectSession(store.sessions[0].id);
  }
});
</script>
