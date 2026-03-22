<template>
  <div class="h-full flex flex-col bg-gradient-to-b from-slate-50 to-white">
    <!-- Chat Area -->
    <div class="flex-1 overflow-y-auto">
      <!-- Empty State -->
      <div v-if="!hasActiveSession" class="max-w-3xl mx-auto px-6 pt-20 pb-40">
        <!-- Hero Section -->
        <div class="text-center mb-16">
          <div class="relative inline-block mb-8">
            <div class="w-20 h-20 rounded-2xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-2xl shadow-indigo-500/30">
              <Sparkles class="w-10 h-10 text-white" />
            </div>
            <div class="absolute inset-0 rounded-2xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 blur-xl opacity-40 -z-10" />
          </div>
          <h1 class="text-5xl font-extrabold text-slate-800 mb-4 tracking-tight">
            有什么我可以帮您分析的？
          </h1>
          <p class="text-lg text-slate-500 max-w-xl mx-auto">
            直接描述您的监管需求，AI Agent 将自动分发到对应的工作流
          </p>
        </div>

        <!-- Quick Action Cards -->
        <div class="grid grid-cols-3 gap-6">
          <div
            v-for="action in quickActions"
            :key="action.label"
            class="group bg-white rounded-2xl p-6 border border-slate-200/60 hover:border-indigo-200 hover:shadow-xl hover:shadow-indigo-500/10 transition-all duration-300 cursor-pointer"
            @click="handleQuickAction(action)"
          >
            <div
              class="w-12 h-12 rounded-xl flex items-center justify-center mb-4 transition-transform duration-300 group-hover:scale-110"
              :class="[action.bgColor, action.textColor]"
            >
              <component :is="action.icon" class="w-6 h-6" />
            </div>
            <h3 class="font-bold text-slate-800 mb-2">{{ action.label }}</h3>
            <p class="text-sm text-slate-500 leading-relaxed">{{ action.description }}</p>
          </div>
        </div>
      </div>

      <!-- Chat Messages -->
      <div v-else class="max-w-4xl mx-auto px-6 py-8 pb-48">
        <div class="space-y-6">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="animate-fadeIn"
          >
            <!-- User Message -->
            <div v-if="message.role === 'user'" class="flex gap-4 justify-end">
              <div class="max-w-md bg-indigo-600 text-white rounded-2xl rounded-br-md px-5 py-3 shadow-lg">
                <p class="text-sm">{{ message.content }}</p>
              </div>
              <div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center text-slate-600 text-xs font-bold flex-shrink-0">
                U
              </div>
            </div>

            <!-- Agent Message -->
            <div v-else class="flex gap-4 justify-start">
              <div class="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0">
                DA
              </div>
              <div class="max-w-lg bg-white border border-slate-200 rounded-2xl rounded-bl-md px-5 py-3 shadow-sm">
                <p class="text-sm text-slate-700">{{ message.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="fixed bottom-8 left-1/2 -translate-x-1/2 w-full max-w-2xl px-6">
      <div class="bg-white rounded-2xl shadow-xl border border-slate-200/60 p-4 transition-all duration-300 focus-within:shadow-2xl focus-within:border-indigo-200/60">
        <!-- File Preview -->
        <div v-if="selectedFiles.length > 0" class="flex flex-wrap gap-2 mb-3">
          <div
            v-for="(file, index) in selectedFiles"
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

        <!-- Textarea -->
        <textarea
          v-model="inputText"
          :placeholder="hasActiveSession ? '向 Agent 追加要求或提供更多材料...' : '描述您的监管需求，例如：检测这两份标书文件是否雷同...'"
          class="w-full min-h-[48px] max-h-36 border-0 resize-none outline-none text-sm text-slate-800 placeholder:text-slate-400"
          rows="1"
          @keydown.enter.exact.prevent="handleSend"
        />

        <!-- Footer -->
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
              @change="handleFileChange"
            />
          </div>

          <button
            class="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/30 hover:shadow-indigo-500/40 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="!inputText.trim() && selectedFiles.length === 0"
            @click="handleSend"
          >
            <Send class="w-4 h-4" />
            发送任务
          </button>
        </div>
      </div>

      <p class="text-center text-xs text-slate-400 mt-3">
        AI 生成内容仅供参考，重大决策请人工复核 (Drug-Agent Core v0.3)
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  Sparkles,
  FileText,
  Shield,
  AlertTriangle,
  Send,
  Upload,
  X,
  File as FileIcon
} from 'lucide-vue-next'

const inputText = ref('')
const selectedFiles = ref([])
const fileInput = ref(null)
const messages = ref([])

// Demo session data
const currentSession = ref({
  id: 'sess_demo',
  title: '年度设备采购标书比对',
  messages: [
    {
      role: 'user',
      content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。'
    },
    {
      role: 'assistant',
      content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。'
    }
  ]
})

const hasActiveSession = computed(() => {
  return currentSession.value && currentSession.value.messages.length > 0
})

const quickActions = [
  {
    icon: FileText,
    label: '标书审查',
    description: '对比多份标书文件，检测雷同率和围标嫌疑',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    bgColor: 'bg-indigo-50',
    textColor: 'text-indigo-600'
  },
  {
    icon: Shield,
    label: '合同预审',
    description: '基于合规知识库提取潜在风险条款',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    bgColor: 'bg-emerald-50',
    textColor: 'text-emerald-600'
  },
  {
    icon: AlertTriangle,
    label: '合规预警',
    description: '分析采购数据异常波动，生成预警报告',
    prompt: '分析近3个月的骨科耗材采购数据，生成异常波动预警报告。',
    bgColor: 'bg-amber-50',
    textColor: 'text-amber-600'
  }
]

const handleQuickAction = (action) => {
  inputText.value = action.prompt
  handleSend()
}

const handleSend = () => {
  if (!inputText.value.trim() && selectedFiles.value.length === 0) return

  // Add user message
  messages.value.push({
    role: 'user',
    content: inputText.value
  })

  // Clear input
  inputText.value = ''
  selectedFiles.value = []

  // Simulate agent response
  setTimeout(() => {
    messages.value.push({
      role: 'assistant',
      content: '正在分析您的请求，请稍候...'
    })
  }, 1000)
}

const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileChange = (event) => {
  const files = Array.from(event.target.files || [])
  selectedFiles.value = [...selectedFiles.value, ...files]
  event.target.value = ''
}

const removeFile = (index) => {
  selectedFiles.value.splice(index, 1)
}
</script>

<style scoped>
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-fadeIn {
  animation: fadeIn 0.3s ease-out;
}
</style>
