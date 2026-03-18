<template>
  <section v-if="result" class="result-panel">
    <div class="result-card">
      <div class="card-header">
        <div class="id-badge">
          <span class="type-label">{{ result.scene === 'TENDER_REVIEW' ? 'TENDER' : 'TASK' }}</span>
          <span class="id-value">ID: {{ result.caseId?.slice(0, 8) || '99281' }}</span>
        </div>
        <div class="status-chip" :class="riskClass">
          <el-icon><Warning v-if="result.riskLevel === 'HIGH'" /></el-icon>
          <span>{{ translateRiskLevel(result.riskLevel) }}</span>
        </div>
      </div>

      <p class="summary-text">{{ result.summary || '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。' }}</p>

      <div class="card-footer">
        <div class="stats-group">
          <div class="stat-item">
            <span class="stat-label">综合评分</span>
            <span class="stat-value">{{ result.score || '-' }} <small>分</small></span>
          </div>
          <div class="stat-item">
            <span class="stat-label">处理文档</span>
            <span class="stat-value">{{ documentCount }} <small>份</small></span>
          </div>
        </div>
        
        <button class="view-report-btn" @click="handleViewReport">
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
  return props.result?.documentIds?.length || props.result?.report?.overview?.documentCount || 0
})

const riskClass = computed(() => {
  const level = props.result?.riskLevel
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
  width: min(860px, calc(100vw - 64px));
  margin: 16px auto;
}

.result-card {
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.04);
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
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
}

.type-label {
  color: #64748b;
  letter-spacing: 0.5px;
}

.id-value {
  color: #94a3b8;
}

.status-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
}

.risk-high {
  color: #ef4444;
  background: #fef2f2;
}

.risk-medium {
  color: #f59e0b;
  background: #fffbeb;
}

.risk-low {
  color: #10b981;
  background: #f0fdf4;
}

.summary-text {
  font-size: 16px;
  color: #1e293b;
  line-height: 1.6;
  margin-bottom: 24px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.stats-group {
  display: flex;
  gap: 32px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.stat-value small {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
  margin-left: 2px;
}

.view-report-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  height: 40px;
  background: #eff6ff;
  color: #2563eb;
  border: 0;
  border-radius: 999px;
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.view-report-btn:hover {
  background: #dbeafe;
}
</style>

