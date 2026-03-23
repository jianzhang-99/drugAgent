<template>
  <div class="empty-state text-center py-20">
    <div class="inline-flex items-center justify-center w-24 h-24 bg-slate-100 rounded-3xl mb-6">
      <component :is="icon" class="w-12 h-12 text-slate-400" />
    </div>
    <h3 class="text-xl font-medium text-slate-600 mb-2">{{ title }}</h3>
    <p class="text-slate-400 mb-6 max-w-sm mx-auto">{{ description }}</p>
    <div class="flex items-center justify-center gap-3">
      <button
        v-if="showCreateButton"
        class="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors shadow-sm flex items-center gap-2"
        @click="$emit('create')"
      >
        <Plus class="w-4 h-4" />
        创建任务
      </button>
      <button
        v-if="showRefreshButton"
        class="px-5 py-2.5 bg-white text-slate-700 text-sm font-semibold rounded-xl border border-slate-200 hover:bg-slate-50 transition-colors flex items-center gap-2"
        @click="$emit('refresh')"
      >
        <RefreshCw class="w-4 h-4" />
        刷新
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  Inbox,
  Search,
  AlertCircle,
  Plus,
  RefreshCw
} from 'lucide-vue-next'

const props = defineProps({
  type: {
    type: String,
    default: 'empty',
    validator: (v) => ['empty', 'no-result', 'error', 'loading'].includes(v)
  },
  title: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  showCreateButton: {
    type: Boolean,
    default: true
  },
  showRefreshButton: {
    type: Boolean,
    default: true
  }
})

defineEmits(['create', 'refresh'])

const icon = computed(() => {
  const iconMap = {
    empty: Inbox,
    'no-result': Search,
    error: AlertCircle,
    loading: RefreshCw
  }
  return iconMap[props.type] || Inbox
})

const defaultTitle = computed(() => {
  const titleMap = {
    empty: '暂无任务',
    'no-result': '没有找到任务',
    error: '加载失败',
    loading: '加载中...'
  }
  return titleMap[props.type] || props.title
})

const defaultDescription = computed(() => {
  const descMap = {
    empty: '开始一个新任务来体验 AI 审查流程',
    'no-result': '请尝试调整筛选条件',
    error: '请稍后重试或联系管理员',
    loading: '正在获取任务列表'
  }
  return descMap[props.type] || props.description
})
</script>

<style scoped>
.empty-state {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
