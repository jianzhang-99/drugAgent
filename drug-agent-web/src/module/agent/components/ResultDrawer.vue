<template>
  <t-drawer
    v-model:visible="visible"
    :header="drawerTitle"
    size="560px"
    placement="right"
    @close="handleClose"
  >
    <div v-if="result" class="result-detail">
      <div class="detail-section">
        <h4 class="section-title">风险等级</h4>
        <div class="hero-panel">
          <div class="risk-badge" :class="`risk-${result.riskLevel || 'unknown'}`">
            {{ riskLabel }}
          </div>
          <div class="summary-pane">{{ result.summary || '暂无摘要' }}</div>
        </div>
      </div>

      <div v-if="result.score !== undefined" class="detail-section">
        <h4 class="section-title">风险评分</h4>
        <div class="score-display">
          <t-progress
            :percentage="result.score"
            :color="progressColor"
            :show-text="false"
          />
          <span class="score-number">{{ result.score }}</span>
        </div>
      </div>

      <div v-if="result.steps && result.steps.length > 0" class="detail-section">
        <h4 class="section-title">审查步骤</h4>
        <t-steps :current="result.steps.length" layout="vertical">
          <t-step-item
            v-for="(step, index) in result.steps"
            :key="index"
            :title="`步骤 ${index + 1}`"
            :content="step"
          />
        </t-steps>
      </div>

      <div v-if="result.evidenceList && result.evidenceList.length > 0" class="detail-section">
        <h4 class="section-title">证据列表</h4>
        <div class="evidence-list">
          <div
            v-for="(evidence, index) in result.evidenceList"
            :key="index"
            class="evidence-item"
          >
            <div class="evidence-type">
              <t-tag>{{ evidence.type || '证据' }}</t-tag>
            </div>
            <div class="evidence-content">{{ evidence.content }}</div>
            <div v-if="evidence.similarity" class="evidence-similarity">
              相似度: {{ (evidence.similarity * 100).toFixed(1) }}%
            </div>
          </div>
        </div>
      </div>

      <div v-if="result.report" class="detail-section">
        <h4 class="section-title">审查报告</h4>
        <div class="report-content">
          <template v-if="result.report.overview">
            <h5>概述</h5>
            <p>{{ result.report.overview }}</p>
          </template>
          <template v-if="result.report.findings">
            <h5>发现</h5>
            <div
              v-for="(finding, index) in result.report.findings"
              :key="index"
              class="finding-item"
            >
              <span class="finding-title">{{ finding.title || `发现 ${index + 1}` }}</span>
              <span class="finding-desc">{{ finding.description }}</span>
            </div>
          </template>
          <template v-if="result.report.conclusion">
            <h5>结论</h5>
            <p>{{ result.report.conclusion }}</p>
          </template>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <t-button theme="default" @click="handleClose">关闭</t-button>
      </div>
    </template>
  </t-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';

const store = useAgentStore();

const visible = computed({
  get: () => !!store.currentResult,
  set: (val) => {
    if (!val) {
      store.setCurrentResult(null);
    }
  },
});

const drawerTitle = computed(() => {
  return store.currentResult?.scene
    ? `${store.currentResult.scene} - 审查结果`
    : '审查结果详情';
});

const result = computed(() => store.currentResult);

const riskLabel = computed(() => {
  const map: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '安全',
    unknown: '未知',
  };
  return map[result.value?.riskLevel || 'unknown'] || '未知';
});

const progressColor = computed(() => {
  const score = result.value?.score || 0;
  if (score >= 80) return '#cf1322';
  if (score >= 60) return '#d46b08';
  return '#389e0d';
});

function handleClose() {
  store.setCurrentResult(null);
}
</script>

<style scoped>
.result-detail {
  padding: 0 8px 24px;
}

.detail-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e7e7e7;
}

.risk-badge {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
}

.hero-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(247, 251, 250, 0.9), rgba(241, 247, 248, 0.9));
  border: 1px solid rgba(19, 49, 59, 0.08);
}

.summary-pane {
  font-size: 14px;
  line-height: 1.75;
  color: #4d6672;
}

.risk-high {
  background: #fff2f0;
  color: #cf1322;
}

.risk-medium {
  background: #fff7e6;
  color: #d46b08;
}

.risk-low {
  background: #f9f0ff;
  color: #722ed1;
}

.risk-safe {
  background: #f6ffed;
  color: #389e0d;
}

.risk-unknown {
  background: #f5f5f5;
  color: #666;
}

.score-display {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(19, 49, 59, 0.08);
}

.score-number {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  min-width: 50px;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.evidence-item {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 14px;
}

.evidence-type {
  margin-bottom: 8px;
}

.evidence-content {
  font-size: 14px;
  color: #333;
  line-height: 1.5;
}

.evidence-similarity {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.report-content h5 {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 8px;
  color: #333;
}

.report-content p {
  font-size: 14px;
  line-height: 1.6;
  color: #666;
  margin: 0 0 16px;
}

.finding-item {
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 12px;
  margin-bottom: 8px;
}

.finding-title {
  font-weight: 500;
  color: #333;
  margin-right: 8px;
}

.finding-desc {
  color: #666;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
