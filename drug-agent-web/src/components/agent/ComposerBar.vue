<template>
  <div class="w-full max-w-4xl mx-auto bg-white rounded-3xl shadow-[0_-4px_24px_-8px_rgba(0,0,0,0.08)] border border-slate-200 p-1.5 transition-all focus-within:ring-4 focus-within:ring-blue-100 focus-within:border-blue-400">
    <!-- File List -->
    <div v-if="uploadedFiles.length > 0" class="px-4 pt-3 pb-1">
      <div class="flex flex-wrap gap-2">
        <div
          v-for="(file, index) in uploadedFiles"
          :key="index"
          class="flex items-center gap-2 px-3 py-1.5 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-700 group"
        >
          <FileIcon size="12" class="text-blue-500" />
          <span class="max-w-[120px] truncate">{{ file.name }}</span>
          <span class="text-blue-400">({{ formatFileSize(file.size) }})</span>
          <button
            v-if="!file.uploading"
            @click="removeFile(index)"
            class="ml-1 text-blue-400 hover:text-red-500 transition-colors"
          >
            <X size="12" />
          </button>
          <Loader2 v-else size="12" class="animate-spin ml-1" />
        </div>
      </div>
    </div>

    <textarea
      v-model="inputText"
      @keydown="handleKeyDown"
      rows="2"
      :placeholder="placeholder"
      :disabled="isOffline"
      class="w-full bg-transparent border-none p-4 text-slate-700 focus:ring-0 outline-none resize-none text-[15px] max-h-32 disabled:bg-slate-50 disabled:text-slate-400"
    />
    <div class="flex items-center justify-between px-3 pb-2 pt-1 border-t border-slate-50">
      <div class="flex gap-1 sm:gap-2">
        <!-- File Upload Button -->
        <button
          @click="triggerFileInput"
          :disabled="isOffline || isUploading"
          class="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Loader2 v-if="isUploading" size="16" class="animate-spin" />
          <Upload v-else size="16" />
          <span class="hidden sm:inline">上传材料</span>
        </button>
        <input
          ref="fileInputRef"
          type="file"
          multiple
          accept=".pdf,.doc,.docx,.xls,.xlsx,.txt"
          @change="handleFileChange"
          class="hidden"
        />
        <button class="p-2 text-slate-500 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium">
          <BookOpen size="16" />
          <span class="hidden sm:inline">引用知识</span>
        </button>
      </div>

      <!-- Offline Indicator -->
      <div v-if="isOffline" class="flex items-center gap-1.5 text-xs text-amber-600 bg-amber-50 px-3 py-1.5 rounded-lg">
        <WifiOff size="14" />
        <span>离线</span>
      </div>

      <button
        @click="handleSubmit"
        :disabled="isLoading || !inputText.trim() || isOffline"
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Send, Upload, BookOpen, Loader2, FileIcon, X, WifiOff } from 'lucide-vue-next'

interface UploadFile {
  name: string
  size: number
  uploading?: boolean
}

const props = defineProps<{
  isLoading?: boolean
  hasActiveSession?: boolean
}>()

const emit = defineEmits<{
  submit: [text: string, attachments?: string[]]
}>()

const inputText = ref('')
const uploadedFiles = ref<UploadFile[]>([])
const isUploading = ref(false)
const isOffline = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const placeholder = computed(() => {
  if (isOffline.value) return '网络已断开，请检查网络连接...'
  return props.hasActiveSession
    ? '向 Agent 追加要求或提供更多材料...'
    : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'
})

// Network status detection
const updateNetworkStatus = () => {
  isOffline.value = !navigator.onLine
}

onMounted(() => {
  window.addEventListener('online', updateNetworkStatus)
  window.addEventListener('offline', updateNetworkStatus)
  updateNetworkStatus()
})

onUnmounted(() => {
  window.removeEventListener('online', updateNetworkStatus)
  window.removeEventListener('offline', updateNetworkStatus)
})

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files) return

  for (const file of files) {
    // Simulate upload delay
    const uploadFile: UploadFile = {
      name: file.name,
      size: file.size,
      uploading: true
    }
    uploadedFiles.value.push(uploadFile)

    // Simulate upload completion after 1.5s
    setTimeout(() => {
      const index = uploadedFiles.value.findIndex(f => f.name === file.name && f.uploading)
      if (index !== -1) {
        uploadedFiles.value[index].uploading = false
      }
    }, 1500)
  }

  // Reset input
  target.value = ''
}

const removeFile = (index: number) => {
  uploadedFiles.value.splice(index, 1)
}

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const handleSubmit = () => {
  if (!inputText.value.trim() || props.isLoading || isOffline.value) return

  const attachments = uploadedFiles.value.map(f => f.name)
  emit('submit', inputText.value, attachments)

  inputText.value = ''
  uploadedFiles.value = []
}

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSubmit()
  }
}
</script>
