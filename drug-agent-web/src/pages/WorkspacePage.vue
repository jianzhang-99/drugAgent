<template>
  <!--
    WorkspacePage.vue - 过渡阶段使用新的 AgentWorkbenchPage
    后续可以直接跳转到 /workbench 路由
  -->
  <AgentWorkbenchPage />
</template>

<script setup lang="ts">
import AgentWorkbenchPage from '@/components/agent/AgentWorkbenchPage.vue'
</script>
