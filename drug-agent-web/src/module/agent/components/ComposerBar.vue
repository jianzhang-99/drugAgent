<template>
  <div class="composer-bar">
    <!-- 上传面板 -->
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

    <!-- 消息输入区 -->
    <div class="input-area">
      <div class="input-wrapper">
        <t-input
          v-model="inputText"
          placeholder="输入您的问题..."
          :disabled="store.sending"
          @enter="handleSend"
          @keydown.enter.ctrl="handleSend"
        >
          <template #suffix-icon>
            <t-icon name="upload" class="upload-icon" @click="showUploadPanel = true" />
          </template>
        </t-input>
      </div>

      <t-button
        theme="primary"
        :disabled="!inputText.trim() || store.sending"
        :loading="store.sending"
        @click="handleSend"
      >
        发送
      </t-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useAgentStore } from '../store/agentStore';
import UploadPanel from './UploadPanel.vue';

const store = useAgentStore();
const inputText = ref('');
const showUploadPanel = ref(false);

function handleSend() {
  if (!inputText.value.trim()) return;
  store.sendMessage(inputText.value.trim());
  inputText.value = '';
}
</script>

<style scoped>
.composer-bar {
  padding: 16px 24px;
  background: #fff;
}

.input-area {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper {
  flex: 1;
}

.upload-icon {
  cursor: pointer;
  color: #666;
}

.upload-icon:hover {
  color: #1890ff;
}
</style>
