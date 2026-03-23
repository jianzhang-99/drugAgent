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
          <span class="match-badge">命中 {{ evidence.matchCount || 1 }} 次</span>
        </div>

        <div class="evidence-body">
          <div class="evidence-section">
            <h4>规则解释</h4>
            <p>{{ evidence.explanation || '该规则用于检测相关风险项。' }}</p>
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

.match-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: #eef2ff;
  color: #4f46e5;
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
