<script setup>
import { computed } from 'vue'
import { ElAvatar } from 'element-plus'
import { User, Robot } from '@element-plus/icons-vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  }
})

const isUser = computed(() => props.message.role === 'user')
const isAssistant = computed(() => props.message.role === 'assistant')

// 解析 metadata
const metadata = computed(() => {
  if (!props.message.metadata) return null
  try {
    return typeof props.message.metadata === 'string'
      ? JSON.parse(props.message.metadata)
      : props.message.metadata
  } catch {
    return null
  }
})

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="message-item" :class="{ user: isUser, assistant: isAssistant }">
    <!-- 头像 -->
    <el-avatar v-if="isUser" :icon="User" class="message-avatar user-avatar" />
    <el-avatar v-else :icon="Robot" class="message-avatar assistant-avatar" />

    <!-- 消息内容 -->
    <div class="message-content-wrapper">
      <div class="message-header">
        <span class="sender-name">{{ isUser ? '我' : 'AI 助手' }}</span>
        <span class="message-time">{{ formatTime(message.createdAt) }}</span>
      </div>

      <!-- 纯文本消息 -->
      <div v-if="!metadata" class="message-content">
        {{ message.content }}
      </div>

      <!-- AI 结构化响应 -->
      <div v-else class="message-structured">
        <div class="message-content">
          {{ message.content }}
        </div>

        <!-- Agent 结果展示 -->
        <div v-if="metadata.result" class="agent-result">
          <div class="result-header">
            <span class="scene-badge">{{ metadata.result.scene || metadata.scene }}</span>
            <span class="risk-badge" :class="metadata.result.riskLevel">
              {{ metadata.result.riskLevel === 'HIGH' ? '高风险' : metadata.result.riskLevel === 'MEDIUM' ? '中风险' : '低风险' }}
            </span>
            <span v-if="metadata.result.score" class="score">评分: {{ metadata.result.score }}</span>
          </div>

          <div v-if="metadata.result.summary" class="result-section">
            <div class="section-title">摘要</div>
            <div class="section-content">{{ metadata.result.summary }}</div>
          </div>

          <div v-if="metadata.result.managementSummary?.length" class="result-section">
            <div class="section-title">管理建议</div>
            <ul class="suggestion-list">
              <li v-for="(item, idx) in metadata.result.managementSummary" :key="idx">{{ item }}</li>
            </ul>
          </div>

          <div v-if="metadata.result.suggestedActions?.length" class="result-section">
            <div class="section-title">建议操作</div>
            <div class="action-tags">
              <span v-for="action in metadata.result.suggestedActions" :key="action" class="action-tag">
                {{ action }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  max-width: 100%;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
}

.user-avatar {
  background-color: var(--el-color-primary);
}

.assistant-avatar {
  background-color: var(--el-color-success);
}

.message-content-wrapper {
  flex: 1;
  max-width: 80%;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.user .message-header {
  flex-direction: row-reverse;
}

.sender-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.message-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.message-content {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.user .message-content {
  background-color: var(--el-color-primary);
  color: white;
  border-bottom-right-radius: 4px;
}

.assistant .message-content {
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  border-bottom-left-radius: 4px;
}

.message-structured {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-result {
  background-color: var(--el-fill-color-lighter);
  border-radius: 12px;
  padding: 12px 16px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.scene-badge {
  font-size: 11px;
  padding: 2px 8px;
  background-color: var(--el-color-info-light-9);
  color: var(--el-color-info);
  border-radius: 4px;
}

.risk-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}

.risk-badge.HIGH {
  background-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.risk-badge.MEDIUM {
  background-color: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.risk-badge.LOW {
  background-color: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.score {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-section {
  margin-top: 10px;
}

.section-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.section-content {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
}

.suggestion-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.suggestion-list li {
  margin-bottom: 4px;
}

.action-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-tag {
  font-size: 11px;
  padding: 3px 10px;
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border-radius: 12px;
}
</style>
