<template>
  <component
    :is="componentMap[message.type]"
    :message="message"
    :is-selected="isSelected"
    @click-card="$emit('click-card', $event)"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage, ReportSummary } from '@/store/agent/types'

import UserMessageBubble from './UserMessageBubble.vue'
import AssistantProgressMessage from './AssistantProgressMessage.vue'
import AssistantTextMessage from './AssistantTextMessage.vue'
import AssistantClarifyMessage from './AssistantClarifyMessage.vue'
import AssistantResultCard from './AssistantResultCard.vue'
import SystemErrorMessage from './SystemErrorMessage.vue'

const props = defineProps<{
  message: ChatMessage
  isSelected?: boolean
}>()

defineEmits<{
  'click-card': [result: ReportSummary]
}>()

const componentMap = {
  'user_text': UserMessageBubble,
  'assistant_progress': AssistantProgressMessage,
  'assistant_text': AssistantTextMessage,
  'assistant_clarify': AssistantClarifyMessage,
  'assistant_result_card': AssistantResultCard,
  'system_error': SystemErrorMessage
}
</script>
