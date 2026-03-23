<template>
  <section class="evidence-panel">
    <div class="panel-head">
      <h2>命中证据</h2>
      <span class="panel-tip">触发规则及解释说明</span>
    </div>

    <div v-if="evidences.length > 0" class="evidence-list">
      <article v-for="(evidence, index) in evidences" :key="index" class="evidence-card">
        <div class="evidence-header">
          <div class="evidence-rule">
            <svg class="evidence-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4" />
              <circle cx="12" cy="12" r="10" />
            </svg>
            <strong>{{ evidence.ruleName }}</strong>
          </div>
          <span class="risk-level" :class="getRiskLevelClass(evidence.level)">
            风险{{ getRiskLevelText(evidence.level) }}
          </span>
        </div>

        <div class="evidence-content">
          <div class="evidence-section matched-text-section">
            <h4>匹配片段</h4>
            <p class="matched-text">"{{ evidence.matchedText || evidence.matchDetails?.[0]?.matchedText || '未提供匹配文本' }}"</p>
          </div>

          <div class="evidence-section">
            <h4>解释说明</h4>
            <p>{{ evidence.explanation || '该规则用于检测相关风险项。' }}</p>
          </div>

          <div v-if="evidence.sourceDocuments?.length" class="evidence-section source-docs">
            <h4>来源文档</h4>
            <div class="source-doc-list">
              <span v-for="doc in evidence.sourceDocuments" :key="doc" class="source-doc-tag">{{ doc }}</span>
            </div>
          </div>

          <div v-if="evidence.matchDetails?.length" class="evidence-section">
            <h4>匹配详情</h4>
            <div class="match-list">
              <div v-for="(match, mIndex) in evidence.matchDetails" :key="mIndex" class="match-item">
                <span class="match-doc">{{ match.documentName || match.docId || '文档' }}</span>
                <span class="match-text">{{ match.matchedText || match.content || '未提供匹配文本' }}</span>
              </div>
            </div>
          </div>
        </div>
      </article>
    </div>

    <div v-else class="empty-evidence">
      <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M9 12l2 2 4-4" />
        <circle cx="12" cy="12" r="10" />
      </svg>
      <p>暂无命中证据</p>
      <span>完成文档解析后，将在此展示触发的规则及解释说明。</span>
    </div>
  </section>
</template>

<script setup>
defineProps({
  evidences: {
    type: Array,
    default: () => []
  }
})

const getRiskLevelClass = (level) => {
  const levelMap = {
    HIGH: 'risk-high',
    MEDIUM: 'risk-medium',
    LOW: 'risk-low',
    PENDING: 'risk-pending'
  }
  return levelMap[level] || 'risk-pending'
}

const getRiskLevelText = (level) => {
  const textMap = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
    PENDING: '待判定'
  }
  return textMap[level] || '待判定'
}
</script>

<style scoped>
.evidence-panel {
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

.panel-tip {
  color: var(--text-sub);
  font-size: 13px;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.evidence-card {
  border: 1px solid var(--border-light);
  border-radius: 16px;
  padding: 18px;
  background: #fbfcff;
}

.evidence-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.evidence-rule {
  display: flex;
  align-items: center;
  gap: 10px;
}

.evidence-icon {
  width: 20px;
  height: 20px;
  color: var(--primary-color);
  flex-shrink: 0;
}

.evidence-rule strong {
  font-size: 16px;
  color: var(--text-main);
}

.risk-level {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.risk-high {
  background: #fef2f2;
  color: #dc2626;
}

.risk-medium {
  background: #fff7ed;
  color: #b45309;
}

.risk-low {
  background: #ecfdf3;
  color: #15803d;
}

.risk-pending {
  background: #f3f4f6;
  color: #6b7280;
}

.evidence-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.evidence-section h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.evidence-section p {
  margin: 0;
  color: var(--text-main);
  line-height: 1.7;
}

.matched-text-section .matched-text {
  background: #fef9c3;
  border: 1px solid #fef08a;
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 14px;
  color: #854d0e;
  line-height: 1.6;
  margin-top: 8px;
}

.source-docs h4 {
  margin: 0 0 8px;
}

.source-doc-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.source-doc-tag {
  display: inline-flex;
  padding: 4px 10px;
  background: #eef2ff;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #4f46e5;
}

.match-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.match-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: 10px;
}

.match-doc {
  font-size: 12px;
  font-weight: 700;
  color: var(--primary-color);
}

.match-text {
  font-size: 14px;
  color: var(--text-main);
  line-height: 1.5;
}

.empty-evidence {
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

.empty-evidence p {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.empty-evidence span {
  font-size: 13px;
  color: var(--text-sub);
}
</style>
