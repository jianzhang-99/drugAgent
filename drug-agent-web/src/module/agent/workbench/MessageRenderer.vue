<template>
  <div class="message-renderer">
    <!-- 用户消息 -->
    <UserMessageBubble
      v-if="message.type === 'user_text'"
      :message="asUserMessage(message)"
    />

    <!-- AI 进度消息 (loading) -->
    <AssistantProgressMessage
      v-else-if="message.type === 'assistant_progress'"
      :message="asProgressMessage(message)"
    />

    <!-- AI 文本消息 -->
    <AssistantTextMessage
      v-else-if="message.type === 'assistant_text'"
      :message="asTextMessage(message)"
    />

    <!-- AI 澄清消息 -->
    <AssistantClarifyMessage
      v-else-if="message.type === 'assistant_clarify'"
      :message="asClarifyMessage(message)"
    />

    <!-- AI 结果卡片消息 -->
    <AssistantResultCard
      v-else-if="message.type === 'assistant_result_card'"
      :message="asResultCardMessage(message)"
      :is-selected="isSelected"
      @click="handleCardClick"
    />

    <!-- 系统错误消息 -->
    <SystemErrorMessage
      v-else-if="message.type === 'system_error'"
      :message="asSystemError(message)"
    />
  </div>
</template>

<script setup lang="ts">
import type {
  ChatMessage,
  UserMessage,
  AssistantProgressMessage,
  AssistantTextMessage,
  AssistantClarifyMessage,
  AssistantResultCardMessage,
  SystemErrorMessage
} from '@/module/agent/types/chatMessage'
import UserMessageBubble from './UserMessageBubble.vue'
import AssistantProgressMessage from './AssistantProgressMessage.vue'
import AssistantTextMessage from './AssistantTextMessage.vue'
import AssistantClarifyMessage from './AssistantClarifyMessage.vue'
import AssistantResultCard from './AssistantResultCard.vue'
import SystemErrorMessage from './SystemErrorMessage.vue'

// Props
const props = defineProps<{
  message: ChatMessage
  isSelected?: boolean
}>()

// Emits
const emit = defineEmits<{
  (e: 'card-click', message: AssistantResultCardMessage): void
}>()

// 类型守卫函数
function asUserMessage(msg: ChatMessage): UserMessage {
  return msg as UserMessage
}

function asProgressMessage(msg: ChatMessage): AssistantProgressMessage {
  return msg as AssistantProgressMessage
}

function asTextMessage(msg: ChatMessage): AssistantTextMessage {
  return msg as AssistantTextMessage
}

function asClarifyMessage(msg: ChatMessage): AssistantClarifyMessage {
  return msg as AssistantClarifyMessage
}

function asResultCardMessage(msg: ChatMessage): AssistantResultCardMessage {
  return msg as AssistantResultCardMessage
}

function asSystemError(msg: ChatMessage): SystemErrorMessage {
  return msg as SystemErrorMessage
}

// 处理卡片点击
function handleCardClick() {
  if (props.message.type === 'assistant_result_card') {
    emit('card-click', props.message)
  }
}
</script>

<style scoped>
.message-renderer {
  width: 100%;
}
</style>
