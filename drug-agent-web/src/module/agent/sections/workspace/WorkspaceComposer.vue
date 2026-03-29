<template>
  <div class="composer-shell">
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

    <div class="composer-panel">
      <div class="composer-input-wrapper">
        <t-textarea
          v-model="inputText"
          class="composer-input"
          :disabled="store.sending"
          placeholder="描述您的监管需求，例如：检测这两份标书文件是否雷同..."
          :autosize="{ minRows: 1, maxRows: 8 }"
          @keydown.enter.prevent="handleSend"
        />
      </div>

      <div class="composer-footer">
        <div class="composer-tools">
          <button class="tool-button" type="button" @click="showUploadPanel = true">
            <span>⇪</span>
            <span>上传材料</span>
          </button>
          <button class="tool-button" type="button" @click="handleKnowledgeClick">
            <span>◫</span>
            <span>引用知识</span>
          </button>
        </div>

        <t-button
          theme="primary"
          size="large"
          class="send-button"
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
import { useAgentStore } from '../../store/agentStore';
import UploadPanel from '../../components/UploadPanel.vue';
import { MessagePlugin } from 'tdesign-vue-next';

const store = useAgentStore();
const inputText = ref('');
const showUploadPanel = ref(false);

function handleSend() {
  if (!inputText.value.trim()) return;
  store.sendMessage(inputText.value.trim());
  inputText.value = '';
}

function handleKnowledgeClick() {
  MessagePlugin.info('知识库关联对话功能建设中，后续可支持从右侧面板拖拽法务条款');
}
</script>

<style scoped>
.composer-shell {
  position: relative;
}

.composer-panel {
  border: 1px solid #dfe7f2;
  border-radius: 34px;
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 -6px 26px -10px rgba(15, 23, 42, 0.16);
  padding: 10px 10px 6px;
}

.composer-input-wrapper {
  padding: 8px 10px 2px;
}

:deep(.t-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 10px 12px;
  resize: none;
  color: #31435f;
  font-size: 16px;
  line-height: 1.7;
  background: transparent !important;
  outline: none;
}

:deep(.t-textarea__inner:focus) {
  box-shadow: none !important;
}

:deep(.t-textarea__inner::placeholder) {
  color: #9aa9bf;
}

.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid #edf2f8;
  padding: 8px 12px 10px;
}

.composer-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.tool-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  color: #657894;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.send-button {
  min-width: 160px;
}

.composer-note {
  margin-top: 12px;
  color: #9aa7ba;
  text-align: center;
  font-size: 12px;
}

@media (max-width: 768px) {
  .composer-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .composer-tools {
    justify-content: space-between;
  }
}
</style>
