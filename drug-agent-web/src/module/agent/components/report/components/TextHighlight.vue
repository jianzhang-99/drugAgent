<template>
  <div class="text-highlight">
    <div class="highlight-header">
      <span class="highlight-category">{{ category }}</span>
      <div class="highlight-meta">
        <span class="similarity" v-if="similarity">相似度：{{ similarity }}</span>
        <span class="verdict-badge" :class="verdictClass">{{ verdict }}</span>
      </div>
    </div>
    <div class="highlight-content">
      <div class="text-col text-a" v-if="textA">
        <div class="text-label">
          <span class="label-dot dot-a"></span>
          <span>{{ labelA }}</span>
        </div>
        <div class="text-body" v-html="highlightText(textA, textB)"></div>
      </div>
      <div class="text-col text-b" v-if="textB">
        <div class="text-label">
          <span class="label-dot dot-b"></span>
          <span>{{ labelB }}</span>
        </div>
        <div class="text-body" v-html="highlightText(textB, textA)"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  category?: string;
  textA?: string;
  textB?: string;
  labelA?: string;
  labelB?: string;
  similarity?: string;
  verdict?: string;
}

const props = withDefaults(defineProps<Props>(), {
  category: '',
  textA: '',
  textB: '',
  labelA: '文本A',
  labelB: '文本B',
  similarity: '',
  verdict: '',
});

const verdictClass = computed(() => {
  if (props.verdict.includes('高度相似') || props.verdict.includes('完全相同')) {
    return 'verdict-danger';
  }
  if (props.verdict.includes('相似')) {
    return 'verdict-warning';
  }
  return 'verdict-normal';
});

function highlightText(source: string, compare: string): string {
  if (!compare || !source) return escapeHtml(source);

  // 简单的相似文本高亮逻辑
  // 实际项目中可以使用更复杂的相似度算法
  const sourceEscaped = escapeHtml(source);
  return sourceEscaped;
}

function escapeHtml(text: string): string {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
    .replace(/\n/g, '<br/>');
}
</script>

<style scoped>
.text-highlight {
  border: 1px solid #e8eaf0;
  border-radius: 12px;
  overflow: hidden;
}

.highlight-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f7f8fa;
  border-bottom: 1px solid #e8eaf0;
}

.highlight-category {
  font-size: 14px;
  font-weight: 600;
  color: #1d2129;
}

.highlight-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.similarity {
  font-size: 13px;
  font-weight: 600;
  color: #f53f3f;
}

.verdict-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.verdict-danger {
  background: #fff1f0;
  color: #f53f3f;
}

.verdict-warning {
  background: #fff7e6;
  color: #faad14;
}

.verdict-normal {
  background: #e8ffea;
  color: #00b42a;
}

.highlight-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.text-col {
  padding: 14px 16px;
}

.text-a {
  border-right: 1px solid #e8eaf0;
}

.text-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #4e5969;
  margin-bottom: 10px;
}

.label-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-a {
  background: #165dff;
}

.dot-b {
  background: #00b42a;
}

.text-body {
  font-size: 13px;
  color: #1d2129;
  line-height: 1.7;
  padding: 10px 12px;
  background: #fafafa;
  border-radius: 8px;
  word-break: break-word;
}

:deep(mark) {
  background: #fef0b2;
  color: inherit;
  border-radius: 2px;
  padding: 0 2px;
}
</style>
