<template>
  <div class="composer-bar">
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

    <div class="composer-shell">
      <t-textarea
        v-model="inputText"
        class="composer-textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="描述您的监管需求，例如：检测这两份标书文件是否雷同..."
        :disabled="store.sending"
        @keydown.enter.prevent="handleSend"
      />

      <div class="action-row">
        <div class="left-actions">
          <button class="tool-link" type="button" @click="showUploadPanel = true">
            <span>⇪</span>
            <span>上传材料</span>
          </button>
          <button class="tool-link" type="button">
            <span>◫</span>
            <span>引用知识</span>
          </button>
        </div>

        <t-button
          theme="primary"
          size="large"
          class="send-btn"
          :disabled="!inputText.trim() || store.sending"
          :loading="store.sending"
          @click="handleSend"
        >
          发送任务
        </t-button>
      </div>
    </div>

    <div class="composer-note">
      AI 生成内容仅供参考，重大决策请人工复核（横渡智能体 Core v0.3）
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
  position: relative;
}

.composer-shell {
  border-radius: 24px;
  background: #fff;
  border: 1px solid #e8ecf1;
  box-shadow: 0 4px 24px -4px rgba(15, 23, 42, 0.1);
  padding: 12px 12px 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.composer-shell:focus-within {
  border-color: #6366f1;
  box-shadow: 0 4px 24px -4px rgba(99, 102, 241, 0.15);
}

.composer-textarea :deep(textarea) {
  border: none;
  box-shadow: none;
  resize: none;
  padding: 16px 20px 14px;
  font-size: 15px;
  line-height: 1.7;
  color: #31435f;
}

.composer-textarea :deep(textarea::placeholder) {
  color: #9aa9bf;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px 8px;
  border-top: 1px solid #f0f3f7;
}

.left-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.tool-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #6b7a8f;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: all 0.2s;
}

.tool-link:hover {
  background: #f5f7fa;
  color: #3b82f6;
}

.send-btn {
  min-width: 120px;
  border-radius: 12px;
}

.composer-note {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: #b0bac6;
}

@media (max-width: 768px) {
  .action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .left-actions {
    justify-content: center;
  }
}
</style>
