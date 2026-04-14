<template>
  <div v-if="data" class="action-suggestions">
    <section
      v-for="(level, index) in levels"
      :key="level.title"
      class="level-card"
      :class="`card-${index + 1}`"
    >
      <div class="level-head">
        <div>
          <h3>{{ level.title }}</h3>
          <p v-if="level.objective">目标：{{ level.objective }}</p>
        </div>
      </div>

      <div class="action-list">
        <article
          v-for="(action, actionIndex) in level.actions"
          :key="`${level.title}-${actionIndex}`"
          class="action-item"
        >
          <div class="action-index">{{ actionIndex + 1 }}</div>
          <div class="action-body">
            <div class="action-text">{{ action.action }}</div>
            <div class="action-meta">
              <span>{{ action.role }}</span>
              <span class="priority" :class="`priority-${priorityCode(action.priority)}`">
                {{ action.priority }}
              </span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="role-panel">
      <div class="role-header">
        <h3>责任角色建议</h3>
        <p>便于评标、风控和合规角色快速分工。</p>
      </div>
      <el-table :data="roleRows" border stripe style="width: 100%">
        <el-table-column prop="action" label="动作" min-width="280" />
        <el-table-column prop="role" label="建议责任角色" min-width="160" />
        <el-table-column prop="priority" label="优先级" min-width="100">
          <template #default="{ row }">
            <span class="priority" :class="`priority-${priorityCode(row.priority)}`">{{ row.priority }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="data.retentionAdvice?.length" class="retention-panel">
      <div class="role-header">
        <h3>留痕建议</h3>
        <p>确保后续审计、复议和监管协同可以完整追溯。</p>
      </div>
      <div class="retention-list">
        <div v-for="(item, index) in data.retentionAdvice" :key="index" class="retention-item">
          <span class="retention-index">{{ index + 1 }}</span>
          <span>{{ item }}</span>
        </div>
      </div>
    </section>
  </div>

  <div v-else class="empty-state">暂无处置建议数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ActionLevel, Page5ActionSuggestions } from '../../types/report.types';

interface Props {
  data?: Page5ActionSuggestions;
}

const props = defineProps<Props>();

function isActionLevel(level?: ActionLevel): level is ActionLevel {
  return !!level;
}

const levels = computed(() =>
  [props.data?.level1, props.data?.level2, props.data?.level3].filter(isActionLevel)
);

const roleRows = computed(() =>
  levels.value.flatMap((level) =>
    (level?.actions || []).map((action) => ({
      action: action.action,
      role: action.role,
      priority: action.priority,
    }))
  )
);

function priorityCode(priority?: string) {
  const value = (priority || '').toLowerCase();
  if (value.includes('高') || value.includes('high')) return 'high';
  if (value.includes('中') || value.includes('medium')) return 'medium';
  return 'low';
}
</script>

<style scoped>
.action-suggestions {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.level-card,
.role-panel,
.retention-panel {
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  padding: 20px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.card-1 {
  border-left: 6px solid #ef4444;
}

.card-2 {
  border-left: 6px solid #f59e0b;
}

.card-3 {
  border-left: 6px solid #64748b;
}

.level-head h3,
.role-header h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.level-head p,
.role-header p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.action-item {
  display: flex;
  gap: 14px;
  padding: 16px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
}

.action-index,
.retention-index {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f172a;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  flex-shrink: 0;
}

.action-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-text {
  color: #0f172a;
  line-height: 1.7;
  font-weight: 600;
}

.action-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #64748b;
  font-size: 12px;
}

.priority {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.priority-high {
  background: #fee2e2;
  color: #b91c1c;
}

.priority-medium {
  background: #fef3c7;
  color: #b45309;
}

.priority-low {
  background: #dbeafe;
  color: #1d4ed8;
}

.role-panel :deep(.el-table) {
  margin-top: 16px;
  border-radius: 16px;
  overflow: hidden;
}

.role-panel :deep(.el-table th) {
  background: #f8fafc !important;
  color: #475569;
  font-weight: 700;
}

.retention-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.retention-item {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
  border: 1px solid #dbeafe;
  color: #334155;
  line-height: 1.7;
}

.empty-state {
  padding: 32px;
  text-align: center;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .level-card,
  .role-panel,
  .retention-panel {
    padding: 16px;
  }

  .action-item,
  .retention-item {
    flex-direction: column;
  }
}
</style>
