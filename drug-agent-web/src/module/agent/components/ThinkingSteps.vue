<template>
  <div class="thinking-steps" :class="{ expanded: isExpanded }">
    <!-- 折叠头部 -->
    <button type="button" class="thinking-header" @click="toggle">
      <div class="header-left">
        <span class="brain-icon">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 2a5 5 0 0 1 5 5v1a5 5 0 0 1-5 5 5 5 0 0 1-5-5V7a5 5 0 0 1 5-5z" />
            <path d="M9 17v1a3 3 0 0 0 6 0v-1" />
            <path d="M12 12v5" />
          </svg>
        </span>
        <span class="header-label">推理过程</span>
        <span class="step-count">{{ steps.length }} 步</span>
      </div>
      <span class="chevron" :class="{ rotated: isExpanded }">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </span>
    </button>

    <!-- 步骤列表 -->
    <div class="steps-body" v-show="isExpanded">
      <transition-group name="step-reveal" tag="div" class="steps-track">
        <div
          v-for="(step, index) in steps"
          :key="step.code + index"
          class="step-item"
          :class="[`status-${step.status.toLowerCase()}`, `type-${step.type.toLowerCase()}`]"
        >
          <!-- 左侧连线 + 图标 -->
          <div class="step-rail">
            <div class="step-dot" :class="`status-${step.status.toLowerCase()}`">
              <!-- COMPLETED -->
              <svg v-if="step.status === 'COMPLETED'" xmlns="http://www.w3.org/2000/svg" width="12" height="12"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
                stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              <!-- FAILED -->
              <svg v-else-if="step.status === 'FAILED'" xmlns="http://www.w3.org/2000/svg" width="12" height="12"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
                stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
              <!-- PROCESSING - 旋转动画 -->
              <svg v-else-if="step.status === 'PROCESSING'" class="spin-animation" xmlns="http://www.w3.org/2000/svg" width="12" height="12"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"
                stroke-linejoin="round">
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <!-- INFO -->
              <svg v-else xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24"
                fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
            <div class="step-line" v-if="index < steps.length - 1" :class="{ 'line-processing': step.status === 'PROCESSING' }" />
          </div>

          <!-- 右侧内容 -->
          <div class="step-content">
            <div class="step-title-row">
              <span class="step-type-tag" :class="`tag-${step.type.toLowerCase()}`">{{ typeLabel(step.type) }}</span>
              <span class="step-title">{{ step.title }}</span>
            </div>
            <p class="step-detail" v-if="step.detail">{{ step.detail }}</p>
          </div>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { ThinkingStep } from '../types/agent';

const props = defineProps<{
  steps: ThinkingStep[];
  /** 初始是否展开，默认折叠 */
  defaultExpanded?: boolean;
}>();

const isExpanded = ref(props.defaultExpanded ?? false);

function toggle() {
  isExpanded.value = !isExpanded.value;
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    ROUTE: '路由',
    EXECUTION: '执行',
    CLARIFICATION: '确认',
    ERROR: '异常',
    FINALIZE: '完成',
  };
  return map[type] ?? type;
}
</script>

<style scoped>
/* ===== 容器 ===== */
.thinking-steps {
  margin-bottom: 10px;
  border: 1px solid #e9effb;
  border-radius: 10px;
  background: #f8faff;
  overflow: hidden;
  transition: border-color 0.2s;
}

.thinking-steps.expanded {
  border-color: #d0dcf8;
}

/* ===== 折叠头部 ===== */
.thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 14px;
  background: transparent;
  border: none;
  cursor: pointer;
  gap: 8px;
  transition: background 0.15s;
}

.thinking-header:hover {
  background: #edf2ff;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.brain-icon {
  display: flex;
  align-items: center;
  color: #6366f1;
}

.header-label {
  font-size: 12px;
  font-weight: 600;
  color: #4f5b76;
}

.step-count {
  font-size: 11px;
  color: #94a3b8;
  background: #e2e8f0;
  padding: 1px 7px;
  border-radius: 999px;
}

.chevron {
  color: #94a3b8;
  display: flex;
  align-items: center;
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.chevron.rotated {
  transform: rotate(180deg);
}

/* ===== 步骤列表 ===== */
.steps-body {
  padding: 4px 14px 14px;
}

.steps-track {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* ===== 单个步骤 ===== */
.step-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.step-reveal-enter-active,
.step-reveal-leave-active {
  transition: opacity 0.28s ease, transform 0.28s ease;
}

.step-reveal-enter-from,
.step-reveal-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

/* 左侧轨道 */
.step-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  padding-top: 3px;
}

.step-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1.5px solid transparent;
}

/* 状态颜色 */
.status-completed .step-dot {
  background: #ecfdf5;
  color: #10b981;
  border-color: #a7f3d0;
}

.status-failed .step-dot {
  background: #fef2f2;
  color: #ef4444;
  border-color: #fecaca;
}

.status-info .step-dot {
  background: #fefce8;
  color: #f59e0b;
  border-color: #fde68a;
}

.status-processing .step-dot {
  background: #eff6ff;
  color: #3b82f6;
  border-color: #93c5fd;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.spin-animation {
  animation: spin 1.5s linear infinite;
}

.step-line.line-processing {
  background: linear-gradient(#3b82f6, #93c5fd);
}

.step-line {
  width: 1.5px;
  flex: 1;
  min-height: 12px;
  background: linear-gradient(#d1d5db, #e5e7eb);
  margin: 3px 0;
}

/* 右侧内容 */
.step-content {
  flex: 1;
  min-width: 0;
  padding-bottom: 12px;
}

.step-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.step-type-tag {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  letter-spacing: 0.3px;
}

.tag-route       { background: #ede9fe; color: #7c3aed; }
.tag-execution   { background: #e0f2fe; color: #0369a1; }
.tag-clarification { background: #fef9c3; color: #92400e; }
.tag-error       { background: #fee2e2; color: #b91c1c; }
.tag-finalize    { background: #dcfce7; color: #15803d; }

.step-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.step-detail {
  margin: 4px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  word-break: break-all;
}
</style>
