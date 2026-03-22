<template>
  <div class="h-full flex flex-col bg-gradient-to-b from-slate-50 to-white">
    <!-- Chat Area -->
    <div
      class="flex-1"
      :class="hasActiveSession ? 'overflow-y-auto' : 'flex items-center justify-center px-6 py-10'"
    >
      <!-- Empty State -->
      <div v-if="!hasActiveSession" class="w-full max-w-3xl">
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
              <div class="max-w-md">
                <div class="bg-indigo-600 text-white rounded-2xl rounded-br-md px-5 py-3 shadow-lg">
                  <p class="text-sm">{{ message.content }}</p>
                </div>
                <!-- Attachments -->
                <div v-if="message.attachments?.length" class="flex flex-wrap justify-end gap-2 mt-2">
                  <span
                    v-for="(file, idx) in message.attachments"
                    :key="idx"
                    class="px-2 py-1 bg-slate-100 text-slate-600 text-xs rounded-lg"
                  >
                    {{ file }}
                  </span>
                </div>
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

        <!-- AI Result Panel -->
        <div v-if="aiResult" class="mt-6 animate-fadeIn">
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <!-- Risk Header -->
            <div class="px-6 py-4 bg-gradient-to-r from-slate-50 to-slate-100 border-b border-slate-200 flex items-center justify-between">
              <div class="flex items-center gap-4">
                <div
                  class="px-3 py-1.5 rounded-full text-xs font-bold"
                  :class="{
                    'bg-red-100 text-red-700': aiResult.riskLevel === 'High',
                    'bg-amber-100 text-amber-700': aiResult.riskLevel === 'Medium',
                    'bg-emerald-100 text-emerald-700': aiResult.riskLevel === 'Low'
                  }"
                >
                  <component :is="aiResult.riskLevel === 'High' ? ShieldAlert : aiResult.riskLevel === 'Medium' ? AlertCircle : CheckCircle2" class="w-4 h-4 inline mr-1" />
                  {{ aiResult.riskLevel === 'High' ? '高风险' : aiResult.riskLevel === 'Medium' ? '中风险' : '低风险' }}
                </div>
                <span class="text-xs text-slate-500">{{ aiResult.scene === 'TENDER' ? '标书审查' : aiResult.scene === 'CONTRACT' ? '合同预审' : '合规预警' }}</span>
              </div>
              <span class="text-xs text-slate-400 font-mono">{{ aiResult.traceId }}</span>
            </div>

            <!-- Summary -->
            <div class="px-6 py-4 border-b border-slate-100">
              <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">结果摘要</h4>
              <p class="text-sm text-slate-700">{{ aiResult.summary }}</p>
            </div>

            <!-- Steps -->
            <div v-if="aiResult.steps?.length" class="px-6 py-4 border-b border-slate-100">
              <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">执行步骤</h4>
              <div class="flex flex-wrap gap-2">
                <span
                  v-for="(step, idx) in aiResult.steps"
                  :key="idx"
                  class="px-2.5 py-1 bg-indigo-50 text-indigo-600 text-xs rounded-full"
                >
                  {{ idx + 1 }}. {{ step }}
                </span>
              </div>
            </div>

            <!-- Evidence List -->
            <div v-if="aiResult.evidenceList?.length" class="px-6 py-4">
              <h4 class="text-xs font-bold text-slate-500 uppercase mb-2">关键证据</h4>
              <ul class="space-y-2">
                <li
                  v-for="(evidence, idx) in aiResult.evidenceList"
                  :key="idx"
                  class="flex gap-2 text-xs text-slate-600"
                >
                  <span class="text-indigo-400 flex-shrink-0">•</span>
                  <span class="leading-relaxed">{{ evidence }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div
      :class="
        hasActiveSession
          ? 'fixed bottom-8 left-1/2 w-full max-w-2xl -translate-x-1/2 px-6'
          : 'w-full max-w-2xl mx-auto px-6 pb-4'
      "
    >
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
        AI 生成内容仅供参考，重大决策请人工复核 (横渡智能系统 v1.0)
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  Sparkles,
  FileText,
  Shield,
  AlertTriangle,
  Send,
  Upload,
  X,
  File as FileIcon,
  ShieldAlert,
  CheckCircle2,
  AlertCircle
} from 'lucide-vue-next'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const sessionStore = useSessionStore()

const inputText = ref('')
const selectedFiles = ref([])
const fileInput = ref(null)

// Get active session from store
const activeSession = computed(() => {
  const sessionId = route.query.sessionId
  if (sessionId) {
    return sessionStore.sessions.find(s => s.id === sessionId) || null
  }
  return sessionStore.activeSession
})

const messages = computed(() => {
  return activeSession.value?.messages || []
})

const hasActiveSession = computed(() => {
  return messages.value.length > 0
})

// Get AI result from agent messages
const aiResult = computed(() => {
  const agentMsg = messages.value.find(m => m.role === 'agent' && m.result)
  return agentMsg?.result || null
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

  // Create new session if needed
  let sessionId = activeSession.value?.id
  if (!sessionId) {
    const newSession = sessionStore.createSession('新对话')
    sessionId = newSession.id
  }

  // Add user message to store
  const fileNames = selectedFiles.value.map(f => f.name)
  sessionStore.addMessage(sessionId, {
    role: 'user',
    content: inputText.value,
    attachments: fileNames.length > 0 ? fileNames : undefined
  })

  // Clear input
  inputText.value = ''
  selectedFiles.value = []

  // Simulate agent response (in real app, this would be an API call)
  setTimeout(() => {
    sessionStore.addMessage(sessionId, {
      role: 'agent',
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
