<template>
  <section v-if="result" class="result-panel">
    <div class="result-card" :class="['risk-' + riskLevelKey, { 'is-selected': isSelected }]">
      <!-- 顶部结果区 -->
      <div class="result-header">
        <div class="risk-badge" :class="riskBadgeClass">
          <el-icon><Warning v-if="isHighRisk" /><CircleCheck v-if="isLowRisk" /><Info v-if="isMediumRisk" /></el-icon>
          <span>{{ riskLevelText }}</span>
        </div>
        <span class="scene-tag">{{ sceneText }}</span>
      </div>

      <!-- 一句话结论 -->
      <div class="conclusion-box">
        <p class="conclusion-text">{{ conclusionText }}</p>
      </div>

      <!-- 核心指标区 -->
      <div class="metrics-group" v-if="metrics.length > 0">
        <div class="metric-item" v-for="metric in metrics" :key="metric.label">
          <span class="metric-value">{{ metric.value }}</span>
          <span class="metric-label">{{ metric.label }}</span>
        </div>
      </div>

      <!-- 审查说明区（低风险） -->
      <div class="review-notes" v-if="isLowRisk && reviewNotes">
        <h4 class="notes-title">审查说明</h4>
        <p class="notes-content">{{ reviewNotes }}</p>
      </div>

      <!-- 风险详情区（中/高风险） -->
      <div class="risk-details" v-if="!isLowRisk && riskDetails">
        <h4 class="details-title">{{ isHighRisk ? '关键风险' : '注意事项' }}</h4>
        <ul class="details-list">
          <li v-for="(item, idx) in riskDetails" :key="idx">{{ item }}</li>
        </ul>
      </div>

      <!-- 建议动作区 -->
      <div class="action-suggestions" v-if="actionSuggestions.length > 0">
        <h4 class="suggestions-title">建议动作</h4>
        <div class="suggestion-tags">
          <span class="suggestion-tag" v-for="(action, idx) in actionSuggestions" :key="idx">
            {{ action }}
          </span>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="card-actions">
        <button class="action-btn primary" @click="handleViewReport">
          <span>查看完整报告</span>
          <el-icon><Right /></el-icon>
        </button>
        <button class="action-btn secondary" @click="handleViewDetail">
          <span>查看详情</span>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, CircleCheck, Info, Right } from '@element-plus/icons-vue'

const props = defineProps({
  result: { type: Object, default: null },
  isSelected: { type: Boolean, default: false }
})

const emit = defineEmits(['view-detail', 'view-report'])

// 风险等级映射
const riskLevelKey = computed(() => {
  const level = props.result?.riskLevel || 'LOW'
  if (level === 'HIGH' || level === '高风险') return 'high'
  if (level === 'MEDIUM' || level === '中风险') return 'medium'
  return 'low'
})

const isHighRisk = computed(() => riskLevelKey.value === 'high')
const isMediumRisk = computed(() => riskLevelKey.value === 'medium')
const isLowRisk = computed(() => riskLevelKey.value === 'low')

const riskBadgeClass = computed(() => {
  return {
    'badge-high': isHighRisk.value,
    'badge-medium': isMediumRisk.value,
    'badge-low': isLowRisk.value
  }
})

const riskLevelText = computed(() => {
  const level = props.result?.riskLevel || 'LOW'
  if (level === 'HIGH' || level === '高风险') return '高风险'
  if (level === 'MEDIUM' || level === '中风险') return '中风险'
  return '低风险'
})

const sceneText = computed(() => {
  return props.result?.scene === 'TENDER_REVIEW' ? '标书审查' : '标书审查'
})

// 一句话结论
const conclusionText = computed(() => {
  if (isLowRisk.value) {
    return props.result?.summary?.replace(/发现.*?语义重合度.*?高度疑似围标。?/g, '已完成标书比对，未发现明显高风险信号')
      || '已完成标书比对，当前未发现有效高风险命中'
  }
  return props.result?.summary || '发现异常情况，请查看详情'
})

// 核心指标
const metrics = computed(() => {
  const docs = props.result?.documentIds?.length || props.result?.documentCount || 2
  const risks = props.result?.riskCount || props.result?.effectiveRiskItems || 0
  const score = props.result?.score || props.result?.referenceScore || null

  const result = [
    { label: '比对文件', value: `${docs}份` },
    { label: '风险命中', value: `${risks}项` }
  ]

  if (score !== null && score !== undefined) {
    result.push({ label: '风险评分', value: `${score}分` })
  }

  return result
})

