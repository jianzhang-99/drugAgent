<template>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-4 p-4">
    <!-- 标书审查 -->
    <button
      @click="handleSubmit('tender')"
      class="group relative bg-white border border-slate-200 rounded-xl p-5 text-left transition-all duration-200 hover:scale-105 hover:shadow-md hover:border-indigo-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
    >
      <div class="flex items-start gap-4">
        <div class="flex-shrink-0 w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center group-hover:bg-indigo-100 transition-colors">
          <FileText :size="24" class="text-indigo-600" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="font-semibold text-slate-900 mb-1">标书审查</h3>
          <p class="text-sm text-slate-500 line-clamp-2">帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。</p>
        </div>
      </div>
      <div class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
        <ArrowRight :size="16" class="text-indigo-400" />
      </div>
    </button>

    <!-- 合同预审 -->
    <button
      @click="handleSubmit('contract')"
      class="group relative bg-white border border-slate-200 rounded-xl p-5 text-left transition-all duration-200 hover:scale-105 hover:shadow-md hover:border-emerald-200 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2"
    >
      <div class="flex items-start gap-4">
        <div class="flex-shrink-0 w-12 h-12 rounded-xl bg-emerald-50 flex items-center justify-center group-hover:bg-emerald-100 transition-colors">
          <ShieldCheck :size="24" class="text-emerald-600" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="font-semibold text-slate-900 mb-1">合同预审</h3>
          <p class="text-sm text-slate-500 line-clamp-2">审查最新版本的采购合同，基于合规知识库提取潜在风险条款。</p>
        </div>
      </div>
      <div class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
        <ArrowRight :size="16" class="text-emerald-400" />
      </div>
    </button>

    <!-- 合规预警 -->
    <button
      @click="handleSubmit('compliance')"
      class="group relative bg-white border border-slate-200 rounded-xl p-5 text-left transition-all duration-200 hover:scale-105 hover:shadow-md hover:border-amber-200 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2"
    >
      <div class="flex items-start gap-4">
        <div class="flex-shrink-0 w-12 h-12 rounded-xl bg-amber-50 flex items-center justify-center group-hover:bg-amber-100 transition-colors">
          <AlertTriangle :size="24" class="text-amber-600" />
        </div>
        <div class="flex-1 min-w-0">
          <h3 class="font-semibold text-slate-900 mb-1">合规预警</h3>
          <p class="text-sm text-slate-500 line-clamp-2">分析近3个月的骨科耗材采购数据，生成异常波动预警报告。</p>
        </div>
      </div>
      <div class="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
        <ArrowRight :size="16" class="text-amber-400" />
      </div>
    </button>
  </div>
</template>

<script setup>
import { FileText, ShieldCheck, AlertTriangle, ArrowRight } from 'lucide-vue-next'

const emit = defineEmits(['submit'])

const handleSubmit = (scene) => {
  const prompts = {
    tender: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    contract: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    compliance: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。'
  }
  emit('submit', {
    scene,
    prompt: prompts[scene]
  })
}
</script>
