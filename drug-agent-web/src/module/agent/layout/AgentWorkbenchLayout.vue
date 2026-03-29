<template>
  <div class="agent-workbench">
    <AgentSidebar
      class="workbench-sidebar"
      :style="{ flexBasis: store.isSidebarCollapsed ? '80px' : '396px', overflow: 'hidden', transition: 'flex-basis 0.3s cubic-bezier(0.4, 0, 0.2, 1)' }"
    />

    <div class="workbench-main">
      <WorkspaceHeader v-if="store.activeView === 'WORKSPACE'" />

      <main class="workbench-content">
        <ConversationWorkspace v-if="store.activeView === 'WORKSPACE'" />
        <TaskBoardMock v-else-if="store.activeView === 'TASKS'" />
        <KnowledgeBaseMock v-else-if="store.activeView === 'KNOWLEDGE'" />
      </main>

      <ResultDrawer v-if="store.activeView === 'WORKSPACE'" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useAgentStore } from '../store/agentStore';
import AgentSidebar from '../sections/sidebar/AgentSidebar.vue';
import WorkspaceHeader from '../sections/workspace/WorkspaceHeader.vue';
import ConversationWorkspace from '../sections/workspace/ConversationWorkspace.vue';
import ResultDrawer from '../components/ResultDrawer.vue';
import TaskBoardMock from '../pages/TaskBoardMock.vue';
import KnowledgeBaseMock from '../pages/KnowledgeBaseMock.vue';

const store = useAgentStore();
</script>

<style scoped>
.agent-workbench {
  display: flex;
  min-height: 100vh;
  background:
    linear-gradient(180deg, rgba(248, 250, 255, 0.94), rgba(241, 246, 252, 0.94)),
    radial-gradient(circle at top right, rgba(64, 112, 244, 0.09), transparent 24%);
}

.workbench-sidebar {
  /* flex-basis handled dynamically */
  flex: 0 0 396px;
}

.workbench-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.workbench-content {
  flex: 1;
  min-height: 0;
}

@media (max-width: 1120px) {
  .workbench-sidebar {
    /* overridden by inline style if collapsed */
  }
}

@media (max-width: 900px) {
  .agent-workbench {
    flex-direction: column;
  }

  .workbench-sidebar {
    flex-basis: auto !important;
  }
}
</style>
