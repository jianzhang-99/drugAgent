<template>
  <div class="agent-workbench">
    <AgentSidebar class="workbench-sidebar" />

    <div class="workbench-main">
      <WorkspaceHeader />

      <main class="workbench-content">
        <ConversationWorkspace />
      </main>

      <ResultDrawer />
    </div>
  </div>
</template>

<script setup lang="ts">
import AgentSidebar from '../sections/sidebar/AgentSidebar.vue';
import WorkspaceHeader from '../sections/workspace/WorkspaceHeader.vue';
import ConversationWorkspace from '../sections/workspace/ConversationWorkspace.vue';
import ResultDrawer from '../components/ResultDrawer.vue';
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
  flex: 0 0 396px;
}

.workbench-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
}

.workbench-content {
  flex: 1;
  min-height: 0;
}

@media (max-width: 1120px) {
  .workbench-sidebar {
    flex-basis: 320px;
  }
}

@media (max-width: 900px) {
  .agent-workbench {
    flex-direction: column;
  }

  .workbench-sidebar {
    flex-basis: auto;
  }
}
</style>
