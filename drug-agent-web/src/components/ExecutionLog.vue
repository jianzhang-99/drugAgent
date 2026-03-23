<template>
  <section class="execution-log-panel">
    <div class="panel-head">
      <h2>执行日志</h2>
      <div class="log-actions">
        <button v-if="logs.length > 0" class="clear-btn" @click="$emit('clear')">
          清空日志
        </button>
      </div>
    </div>

    <div v-if="logs.length > 0" class="log-timeline">
      <div
        v-for="(log, index) in logs"
        :key="log.id || index"
        class="log-item"
        :class="[`log-${log.level || 'info'}`, { 'log-active': log.status === 'running' }]"
      >
        <div class="log-indicator">
          <div class="log-dot" :class="statusClass(log.status)"></div>
          <div v-if="index < logs.length - 1" class="log-line"></div>
        </div>

        <div class="log-content">
          <div class="log-header">
            <span class="log-time">{{ formatTime(log.timestamp || log.createdAt) }}</span>
            <span class="log-type-badge" :class="`type-${log.type?.toLowerCase() || 'default'}`">
              {{ log.type || 'INFO' }}
            </span>
          </div>
          <p class="log-title">{{ log.title || log.message || '未知操作' }}</p>
          <p v-if="log.detail" class="log-detail">{{ log.detail }}</p>
          <div v-if="log.metadata" class="log-metadata">
            <span v-for="(value, key) in log.metadata" :key="key" class="meta-tag">
              {{ key }}: {{ value }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-logs">
      <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2" />
        <rect x="9" y="3" width="6" height="4" rx="1" />
        <path d="M9 12h6M9 16h6" />
      </svg>
      <p>暂无执行日志</p>
      <span>开始解析文档后，将在此展示处理步骤和状态。</span>
    </div>
  </section>
</template>

<script setup>
import { formatTime } from '@/utils/timeFormat'

defineProps({
  logs: {
    type: Array,
    default: () => []
  }
})

defineEmits(['clear'])

const statusClass = (status) => {
  const map = {
    running: 'dot-running',
    success: 'dot-success',
    error: 'dot-error',
    pending: 'dot-pending'
  }
  return map[status] || 'dot-default'
}
</script>

<style scoped>
.execution-log-panel {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.panel-head h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
}

.log-actions {
  display: flex;
  gap: 8px;
}

.clear-btn {
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #fef2f2;
  color: #dc2626;
}

.log-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.log-item {
  display: flex;
  gap: 14px;
  padding: 12px 0;
}

.log-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 16px;
}

.log-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #d1d5db;
  flex-shrink: 0;
}

.dot-running {
  background: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.2);
  animation: pulse 1.5s infinite;
}

.dot-success {
  background: #10b981;
}

.dot-error {
  background: #ef4444;
}

.dot-pending {
  background: #d1d5db;
}

.dot-default {
  background: #6b7280;
}

.log-line {
  width: 2px;
  flex: 1;
  background: #e5e7eb;
  margin-top: 6px;
}

.log-content {
  flex: 1;
  min-width: 0;
}

.log-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.log-time {
  font-size: 12px;
  color: var(--text-muted);
  font-family: monospace;
}

.log-type-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.type-doc_parsed,
.type-success {
  background: #ecfdf3;
  color: #15803d;
}

.type-parse,
.type-running {
  background: #eff6ff;
  color: #1d4ed8;
}

.type-error,
.type-failed {
  background: #fef2f2;
  color: #dc2626;
}

.type-warning {
  background: #fffbeb;
  color: #b45309;
}

.type-default {
  background: #f3f4f6;
  color: #4b5563;
}

.log-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.log-detail {
  margin: 0;
  font-size: 13px;
  color: var(--text-sub);
  line-height: 1.5;
}

.log-metadata {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.meta-tag {
  display: inline-flex;
  padding: 3px 8px;
  background: #f3f4f6;
  border-radius: 6px;
  font-size: 11px;
  color: var(--text-muted);
}

.log-error {
  background: #fef2f2;
  border-radius: 12px;
  padding: 12px;
  margin-top: 8px;
}

.log-error .log-title {
  color: #dc2626;
}

.log-active {
  background: #eff6ff;
  border-radius: 12px;
  padding: 12px;
  margin: 0 -12px;
}

.empty-logs {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  width: 48px;
  height: 48px;
  color: var(--text-muted);
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-logs p {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.empty-logs span {
  font-size: 13px;
  color: var(--text-sub);
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