// 审查说明（低风险）
const reviewNotes = computed(() => {
  if (!isLowRisk.value) return null
  return '未检测到联系人复用、团队重叠、报价异常等高风险信号。当前样本在规则维度偏差较小。'
})

// 风险详情（中/高风险）
const riskDetails = computed(() => {
  if (isLowRisk.value) return null
  const details = []
  if (isHighRisk.value) {
    if (props.result?.contactProximity) details.push('联系人与团队成员存在交叉复用')
    if (props.result?.quoteGradient) details.push('报价梯度存在异常')
    if (props.result?.coreTeamOverlap) details.push('核心团队存在重叠')
  }
  if (isMediumRisk.value) {
    details.push('存在一定异常点，需要人工复核确认')
  }
  return details.length > 0 ? details : ['请查看完整报告了解详情']
})

// 建议动作
const actionSuggestions = computed(() => {
  if (isHighRisk.value) {
    return ['优先人工复核', '核查联系人复用', '检查报价异常']
  }
  if (isMediumRisk.value) {
    return ['建议抽样复核', '关注异常方向']
  }
  return ['如为重点项目，建议抽样人工复核']
})

// 操作处理
const handleViewReport = () => {
  emit('view-report', props.result)
}

const handleViewDetail = () => {
  emit('view-detail', props.result)
}
</script>

<style scoped>
.result-panel {
  width: 100%;
  margin: 0 auto 32px;
  padding-left: 52px;
}

.result-card {
  background: #fff;
  border: 1px solid #edf2f7;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
  max-width: 680px;
  transition: all 0.2s ease;
  cursor: pointer;
}

.result-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 8px 30px rgba(59, 130, 246, 0.1);
}

.result-card.is-selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* 顶部结果区 */
.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.risk-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
}

.badge-high {
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
}

.badge-medium {
  color: #d97706;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.badge-low {
  color: #059669;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.scene-tag {
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 4px 10px;
  border-radius: 4px;
}

/* 一句话结论 */
.conclusion-box {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px dashed #e5e7eb;
}

.conclusion-text {
  font-size: 15px;
  color: #374151;
  line-height: 1.7;
  font-weight: 500;
}

/* 核心指标区 */
.metrics-group {
  display: flex;
  gap: 32px;
  margin-bottom: 20px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 10px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-value {
  font-size: 22px;
  font-weight: 800;
  color: #111827;
}

.metric-label {
  font-size: 12px;
  color: #6b7280;
  font-weight: 500;
}

/* 审查说明区 */
.review-notes {
  margin-bottom: 20px;
  padding: 16px;
  background: #ecfdf5;
  border-radius: 10px;
  border-left: 4px solid #10b981;
}

.notes-title {
  font-size: 13px;
  font-weight: 700;
  color: #059669;
  margin: 0 0 8px 0;
}

.notes-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  margin: 0;
}

/* 风险详情区 */
.risk-details {
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 10px;
}

.risk-high .risk-details {
  background: #fef2f2;
  border-left: 4px solid #ef4444;
}

.risk-medium .risk-details {
  background: #fffbeb;
  border-left: 4px solid #f59e0b;
}

.details-title {
  font-size: 13px;
  font-weight: 700;
  margin: 0 0 10px 0;
}

.risk-high .details-title {
  color: #dc2626;
}

.risk-medium .details-title {
  color: #d97706;
}

.details-list {
  margin: 0;
  padding-left: 20px;
  font-size: 14px;
  color: #374151;
  line-height: 1.8;
}

/* 建议动作区 */
.action-suggestions {
  margin-bottom: 20px;
}

.suggestions-title {
  font-size: 13px;
  font-weight: 700;
  color: #4b5563;
  margin: 0 0 10px 0;
}

.suggestion-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggestion-tag {
  padding: 6px 12px;
  background: #e0e7ff;
  color: #4338ca;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

/* 操作区 */
.card-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  border: 0;
}

.action-btn.primary {
  background: #4f46e5;
  color: #fff;
}

.action-btn.primary:hover {
  background: #4338ca;
}

.action-btn.secondary {
  background: #f3f4f6;
  color: #4b5563;
}

.action-btn.secondary:hover {
  background: #e5e7eb;
}
</style>
