<template>
  <div class="bg-white rounded-xl shadow-xl border border-slate-200 p-4 transition-all duration-300 focus-within:shadow-xl focus-within:border-indigo-200">
    <!-- 文件预览 -->
    <div v-if="files.length > 0" class="flex flex-wrap gap-2 mb-3">
      <div
        v-for="(file, index) in files"
        :key="index"
        class="flex items-center gap-2 px-3 py-1.5 bg-slate-100 rounded-lg text-xs text-slate-600"
      >
        <FileIcon class="w-3.5 h-3.5" />
        <span class="max-w-24 truncate">{{ file.name }}</span>
        <button
          class="hover:text-red-500 transition-colors"
          @click="removeFile(index)"
        >
          <X class="w-3.5 h-3.5" />
        </button>
      </div>
    </div>

    <!-- 文本输入框 -->
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      class="w-full min-h-[48px] max-h-36 border-0 resize-none outline-none text-sm text-slate-800 placeholder:text-slate-400"
      rows="1"
      @input="handleInput"
      @keydown.enter.exact.prevent="handleSend"
    />

    <!-- 底部工具栏 -->
    <div class="flex items-center justify-between pt-2 border-t border-slate-100">
      <div class="flex items-center gap-2">
        <button
          class="flex items-center gap-1.5 px-3 py-1.5 text-xs text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
          @click="triggerFileInput"
        >
          <Upload class="w-4 h-4" />
          上传材料
        </button>
        <input
          ref="fileInput"
          type="file"
          multiple
          class="hidden"
          accept=".pdf,.doc,.docx,.md"
          @change="handleFileChange"
        />
      </div>

      <button
        class="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/30 hover:shadow-indigo-500/40 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="!canSend"
        @click="handleSend"
      >
        <Send class="w-4 h-4" />
        发送任务
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Send, Upload, X, File as FileIcon } from 'lucide-vue-next'

const props = defineProps<{
  modelValue: string
  placeholder?: string
  isSending?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'send'): void
  (e: 'upload', files: FileList): void
}>()

// 文件列表
const files = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

// 是否可以发送
const canSend = computed(() => {
  return (props.modelValue.trim().length > 0 || files.value.length > 0) && !props.isSending
})

// 处理输入
function handleInput(event: Event) {
  const target = event.target as HTMLTextAreaElement
  emit('update:modelValue', target.value)
}

// 处理发送
function handleSend() {
  if (!canSend.value) return
  emit('send')
}

// 触发文件选择
function triggerFileInput() {
  fileInput.value?.click()
}

// 处理文件选择
function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const fileList = target.files

  if (fileList) {
    // 验证文件
    const validTypes = ['.pdf', '.doc', '.docx', '.md']
    const maxSize = 50 * 1024 * 1024 // 50MB

    Array.from(fileList).forEach(file => {
      const ext = '.' + file.name.split('.').pop()?.toLowerCase()
      if (!validTypes.includes(ext)) {
        console.warn(`文件 ${file.name} 格式不支持`)
        return
      }
      if (file.size > maxSize) {
        console.warn(`文件 ${file.name} 超过大小限制`)
        return
      }
      // 检查重复
      if (!files.value.some(f => f.name === file.name && f.size === file.size)) {
        files.value.push(file)
      }
    })

    emit('upload', fileList)
  }

  // 清空 input 以允许重复选择同一文件
  target.value = ''
}

// 移除文件
function removeFile(index: number) {
  files.value.splice(index, 1)
}
</script>

<style scoped>
/* 样式已在 Tailwind 中定义 */
</style>
