<template>
  <div v-if="data" class="risk-overview">
    <div class="overview-tip">
      风险总览按“业务风险类型”而不是“规则命中列表”组织，帮助快速判断哪些维度需要人工复核。
    </div>

    <div class="risk-grid">
      <article
        v-for="category in orderedCategories"
        :key="category.type"
        class="category-card"
        :class="`card-${normalizeLevel(category.level)}`"
      >
        <div class="card-head">
          <div>
            <div class="category-title">{{ category.categoryName }}</div>
            <div class="category-meta">
              <span>{{ levelLabel(category.level) }}</span>
              <span>命中 {{ category.hitCount }} 条</span>
            </div>
          </div>
          <span class="review-badge" :class="{ active: category.needHumanReview }">
            {{ category.needHumanReview ? '建议人工复核' : '暂不升级处理' }}
          </span>
        </div>

        <div class="card-block">
          <div class="block-label">风险解释</div>
          <p>{{ category.explanation }}</p>
        </div>

        <div class="card-block">
          <div class="block-label">代表性证据</div>
          <p>{{ category.representativeEvidence }}</p>
        </div>

        <div class="card-block action-block">
          <div class="block-label">建议动作</div>
          <p>{{ category.action }}</p>
        </div>
      </article>
    </div>
  </div>

  <div v-else class="empty-state">暂无风险数据</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { Page2RiskOverview } from '../../types/report.types';

interface Props {
  data?: Page2RiskOverview;
}

const props = defineProps<Props>();

const typeOrder = ['pricing', 'team', 'text_similarity', 'template', 'auxiliary'];

const orderedCategories = computed(() => {
  const categories = props.data?.riskCategories || [];
  return [...categories].sort(
    (a, b) => typeOrder.indexOf(a.type) - typeOrder.indexOf(b.type)
  );
});

function normalizeLevel(level?: string): string {
  if (!level) return 'safe';
  const value = level.toLowerCase();
  if (value.includes('high') || value.includes('高')) return 'high';
  if (value.includes('medium') || value.includes('中')) return 'medium';
  if (value.includes('low') || value.includes('低')) return 'low';
  return 'safe';
}

function levelLabel(level?: string): string {
  return {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '未见明显异常',
  }[normalizeLevel(level)] || '未见明显异常';
}
</script>

<style scoped>
.risk-overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.overview-tip {
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
  border: 1px solid #dbeafe;
  color: #334155;
  line-height: 1.7;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.category-card {
  border-radius: 18px;
  border: 1px solid #e2e8f0;
  padding: 18px;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.05);
}

.card-high {
  border-color: #fecaca;
}

.card-medium {
  border-color: #fde68a;
}

.card-low,
.card-safe {
  border-color: #bfdbfe;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.category-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.category-meta {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.review-badge {
  display: inline-flex;
  align-items: center;
  height: fit-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.review-badge.active {
  background: #fee2e2;
  color: #b91c1c;
}

.card-block {
  padding: 14px 0;
  border-top: 1px dashed #dbe3ee;
}

.card-block:first-of-type {
  border-top: none;
  padding-top: 0;
}

.block-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.card-block p {
  margin: 0;
  color: #334155;
  line-height: 1.7;
}

.action-block p {
  color: #0f172a;
  font-weight: 600;
}

.empty-state {
  padding: 32px;
  text-align: center;
  color: #94a3b8;
}

@media (max-width: 860px) {
  .risk-grid {
    grid-template-columns: 1fr;
  }

  .card-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
