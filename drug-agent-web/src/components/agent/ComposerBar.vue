<template>
  <div class="w-full max-w-4xl mx-auto bg-white rounded-3xl shadow-[0_-4px_24px_-8px_rgba(0,0,0,0.08)] border border-slate-200 p-1.5 transition-all focus-within:ring-4 focus-within:ring-blue-100 focus-within:border-blue-400">
    <textarea
      v-model="inputText"
      @keydown="handleKeyDown"
      rows="2"
      :placeholder="placeholder"
      class="w-full bg-transparent border-none p-4 text-slate-700 focus:ring-0 outline-none resize-none text-[15px] max-h-32"
    />
    <div class="flex items-center justify-between px-3 pb-2 pt-1 border-t border-slate-50">
      <div class="flex gap-1 sm:gap-2">
        <button class="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium">
          <Upload size="16" />
          <span class="hidden sm:inline">上传材料</span>
        </button>
        <button class="p-2 text-slate-500 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium">
          <BookOpen size="16" />
          <span class="hidden sm:inline">引用知识</span>
        </button>
      </div>
      <button
        @click="handleSubmit"
        :disabled="isLoading || !inputText.trim()"
        class="bg-blue-600 text-white px-5 sm:px-6 py-2 rounded-xl text-sm font-bold shadow-md shadow-blue-200 hover:bg-blue-700 transition-all flex items-center gap-2 disabled:bg-slate-300 disabled:shadow-none"
      >
        <Loader2 v-if="isLoading" size="16" class="animate-spin" />
        <Send v-else size="16" />
        <span class="hidden sm:inline">发送任务</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Send, Upload, BookOpen, Loader2 } from 'lucide-vue-next'

const props = defineProps<{
  isLoading?: boolean
  hasActiveSession?: boolean
}>()

const emit = defineEmits<{
  submit: [text: string]
}>()

const inputText = ref('')

const placeholder = computed(() => {
  return props.hasActiveSession
    ? '向 Agent 追加要求或提供更多材料...'
    : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'
})

const handleSubmit = () => {
  if (!inputText.value.trim() || props.isLoading) return
  emit('submit', inputText.value)
  inputText.value = ''
}

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  }
}
</script>
