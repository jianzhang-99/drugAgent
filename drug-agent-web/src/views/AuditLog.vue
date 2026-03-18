<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <div>
          <h1>全局审计日志</h1>
          <p>统一查看任务创建、问答完成、知识导入、文档解析等关键操作。</p>
        </div>
      </header>

      <div v-if="logs.length > 0" class="log-list">
        <article v-for="log in logs" :key="log.id" class="log-card">
          <div class="log-top">
            <span class="log-type">{{ log.type }}</span>
            <time>{{ formatTime(log.createdAt) }}</time>
          </div>
          <h2>{{ log.title }}</h2>
          <p>{{ log.detail }}</p>
        </article>
      </div>

      <div v-else class="empty-state">
        <h2>暂无审计日志</h2>
        <p>完成一次任务创建、问答或知识导入后，这里会自动记录操作轨迹。</p>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed } from 'vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import { getAuditLogs } from '../utils/local-state'

const logs = computed(() => getAuditLogs())

const formatTime = (value) => {
  if (!value) return '未知时间'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.page-shell {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px;
}

.page-header h1,
.empty-state h2,
.log-card h2 {
  margin: 0 0 8px;
  font-weight: 850;
}

.page-header p,
.empty-state p,
.log-card p {
  color: var(--text-sub);
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 24px;
}

.log-card,
.empty-state {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.log-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.log-type {
  display: inline-flex;
  padding: 6px 12px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 12px;
  font-weight: 700;
}
</style>
