<template>
  <div class="filter-bar bg-white rounded-xl p-4 shadow-sm border border-slate-200 mb-4">
    <!-- 搜索框 -->
    <div class="flex items-center gap-3 mb-4">
      <div class="flex-1 relative">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
        <input
          v-model="localKeyword"
          type="text"
          placeholder="搜索任务名称或摘要..."
          class="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
          @input="handleKeywordChange"
        />
        <button
          v-if="localKeyword"
          class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
          @click="clearKeyword"
        >
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- 快捷筛选标签 -->
      <div class="flex items-center gap-2">
        <button
          v-for="quickFilter in quickFilters"
          :key="quickFilter.key"
          :class="[
            'px-3 py-1.5 rounded-lg text-xs font-medium border transition-all',
            filters[quickFilter.key]
              ? 'bg-indigo-50 border-indigo-200 text-indigo-700'
              : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
          ]"
          @click="toggleQuickFilter(quickFilter.key)"
        >
          <component :is="quickFilter.icon" class="w-3.5 h-3.5 inline mr-1" />
          {{ quickFilter.label }}
          <span
            v-if="quickFilter.count > 0"
            class="ml-1 px-1.5 py-0.5 rounded-full text-xs"
            :class="filters[quickFilter.key] ? 'bg-indigo-200' : 'bg-slate-100'"
          >
            {{ quickFilter.count }}
          </span>
        </button>
      </div>
    </div>

    <!-- 筛选条件行 -->
    <div class="flex items-center gap-3 flex-wrap">
      <!-- 状态筛选 -->
      <div class="flex items-center gap-2">
        <label class="text-xs text-slate-500 font-medium">状态</label>
        <select
          v-model="localStatus"
          class="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
          @change="handleStatusChange"
        >
          <option value="">全部</option>
          <option v-for="(label, value) in filterOptions.statusOptions" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>

      <!-- 任务类型筛选 -->
      <div class="flex items-center gap-2">
        <label class="text-xs text-slate-500 font-medium">类型</label>
        <select
          v-model="localTaskType"
          class="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
          @change="handleTaskTypeChange"
        >
          <option value="">全部</option>
          <option v-for="(label, value) in filterOptions.taskTypeOptions" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </div>

      <!-- 风险等级筛选 -->
      <div class="flex items-center gap-2">
        <label class="text-xs text-slate-500 font-medium">风险</label>
        <select
          v-model="localRiskLevel"
          class="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
          @change="handleRiskLevelChange"
        >
          <option value="">全部</option>
          <option value="HIGH">高风险</option>
          <option value="MEDIUM">中风险</option>
          <option value="LOW">低风险</option>
          <option value="UNKNOWN">待评估</option>
        </select>
      </div>

      <!-- 排序 -->
      <div class="flex items-center gap-2 ml-auto">
        <label class="text-xs text-slate-500 font-medium">排序</label>
        <select
          v-model="localSortField"
          class="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
          @change="handleSortChange"
        >
          <option value="createdAt">创建时间</option>
          <option value="updatedAt">更新时间</option>
          <option value="priority">优先级</option>
          <option value="riskLevel">风险等级</option>
          <option value="score">评分</option>
        </select>
        <button
          class="p-1.5 bg-slate-50 border border-slate-200 rounded-lg hover:bg-slate-100 transition-colors"
          @click="toggleSortOrder"
        >
          <component
            :is="localSortOrder === 'desc' ? ArrowDown : ArrowUp"
            class="w-4 h-4 text-slate-600"
          />
        </button>
      </div>

      <!-- 重置按钮 -->
      <button
        v-if="hasActiveFilters"
        class="flex items-center gap-1 px-3 py-1.5 text-xs text-slate-500 hover:text-slate-700 transition-colors"
        @click="resetFilters"
      >
        <RotateCcw class="w-3.5 h-3.5" />
        重置
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import {
  Search,
  X,
  Clock,
  AlertTriangle,
  CheckCircle,
  ArrowDown,
  ArrowUp,
  RotateCcw,
  ListFilter
} from 'lucide-vue-next'

const props = defineProps({
  filters: {
    type: Object,
    required: true
  },
  filterOptions: {
    type: Object,
    required: true
  },
  statistics: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:filters', 'change'])

// ==================== 本地状态 ====================

const localKeyword = ref(props.filters.keyword || '')
const localStatus = ref(props.filters.status || '')
const localTaskType = ref(props.filters.taskType || '')
const localRiskLevel = ref(props.filters.riskLevel || '')
const localSortField = ref(props.filters.sortField || 'createdAt')
const localSortOrder = ref(props.filters.sortOrder || 'desc')

// ==================== 快捷筛选 ====================

const quickFilters = computed(() => [
  {
    key: 'unhandledOnly',
    label: '待处理',
    icon: Clock,
    count: props.statistics?.pendingCount || 0
  },
  {
    key: 'highRiskOnly',
    label: '高风险',
    icon: AlertTriangle,
    count: props.statistics?.highRiskCount || 0
  }
])

// ==================== 计算属性 ====================

const hasActiveFilters = computed(() => {
  return localKeyword.value ||
    localStatus.value ||
    localTaskType.value ||
    localRiskLevel.value ||
    props.filters.unhandledOnly ||
    props.filters.highRiskOnly
})

// ==================== 方法 ====================

const emitChange = () => {
  emit('update:filters', {
    keyword: localKeyword.value,
    status: localStatus.value,
    taskType: localTaskType.value,
    riskLevel: localRiskLevel.value,
    sortField: localSortField.value,
    sortOrder: localSortOrder.value,
    unhandledOnly: props.filters.unhandledOnly,
    highRiskOnly: props.filters.highRiskOnly
  })
  emit('change')
}

let keywordTimeout = null
const handleKeywordChange = () => {
  clearTimeout(keywordTimeout)
  keywordTimeout = setTimeout(() => {
    emitChange()
  }, 300)
}

const clearKeyword = () => {
  localKeyword.value = ''
  emitChange()
}

const handleStatusChange = () => {
  emitChange()
}

const handleTaskTypeChange = () => {
  emitChange()
}

const handleRiskLevelChange = () => {
  emitChange()
}

const handleSortChange = () => {
  emitChange()
}

const toggleSortOrder = () => {
  localSortOrder.value = localSortOrder.value === 'desc' ? 'asc' : 'desc'
  emitChange()
}

const toggleQuickFilter = (key) => {
  emit('update:filters', {
    ...props.filters,
    [key]: !props.filters[key]
  })
  emit('change')
}

const resetFilters = () => {
  localKeyword.value = ''
  localStatus.value = ''
  localTaskType.value = ''
  localRiskLevel.value = ''
  localSortField.value = 'createdAt'
  localSortOrder.value = 'desc'
  emit('update:filters', {
    keyword: '',
    status: '',
    taskType: '',
    riskLevel: '',
    sortField: 'createdAt',
    sortOrder: 'desc',
    unhandledOnly: false,
    highRiskOnly: false
  })
  emit('change')
}

// 监听外部filters变化
watch(() => props.filters, (newFilters) => {
  localKeyword.value = newFilters.keyword || ''
  localStatus.value = newFilters.status || ''
  localTaskType.value = newFilters.taskType || ''
  localRiskLevel.value = newFilters.riskLevel || ''
  localSortField.value = newFilters.sortField || 'createdAt'
  localSortOrder.value = newFilters.sortOrder || 'desc'
}, { deep: true })
</script>

<style scoped>
.filter-bar {
  transition: box-shadow 0.2s ease;
}

.filter-bar:focus-within {
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}
</style>
