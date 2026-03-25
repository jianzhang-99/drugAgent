<template>
  <div class="chat-panel">
    <!-- 欢迎态 / 空状态 -->
    <div v-if="!store.activeSessionId" class="welcome-state">
      <div class="welcome-content">
        <h2>欢迎使用药品监管AI系统</h2>
        <p>请从左侧选择一个会话，或创建新会话开始</p>
        <t-button theme="primary" @click="handleNewSession">新建会话</t-button>
      </div>
    </div>

    <!-- 消息列表 -->
    <div v-else class="message-list" ref="messageListRef">
      <div
        v-for="msg in store.activeMessages"
        :key="msg.id"
        :class="['message-item', `message-${msg.role}`]"
      >
        <MessageRenderer :message="msg" />
      </div>

      <!-- loading 状态 -->
      <div v-if="store.sending" class="message-item message-assistant">
        <div class="loading-indicator">
          <t-loading-indicator />
          <span>AI 正在回复...</span>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="composer-area">
      <ComposerBar />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { useAgentStore } from '../store/agentStore';
import MessageRenderer from './MessageRenderer.vue';
import ComposerBar from './ComposerBar.vue';

const store = useAgentStore();
const messageListRef = ref<HTMLElement>();

// 监听消息变化，自动滚动到底部
watch(
  () => store.activeMessages.length,
  () => {
    nextTick(() => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
      }
    });
  }
);

function handleNewSession() {
  store.createSession();
}
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.welcome-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome-content {
  text-align: center;
}

.welcome-content h2 {
  margin: 0 0 12px;
  font-size: 24px;
  color: #333;
}

.welcome-content p {
  margin: 0 0 24px;
  font-size: 14px;
  color: #666;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.message-item {
  margin-bottom: 16px;
}

.message-user {
  display: flex;
  justify-content: flex-end;
}

.message-assistant {
  display: flex;
  justify-content: flex-start;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  color: #666;
  font-size: 14px;
}

.composer-area {
  border-top: 1px solid #e7e7e7;
  background: #fff;
}
</style>
