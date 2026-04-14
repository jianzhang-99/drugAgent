<template>
  <div v-if="data" class="detail-comparison">
    <section class="section">
      <div class="section-header">
        <div>
          <h3>报价对比表</h3>
          <p>适合查看分项报价、固定价差和异常梯度。</p>
        </div>
      </div>
      <el-table
        v-if="data.priceComparison?.rows?.length"
        :data="data.priceComparison.rows"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="item" label="项目" min-width="180" />
        <el-table-column prop="docA" label="文档 A" min-width="160" />
        <el-table-column prop="docB" label="文档 B" min-width="160" />
        <el-table-column prop="diff" label="差异/说明" min-width="160" />
        <el-table-column prop="verdict" label="判定" min-width="120">
          <template #default="{ row }">
            <span class="badge" :class="badgeClass(row.verdict)">{{ row.verdict }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-block">当前未抽取到结构化报价对比项。</div>
    </section>

    <section class="section">
      <div class="section-header">
        <div>
          <h3>团队对比表</h3>
          <p>适合查看项目经理、技术负责人等关键岗位是否重合。</p>
        </div>
      </div>
      <el-table
        v-if="data.teamComparison?.rows?.length"
        :data="data.teamComparison.rows"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="role" label="角色/字段" min-width="200" />
        <el-table-column prop="docA" label="文档 A" min-width="180" />
        <el-table-column prop="docB" label="文档 B" min-width="180" />
        <el-table-column prop="verdict" label="判定" min-width="120">
          <template #default="{ row }">
            <span class="badge" :class="badgeClass(row.verdict)">{{ row.verdict }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty-block">当前未抽取到结构化团队对比项。</div>
    </section>

    <section class="section">
      <div class="section-header">
        <div>
          <h3>文本高亮对比</h3>
          <p>仅展示高相似片段和关键判定说明，不平铺整篇原文。</p>
        </div>
      </div>
      <div v-if="data.textHighlights?.length" class="highlight-list">
        <article
          v-for="(item, index) in data.textHighlights"
          :key="`${item.category}-${index}`"
          class="highlight-card"
        >
          <div class="highlight-head">
            <div>
              <div class="highlight-category">{{ item.category }}</div>
              <div class="highlight-meta">相似度：{{ item.similarity }}</div>
            </div>
            <span class="badge" :class="badgeClass(item.verdict)">{{ item.verdict }}</span>
          </div>
          <div class="highlight-grid">
            <div class="highlight-text">
              <div class="text-label">原文 A</div>
              <div class="text-body">{{ item.textA || '暂无片段' }}</div>
            </div>
            <div class="highlight-text">
              <div class="text-label">原文 B</div>
              <div class="text-body">{{ item.textB || '暂无片段' }}</div>
            </div>
          </div>
          <div class="analysis-box">
            <span class="analysis-label">判定说明</span>
            <span class="analysis-text">{{ item.analysis || '建议结合上下文继续人工复核。' }}</span>
          </div>
        </article>
      </div>
      <div v-else class="empty-block">当前未抽取到可展示的高相似文本片段。</div>
    </section>
  </div>

  <div v-else class="empty-state">暂无详细比对数据</div>
</template>

<script setup lang="ts">
import type { Page4DetailComparison } from '../../types/report.types';

interface Props {
  data?: Page4DetailComparison;
}

defineProps<Props>();

function badgeClass(verdict?: string) {
  const value = verdict || '';
  if (value.includes('高度') || value.includes('异常') || value.includes('相似')) return 'danger';
  if (value.includes('提示') || value.includes('中度')) return 'warning';
  return 'safe';
}
</script>

<style scoped>
.detail-comparison {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section {
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  padding: 20px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.section-header {
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.section-header p {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.6;
}

.empty-block,
.empty-state {
  padding: 26px;
  border-radius: 16px;
  background: #f8fafc;
  text-align: center;
  color: #94a3b8;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.badge.danger {
  background: #fee2e2;
  color: #b91c1c;
}

.badge.warning {
  background: #fef3c7;
  color: #b45309;
}

.badge.safe {
  background: #dbeafe;
  color: #1d4ed8;
}

:deep(.el-table) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f8fafc !important;
  color: #475569;
  font-weight: 700;
}

.highlight-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.highlight-card {
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 16px;
}

.highlight-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.highlight-category {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.highlight-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.highlight-text {
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.text-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.text-body {
  color: #334155;
  line-height: 1.7;
  white-space: pre-wrap;
}

.analysis-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
  padding: 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fffaf0, #ffffff);
  border: 1px solid #fde68a;
}

.analysis-label {
  font-size: 12px;
  font-weight: 700;
  color: #92400e;
}

.analysis-text {
  color: #334155;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .section {
    padding: 16px;
  }

  .highlight-head,
  .highlight-grid {
    grid-template-columns: 1fr;
    display: grid;
  }

  .highlight-head {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
