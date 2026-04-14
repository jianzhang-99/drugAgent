<template>
  <div class="action-list">
    <div class="level-header" v-if="title">
      <span class="level-badge" :class="`badge-${level}`">{{ title }}</span>
      <span class="level-subtitle" v-if="subtitle">{{ subtitle }}</span>
    </div>
    <div class="actions">
      <div v-for="(action, idx) in actions" :key="idx" class="action-item">
        <div class="action-num">{{ idx + 1 }}</div>
        <div class="action-content">
          <div class="action-text">{{ typeof action === 'string' ? action : action.action }}</div>
          <div class="action-meta" v-if="typeof action === 'object' && (action.role || action.priority)">
            <span class="action-role" v-if="action.role">
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              {{ action.role }}
            </span>
            <span class="action-priority" :class="`priority-${(action.priority || '').toLowerCase()}`" v-if="action.priority">
              {{ action.priority }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface ActionItem {
  action: string;
  role?: string;
  priority?: string;
}

interface Props {
  title?: string;
  subtitle?: string;
  level?: string;
  actions: (string | ActionItem)[];
}

withDefaults(defineProps<Props>(), {
  title: '',
  subtitle: '',
  level: 'medium',
  actions: () => [],
});
</script>

<style scoped>
.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.level-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.level-badge {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
}

.badge-high,
.badge-1 {
  background: #fff1f0;
  color: #f53f3f;
}

.badge-medium,
.badge-2 {
  background: #fff7e6;
  color: #faad14;
}

.badge-low,
.badge-3 {
  background: #f4f5f7;
  color: #4e5969;
}

.level-subtitle {
  font-size: 13px;
  color: #86909c;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-item {
  display: flex;
  gap: 14px;
  padding: 12px 14px;
  background: #f7f8fa;
  border-radius: 10px;
}

.action-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #ffffff;
  color: #4e5969;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid #e8eaf0;
}

.action-content {
  flex: 1;
}

.action-text {
  font-size: 14px;
  color: #1d2129;
  font-weight: 500;
  line-height: 1.5;
  margin-bottom: 6px;
}

.action-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-role {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #86909c;
}

.action-priority {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.priority-high,
.priority-高 {
  background: #fff1f0;
  color: #f53f3f;
}

.priority-medium,
.priority-中 {
  background: #fff7e6;
  color: #faad14;
}

.priority-low,
.priority-低 {
  background: #f4f5f7;
  color: #86909c;
}
</style>
