<template>
  <div class="appendix" v-if="data">
    <!-- 任务信息 -->
    <div class="section task-info" v-if="data.taskInfo">
      <div class="section-header">
        <span class="section-icon icon-task">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
          </svg>
        </span>
        <span class="section-title">任务信息</span>
      </div>
      <div class="info-grid">
        <div class="info-item">
          <span class="info-label">任务ID</span>
          <span class="info-value trace-id">{{ data.taskInfo.taskId }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">审查时间</span>
          <span class="info-value">{{ data.taskInfo.reviewTime }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">模型版本</span>
          <span class="info-value">{{ data.taskInfo.modelVersion }}</span>
        </div>
      </div>
    </div>

    <!-- 规则清单（可折叠） -->
    <div class="section collapsible" v-if="data.ruleList?.length">
      <el-collapse>
        <el-collapse-item title="规则清单" name="rules">
          <template #title>
            <div class="collapse-title">
              <span class="section-icon icon-rules">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
                </svg>
              </span>
              <span>规则清单</span>
              <span class="collapse-count">{{ data.ruleList.length }} 条</span>
            </div>
          </template>
          <div class="rules-list">
            <div v-for="rule in data.ruleList" :key="rule.ruleId" class="rule-item">
              <div class="rule-header">
                <span class="rule-code">{{ rule.ruleCode }}</span>
                <span class="rule-id">ID: {{ rule.ruleId }}</span>
              </div>
              <div class="rule-desc">{{ rule.description }}</div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 原始证据片段（可折叠） -->
    <div class="section collapsible" v-if="data.evidenceFragments?.length">
      <el-collapse>
        <el-collapse-item title="原始证据片段" name="evidence">
          <template #title>
            <div class="collapse-title">
              <span class="section-icon icon-evidence">
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
                </svg>
              </span>
              <span>原始证据片段</span>
              <span class="collapse-count">{{ data.evidenceFragments.length }} 处</span>
            </div>
          </template>
          <div class="evidence-list">
            <div v-for="(fragment, idx) in data.evidenceFragments" :key="fragment.fragmentId" class="evidence-item">
              <div class="evidence-header">
                <span class="evidence-num">{{ idx + 1 }}</span>
                <span class="evidence-source">{{ fragment.source }}</span>
                <span class="fragment-id">ID: {{ fragment.fragmentId }}</span>
              </div>
              <div class="evidence-content">{{ fragment.content }}</div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>

  <!-- 无数据状态 -->
  <div class="empty-state" v-else>
    <span>暂无附录数据</span>
  </div>
</template>

<script setup lang="ts">
import type { Page6Appendix } from '../../types/report.types';

interface Props {
  data?: Page6Appendix;
}

defineProps<Props>();
</script>

<style scoped>
.appendix {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
}

/* Section 通用 */
.section {
  background: #ffffff;
  border: 1px solid #e8eaf0;
  border-radius: 14px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.section-icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-task {
  background: #f3f0ff;
  color: #7c3aed;
}

.icon-rules {
  background: #fff7e6;
  color: #faad14;
}

.icon-evidence {
  background: #e8ffea;
  color: #00b42a;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
}

/* 任务信息 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
  padding: 16px 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: #86909c;
}

.info-value {
  font-size: 14px;
  color: #1d2129;
  font-weight: 500;
}

.trace-id {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  background: #f7f8fa;
  padding: 4px 8px;
  border-radius: 4px;
}

/* 可折叠样式 */
.collapsible :deep(.el-collapse-item__header) {
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
}

.collapsible :deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

.collapsible :deep(.el-collapse-item__content) {
  padding: 0;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #1d2129;
}

.collapse-count {
  font-size: 12px;
  font-weight: 500;
  color: #86909c;
  background: #f2f3f5;
  padding: 2px 8px;
  border-radius: 10px;
  margin-left: 4px;
}

/* 规则列表 */
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 20px;
}

.rule-item {
  padding: 12px 14px;
  background: #f7f8fa;
  border-radius: 8px;
}

.rule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.rule-code {
  font-size: 13px;
  font-weight: 600;
  color: #165dff;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.rule-id {
  font-size: 11px;
  color: #86909c;
}

.rule-desc {
  font-size: 13px;
  color: #4e5969;
  line-height: 1.5;
}

/* 证据片段列表 */
.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px;
}

.evidence-item {
  padding: 14px 16px;
  background: #f7f8fa;
  border-radius: 10px;
  border: 1px solid #e8eaf0;
}

.evidence-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.evidence-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #eff4ff;
  color: #165dff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.evidence-source {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
}

.fragment-id {
  font-size: 11px;
  color: #86909c;
  margin-left: auto;
}

.evidence-content {
  font-size: 13px;
  color: #4e5969;
  line-height: 1.7;
  padding: 10px 12px;
  background: #ffffff;
  border-radius: 6px;
  border: 1px solid #f0f0f0;
  word-break: break-word;
}

/* 空状态 */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #86909c;
  font-size: 14px;
}
</style>
