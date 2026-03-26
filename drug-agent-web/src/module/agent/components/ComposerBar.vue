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
        @keydown.enter.exact.prevent="handleSend"
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
  border-radius: 34px;
  background: #fff;
  border: 1px solid #dfe7f2;
  box-shadow: 0 -4px 24px -8px rgba(15, 23, 42, 0.12);
  padding: 10px 10px 6px;
}

.composer-textarea :deep(textarea) {
  border: none;
  box-shadow: none;
  resize: none;
  padding: 18px 22px 16px;
  font-size: 18px;
  line-height: 1.7;
  color: #31435f;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px 10px;
  border-top: 1px solid #eef3f9;
}

.left-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.tool-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: #61748f;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.send-btn {
  min-width: 160px;
}

.composer-note {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: #a0aec0;
}

@media (max-width: 768px) {
  .action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .left-actions {
    justify-content: space-between;
  }
}
</style>
