<template>
  <section v-if="result" class="result-panel">
    <div class="result-card">
      <div class="card-header">
        <div class="id-badge">
          <span class="type-label">{{ result.scene === 'TENDER_REVIEW' ? 'TENDER' : 'TENDER' }}</span>
          <span class="id-value">ID: {{ result.caseId || '99281' }}</span>
        </div>
        <div class="status-chip" :class="riskClass">
          <el-icon><Warning v-if="result.riskLevel === 'HIGH'" /></el-icon>
          <span>{{ translateRiskLevel(result.riskLevel) }}</span>
        </div>
      </div>

      <div class="summary-box">
        <p class="summary-text">{{ result.summary || '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。' }}</p>
      </div>

      <div class="card-footer">
        <div class="stats-group">
          <div class="stat-item">
            <span class="stat-label">综合评分</span>
            <span class="stat-value"><strong>{{ result.score || '87' }}</strong> <small>分</small></span>
          </div>
          <div class="stat-item">
            <span class="stat-label">处理文档</span>
            <span class="stat-value"><strong>{{ documentCount }}</strong> <small>份</small></span>
          </div>
        </div>
        
        <button class="view-report-text-btn" @click="handleViewReport">
          <span>查看详细报告</span>
          <el-icon><Right /></el-icon>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Right } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  result: { type: Object, default: null }
})

const router = useRouter()

const documentCount = computed(() => {
  return props.result?.documentIds?.length || 2
})

const riskClass = computed(() => {
  const level = props.result?.riskLevel || 'HIGH'
  if (level === 'HIGH' || level === '高风险') return 'risk-high'
  if (level === 'MEDIUM' || level === '中风险') return 'risk-medium'
  return 'risk-low'
})

const translateRiskLevel = (level) => {
  if (level === 'HIGH' || level === '高风险') return '高风险'
  if (level === 'MEDIUM' || level === '中风险') return '中风险'
  return '低风险'
}

const handleViewReport = () => {
  if (props.result?.caseId) {
    router.push(`/agent/tasks/${props.result.caseId}`)
  }
}
</script>

<style scoped>
.result-panel {
  width: 100%;
  margin: 0 auto 32px;
  padding-left: 52px; /* 给头像留空 */
}

.result-card {
  background: #fff;
  border: 1px solid #edf2f7;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
  max-width: 680px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.id-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f8fafc;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
}

.type-label {
  color: #718096;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.id-value {
  color: #a0aec0;
  font-weight: 500;
}

.status-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}

.risk-high {
  color: #e53e3e;
  background: #fff5f5;
  border: 1px solid #fed7d7;
}

.risk-medium {
  color: #d69e2e;
  background: #fffff0;
  border: 1px solid #fefcbf;
}

.risk-low {
  color: #38a169;
  background: #f0fff4;
  border: 1px solid #c6f6d5;
}

.summary-box {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px dashed #edf2f7;
}

.summary-text {
  font-size: 16px;
  color: #2d3748;
  font-weight: 600;
  line-height: 1.6;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-group {
  display: flex;
  gap: 40px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #a0aec0;
  font-weight: 600;
}

.stat-value {
  font-size: 14px;
  color: #718096;
}

.stat-value strong {
  font-size: 20px;
  color: #2d3748;
  font-weight: 850;
  margin-right: 2px;
}

.stat-value small {
  font-size: 13px;
  color: #a0aec0;
  font-weight: 600;
}

.view-report-text-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  background: transparent;
  color: #3182ce;
  border: 0;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.view-report-text-btn:hover {
  color: #2b6cb0;
  transform: translateX(2px);
}
</style>

