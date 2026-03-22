<template>
  <div
    class="flex mb-4"
    :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
  >
    <!-- Agent Avatar -->
    <div
      v-if="message.role === 'agent'"
      class="flex-shrink-0 w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 flex items-center justify-center mr-3"
    >
      <Bot :size="20" class="text-white" />
    </div>

    <div class="max-w-[70%]">
      <!-- Message Bubble -->
      <div
        class="px-4 py-3"
        :class="[
          message.role === 'user'
            ? 'bg-slate-900 text-white rounded-2xl rounded-tr-sm'
            : 'bg-white border border-slate-200 text-slate-800 rounded-2xl rounded-tl-sm shadow-sm'
        ]"
      >
        <!-- Text Content -->
        <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ message.content }}</p>

        <!-- Attachments (User Messages) -->
        <div v-if="message.role === 'user' && message.attachments?.length" class="mt-3 pt-3 border-t border-slate-700">
          <div class="flex flex-wrap gap-2">
            <div
              v-for="(file, index) in message.attachments"
              :key="index"
              class="flex items-center gap-2 px-3 py-1.5 bg-slate-800 rounded-lg text-xs text-slate-300"
            >
              <FileText :size="14" />
              <span>{{ file.name }}</span>
              <span class="text-slate-500">{{ formatFileSize(file.size) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Report Card (Agent Messages with Result) -->
      <div v-if="message.role === 'agent' && message.result" class="mt-3">
        <ReportCard :report="message.result" :selected="selectedReportId === message.result.id" @click="handleReportClick(message.result)" />
      </div>

      <!-- Timestamp -->
      <div
        class="text-xs text-slate-400 mt-1"
        :class="message.role === 'user' ? 'text-right' : 'text-left'"
      >
        {{ formatTime(message.timestamp) }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { Bot, FileText } from 'lucide-vue-next'
import ReportCard from './ReportCard.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  selectedReportId: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['select-report'])

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const formatFileSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

const handleReportClick = (report) => {
  emit('select-report', report)
}
</script>
