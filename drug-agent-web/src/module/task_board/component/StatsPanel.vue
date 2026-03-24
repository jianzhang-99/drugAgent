<template>
  <div class="stats-panel grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
    <!-- 总任务数 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-slate-200">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-xs text-slate-500 font-medium mb-1">任务总数</p>
          <p class="text-2xl font-bold text-slate-800">{{ statistics?.totalCount || 0 }}</p>
        </div>
        <div class="w-10 h-10 bg-indigo-50 rounded-xl flex items-center justify-center">
          <LayoutGrid class="w-5 h-5 text-indigo-600" />
        </div>
      </div>
      <div class="mt-2 flex items-center gap-1 text-xs text-slate-500">
        <span class="text-emerald-600">+{{ statistics?.todayNewCount || 0 }}</span>
        <span>今日新增</span>
      </div>
    </div>

    <!-- 待处理 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-slate-200">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-xs text-slate-500 font-medium mb-1">待处理</p>
          <p class="text-2xl font-bold text-slate-800">{{ pendingCount }}</p>
        </div>
        <div class="w-10 h-10 bg-amber-50 rounded-xl flex items-center justify-center">
          <Clock class="w-5 h-5 text-amber-600" />
        </div>
      </div>
      <div class="mt-2">
        <div class="h-1.5 bg-slate-100 rounded-full overflow-hidden">
          <div
            class="h-full bg-amber-500 rounded-full transition-all duration-500"
            :style="{ width: pendingRate + '%' }"
          />
        </div>
        <p class="mt-1 text-xs text-slate-500">{{ pendingRate }}% 待处理</p>
      </div>
    </div>

    <!-- 执行中 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-slate-200">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-xs text-slate-500 font-medium mb-1">执行中</p>
          <p class="text-2xl font-bold text-blue-600">{{ statistics?.runningCount || 0 }}</p>
        </div>
        <div class="w-10 h-10 bg-blue-50 rounded-xl flex items-center justify-center relative">
          <Loader class="w-5 h-5 text-blue-600 animate-spin" />
        </div>
      </div>
      <div class="mt-2 flex items-center gap-1 text-xs text-blue-600">
        <span>{{ statistics?.parsingCount || 0 }}</span>
        <span class="text-slate-400">解析中</span>
      </div>
    </div>

    <!-- 高风险 -->
    <div class="bg-white rounded-xl p-4 shadow-sm border border-slate-200 cursor-pointer hover:shadow-md transition-shadow"
         :class="{ 'ring-2 ring-red-200': hasHighRisk }"
         @click="$emit('filter-high-risk')"
    >
      <div class="flex items-center justify-between">
        <div>
          <p class="text-xs text-slate-500 font-medium mb-1">高风险</p>
          <p class="text-2xl font-bold" :class="hasHighRisk ? 'text-red-600' : 'text-slate-800'">
            {{ statistics?.highRiskCount || 0 }}
          </p>
        </div>
        <div :class="hasHighRisk ? 'bg-red-100' : 'bg-slate-100'" class="w-10 h-10 rounded-xl flex items-center justify-center">
          <AlertTriangle :class="hasHighRisk ? 'text-red-600' : 'text-slate-600'" class="w-5 h-5" />
        </div>
      </div>
      <div class="mt-2 flex items-center gap-1 text-xs text-slate-500">
        <span class="text-amber-600">{{ statistics?.mediumRiskCount || 0 }}</span>
        <span>中风险</span>
        <span class="mx-1">|</span>
        <span class="text-emerald-600">{{ statistics?.lowRiskCount || 0 }}</span>
        <span>低风险</span>
      </div>
    </div>
  </div>

  <!-- 扩展统计区域 -->
  <div v-if="showExtended" class="bg-white rounded-xl p-4 shadow-sm border border-slate-200 mb-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-slate-800">效率指标</h3>
      <span class="text-xs text-slate-400">近7天</span>
    </div>
    <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
      <!-- 完成率 -->
      <div class="text-center p-3 bg-slate-50 rounded-lg">
        <p class="text-xs text-slate-500 mb-1">完成率</p>
        <p class="text-xl font-bold text-emerald-600">{{ statistics?.completionRate?.toFixed(1) || 0 }}%</p>
      </div>
      <!-- 平均评分 -->
      <div class="text-center p-3 bg-slate-50 rounded-lg">
        <p class="text-xs text-slate-500 mb-1">平均评分</p>
        <p class="text-xl font-bold text-indigo-600">{{ statistics?.avgScore?.toFixed(1) || '-' }}</p>
      </div>
      <!-- 今日完成 -->
      <div class="text-center p-3 bg-slate-50 rounded-lg">
        <p class="text-xs text-slate-500 mb-1">今日完成</p>
        <p class="text-xl font-bold text-blue-600">{{ statistics?.todayCompletedCount || 0 }}</p>
      </div>
      <!-- 平均处理速度 -->
      <div class="text-center p-3 bg-slate-50 rounded-lg">
        <p class="text-xs text-slate-500 mb-1">处理速度</p>
        <p class="text-xl font-bold text-slate-600">{{ statistics?.avgProcessingSpeed?.toFixed(1) || '-' }}</p>
        <p class="text-xs text-slate-400">任务/小时</p>
      </div>
    </div>

    <!-- 风险分布条 -->
    <div class="mt-4">
      <p class="text-xs text-slate-500 mb-2">风险等级分布</p>
      <div class="h-3 bg-slate-100 rounded-full overflow-hidden flex">
        <div
          v-if="highRiskPercent > 0"
          class="bg-red-500 transition-all duration-500"
          :style="{ width: highRiskPercent + '%' }"
        />
        <div
          v-if="mediumRiskPercent > 0"
          class="bg-amber-500 transition-all duration-500"
          :style="{ width: mediumRiskPercent + '%' }"
        />
        <div
          v-if="lowRiskPercent > 0"
          class="bg-emerald-500 transition-all duration-500"
          :style="{ width: lowRiskPercent + '%' }"
        />
        <div
          v-if="unknownRiskPercent > 0"
          class="bg-slate-300 transition-all duration-500"
          :style="{ width: unknownRiskPercent + '%' }"
        />
      </div>
      <div class="flex items-center justify-between mt-2 text-xs">
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 bg-red-500 rounded-full"></span>
          高 {{ statistics?.highRiskCount || 0 }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 bg-amber-500 rounded-full"></span>
          中 {{ statistics?.mediumRiskCount || 0 }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 bg-emerald-500 rounded-full"></span>
          低 {{ statistics?.lowRiskCount || 0 }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 bg-slate-300 rounded-full"></span>
          待评 {{ statistics?.unknownRiskCount || 0 }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  LayoutGrid,
  Clock,
  Loader,
  AlertTriangle
} from 'lucide-vue-next'

const props = defineProps({
  statistics: {
    type: Object,
    default: null
  },
  showExtended: {
    type: Boolean,
    default: false
  }
})

defineEmits(['filter-high-risk'])

// ==================== 计算属性 ====================

const pendingCount = computed(() => {
  return (props.statistics?.pendingCount || 0) + (props.statistics?.parsingCount || 0)
})

const pendingRate = computed(() => {
  const total = props.statistics?.totalCount || 0
  if (total === 0) return 0
  return Math.round((pendingCount.value / total) * 100)
})

const hasHighRisk = computed(() => {
  return (props.statistics?.highRiskCount || 0) > 0
})

// 风险分布百分比
const totalRisk = computed(() => {
  return (props.statistics?.highRiskCount || 0) +
    (props.statistics?.mediumRiskCount || 0) +
    (props.statistics?.lowRiskCount || 0) +
    (props.statistics?.unknownRiskCount || 0)
})

const highRiskPercent = computed(() => {
  if (totalRisk.value === 0) return 0
  return Math.round((props.statistics?.highRiskCount || 0) / totalRisk.value * 100)
})

const mediumRiskPercent = computed(() => {
  if (totalRisk.value === 0) return 0
  return Math.round((props.statistics?.mediumRiskCount || 0) / totalRisk.value * 100)
})

const lowRiskPercent = computed(() => {
  if (totalRisk.value === 0) return 0
  return Math.round((props.statistics?.lowRiskCount || 0) / totalRisk.value * 100)
})

const unknownRiskPercent = computed(() => {
  if (totalRisk.value === 0) return 0
  return 100 - highRiskPercent.value - mediumRiskPercent.value - lowRiskPercent.value
})
</script>

<style scoped>
.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
