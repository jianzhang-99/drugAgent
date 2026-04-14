<template>
  <div v-if="evidences?.length" class="detail-comparison">
    <!-- 报价对比表 -->
    <section class="section">
      <div class="section-header">
        <div class="header-icon">💰</div>
        <div>
          <h3>分项报价异常穿透审查</h3>
          <p>透视不同投标人之间的报价梯度、固定价差及非正常的一致性逻辑。</p>
        </div>
      </div>
      <el-table
        v-if="priceEvidences?.length"
        :data="priceEvidences"
        border
        stripe
        style="width: 100%"
        class="custom-table"
      >
        <el-table-column prop="title" label="比对项目" min-width="180" />
        <el-table-column prop="diffPayload.contentA" label="投标方 A 数据" min-width="160" />
        <el-table-column prop="diffPayload.contentB" label="投标方 B 数据" min-width="160" />
        <el-table-column prop="summary" label="判定逻辑与基准线差异" min-width="200" />
        <el-table-column prop="diffPayload.diffVerdict" label="置信判定" min-width="120">
          <template #default="{ row }">
            <span class="badge" :class="badgeClass(row.diffPayload?.diffVerdict)">{{ verdictText(row.diffPayload?.diffVerdict) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-block">未发现报价逻辑雷同。</div>
    </section>

    <!-- 文本对比区 (Code Diff Style) -->
    <section class="section diff-section">
      <div class="section-header">
        <div class="header-icon">📝</div>
        <div>
          <h3>源码级深度内容比对（Diff）</h3>
          <p>高亮锁定各标书间高度重合的段落，揭示异常协同作弊线索。黄色标记疑似同义改写区，红色标记一字不差复制。</p>
        </div>
      </div>
      
      <div v-if="textEvidences?.length" class="highlight-list">
        <article
          v-for="(item, index) in textEvidences"
          :key="`${item.evidenceId}-${index}`"
          class="diff-card"
        >
          <!-- 判定结论头部 -->
          <div class="diff-verdict-header" :class="`border-${badgeClass(item.diffPayload?.diffVerdict)}`">
            <div class="verdict-left">
              <span class="verdict-icon">
                {{ badgeClass(item.diffPayload?.diffVerdict) === 'danger' ? '🚨' : '⚠️' }}
              </span>
              <div class="verdict-info">
                <span class="verdict-cat">{{ item.title }}</span>
                <p class="verdict-analysis">{{ item.summary || '疑似大段落复制粘贴行为。' }}</p>
              </div>
            </div>
            <div class="verdict-right">
              <span class="similarity-text">雷同匹配率</span>
              <span class="similarity-score" :class="`text-${badgeClass(item.diffPayload?.diffVerdict)}`">{{ item.diffPayload?.similarityScore || '-' }}</span>
            </div>
          </div>

          <!-- 双屏编辑器区域 -->
          <div class="diff-editor-container">
            <div class="diff-pane">
              <div class="pane-header">
                <span class="pane-tab">投标方 A 原文</span>
                <span class="pane-actions">...</span>
              </div>
              <div class="pane-body df-bg-removed">
                <div class="line-numbers">
                  <span v-for="i in 5" :key="i">{{ i }}</span>
                </div>
                <div class="code-content">
                  <span class="diff-highlight diff-remove">{{ item.diffPayload?.contentA || '暂无法提取原文内容' }}</span>
                </div>
              </div>
            </div>
            
            <div class="diff-pane">
              <div class="pane-header">
                <span class="pane-tab">投标方 B 原文</span>
                <span class="pane-actions">...</span>
              </div>
              <div class="pane-body df-bg-added">
                <div class="line-numbers">
                  <span v-for="i in 5" :key="i">{{ i }}</span>
                </div>
                <div class="code-content">
                  <span class="diff-highlight diff-add">{{ item.diffPayload?.contentB || '暂无法提取原文内容' }}</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="empty-block">未发现文本雷同（查重率在正常阈值以内）。</div>
    </section>
  </div>
  <div v-else class="empty-state">暂无比对数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { EvidenceChain } from '../../types/report.types';

interface Props {
  evidences?: EvidenceChain[];
}

const props = defineProps<Props>();

const priceEvidences = computed(() => props.evidences?.filter((e) => e.type === 'price_diff') || []);
const textEvidences = computed(() => props.evidences?.filter((e) => e.type === 'text_diff' || e.type === 'structure_diff') || []);

function badgeClass(verdict?: string) {
  const value = verdict || '';
  if (value.includes('exact') || value.includes('高度') || value.includes('异常') || value.includes('雷同')) return 'danger';
  if (value.includes('fuzzy') || value.includes('warning') || value.includes('提示') || value.includes('相似') || value.includes('中度')) return 'warning';
  return 'safe';
}

function verdictText(verdict?: string) {
  if (verdict === 'exact_match') return '极度异常';
  if (verdict === 'fuzzy_match') return '疑似异常';
  if (verdict === 'anomaly_gap') return '价差异常';
  if (verdict === 'safe') return '正常';
  if (verdict === 'warning') return '关注';
  return verdict || '未知';
}
</script>

<style scoped>
.detail-comparison {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section {
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
}

.section-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}

.header-icon {
  font-size: 24px;
  background: #f1f5f9;
  width: 48px;
  height: 48px;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 12px;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}
.section-header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.empty-block,
.empty-state {
  padding: 30px;
  border-radius: 12px;
  background: #f8fafc;
  text-align: center;
  color: #94a3b8;
  border: 1px dashed #cbd5e1;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}
.badge.danger { background: #fee2e2; color: #b91c1c; }
.badge.warning { background: #fef3c7; color: #b45309; }
.badge.safe { background: #dbeafe; color: #1d4ed8; }

/* Custom Table */
.custom-table {
  border-radius: 12px;
  overflow: hidden;
}

/* Diff Section 重构 */
.highlight-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.diff-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 20px -6px rgba(0,0,0,0.05);
}

.diff-verdict-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  border-top-width: 4px;
  border-top-style: solid;
}
.border-danger { border-top-color: #ef4444; }
.border-warning { border-top-color: #f59e0b; }
.border-safe { border-top-color: #3b82f6; }

.text-danger { color: #ef4444; }
.text-warning { color: #f59e0b; }
.text-safe { color: #3b82f6; }

.verdict-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.verdict-icon { font-size: 24px; }
.verdict-info { display: flex; flex-direction: column; }
.verdict-cat { font-weight: 800; font-size: 15px; color: #0f172a; }
.verdict-analysis { font-size: 13px; color: #475569; margin: 4px 0 0; max-width: 500px;}

.verdict-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.similarity-score { font-size: 24px; font-weight: 900; }
.similarity-text { font-size: 12px; color: #64748b; text-transform: uppercase; letter-spacing: 1px; }

/* 代码编辑器风格容器 */
.diff-editor-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
}

.diff-pane {
  display: flex;
  flex-direction: column;
}
.diff-pane:first-child { border-right: 1px solid #e2e8f0; }

.pane-header {
  background: #f1f5f9;
  padding: 8px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
}
.pane-tab {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  background: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  border: 1px solid #cbd5e1;
}
.pane-actions { color: #94a3b8; font-weight: bold; letter-spacing: 2px; }

.pane-body {
  display: flex;
  font-family: 'JetBrains Mono', 'Courier New', Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
  min-height: 120px;
}

.line-numbers {
  display: flex;
  flex-direction: column;
  padding: 12px 10px;
  background: #f8fafc;
  color: #94a3b8;
  border-right: 1px solid #e2e8f0;
  text-align: right;
  user-select: none;
}

.code-content {
  padding: 12px 16px;
  flex: 1;
  white-space: pre-wrap;
  color: #1e293b;
}

.diff-highlight {
  border-radius: 3px;
  padding: 1px 3px;
}

/* GitHub Style Diff Colors */
.df-bg-removed { background: #fff5f5; }
.df-bg-added { background: #f0fdf4; }

.diff-remove {
  background: rgba(254, 202, 202, 0.6);
  text-decoration: line-through;
  text-decoration-color: rgba(239, 68, 68, 0.5);
}
.diff-add {
  background: rgba(187, 247, 208, 0.7);
}

@media (max-width: 900px) {
  .diff-editor-container {
    grid-template-columns: 1fr;
  }
  .diff-pane:first-child {
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }
}
</style>
