<template>
  <div class="composer-shell">
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

    <div class="composer-panel">
      <div class="composer-row">
        
        <div class="prefix-actions">
          <t-tooltip content="上传审查材料" placement="top">
            <button class="circular-btn" type="button" @click="showUploadPanel = true">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.2 15c.7-1.2 1-2.5.7-3.9-.6-2-2.4-3.5-4.4-3.5h-1.2c-.7-3-3.2-5.2-6.2-5.6-3-.3-5.9 1.3-7.3 4-1.2 2.5-1 6.5.5 8.8m8.7-1.6V21"/><path d="M16 16l-4-4-4 4"/></svg>
            </button>
          </t-tooltip>
          <t-tooltip content="引用内部知识库" placement="top">
            <button class="circular-btn" type="button" @click="handleKnowledgeClick">
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/><path d="M8 7h6"/><path d="M8 11h8"/></svg>
            </button>
          </t-tooltip>
        </div>

        <div class="composer-input-wrapper">
          <t-textarea
            v-model="inputText"
            class="composer-input"
            :disabled="store.sending"
            placeholder="描述监管需求，例如：对比附件中标书是否雷同..."
            :autosize="{ minRows: 1, maxRows: 8 }"
            @keydown="handleKeydown"
          />
        </div>

        <div class="suffix-actions">
          <button
            class="send-btn-circle"
            :class="{ 'is-active': inputText.trim() && !store.sending }"
            :disabled="!inputText.trim() || store.sending"
            @click="handleSend"
          >
            <t-loading v-if="store.sending" size="small" inherit-color />
            <svg v-else xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          </button>
        </div>

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

function handleKeydown(value: string, context: { e: KeyboardEvent }) {
  const e = context.e || (value as unknown as KeyboardEvent);
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

function handleKnowledgeClick() {
  MessagePlugin.info('知识库关联对话功能建设中，后续可支持拖拽法务条款');
}
</script>

<style scoped>
.composer-shell {
  position: relative;
}

.composer-panel {
  border: 1px solid #e2e8f0;
  border-radius: 26px;
  background: #ffffff;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.04), 0 4px 10px rgba(0, 0, 0, 0.02);
  padding: 8px 10px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.composer-panel:focus-within {
  border-color: #93c5fd;
  box-shadow: 0 10px 40px rgba(59, 130, 246, 0.08), 0 0 0 4px rgba(59, 130, 246, 0.1);
}

.composer-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.prefix-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 4px;
}

.circular-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.circular-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.circular-btn:active {
  transform: scale(0.92);
}

.composer-input-wrapper {
  flex: 1;
  min-width: 0;
}

:deep(.t-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 10px 4px;
  resize: none;
  color: #1e293b;
  font-size: 15px;
  line-height: 1.6;
  background: transparent !important;
  outline: none;
}

:deep(.t-textarea__inner:focus) {
  box-shadow: none !important;
}

:deep(.t-textarea__inner::placeholder) {
  color: #94a3b8;
}

.suffix-actions {
  display: flex;
  align-items: center;
  padding-bottom: 4px;
}

.send-btn-circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  background: #f1f5f9;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.send-btn-circle.is-active {
  background: linear-gradient(135deg, #0ea5e9, #3b82f6);
  color: #ffffff;
}

.send-btn-circle:disabled {
  cursor: not-allowed;
  transform: none !important;
}

.send-btn-circle.is-active:hover {
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
  transform: translateY(-2px);
}

.send-btn-circle.is-active:active {
  transform: translateY(1px);
}

.composer-note {
  margin-top: 14px;
  color: #9aa7ba;
  text-align: center;
  font-size: 12px;
}
</style>
