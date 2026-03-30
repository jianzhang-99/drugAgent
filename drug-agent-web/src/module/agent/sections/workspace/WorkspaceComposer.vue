<template>
  <div class="composer-shell">
    <UploadPanel v-if="showUploadPanel" @close="showUploadPanel = false" />

    <div class="composer-panel">
      <!-- 上半部分：多行文本输入区 -->
      <div class="composer-input-wrapper">
        <t-textarea
          v-model="inputText"
          class="composer-input"
          :disabled="store.sending"
          placeholder="描述您的监管需求，例如：检测这两份标书文件是否雷同..."
          :autosize="{ minRows: 2, maxRows: 8 }"
          @keydown="handleKeydown"
        />
      </div>

      <!-- 分割线 -->
      <div class="composer-divider"></div>

      <!-- 下半部分：操作栏 -->
      <div class="composer-footer">
        <div class="composer-tools">
          <button class="tool-btn" type="button" @click="showUploadPanel = true">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <span>上传材料</span>
          </button>
          
          <button class="tool-btn" type="button" @click="handleKnowledgeClick">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            <span>引用知识</span>
          </button>
        </div>

        <div class="composer-actions">
          <t-select
            v-model="store.currentModel"
            :options="modelOptions"
            size="small"
            style="width: 140px; margin-right: 12px"
            placeholder="选择模型"
          >
            <template #valueDisplay="{ value }">
              <span style="font-size: 13px">{{ value === 'minimax' ? 'MiniMax' : (value === 'dashscope' ? '阿里云百炼' : value) }}</span>
            </template>
          </t-select>

          <button 
            class="send-btn" 
            :class="{ active: inputText.trim() && !store.sending }" 
            :disabled="!inputText.trim() || store.sending"
            @click="handleSend"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            <span>发送任务</span>
            <t-loading v-if="store.sending" size="small" inherit-color style="margin-left: 4px" />
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
import { computed, ref, onMounted } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import UploadPanel from '../../components/UploadPanel.vue';
import { MessagePlugin } from 'tdesign-vue-next';

const store = useAgentStore();
const inputText = ref('');
const showUploadPanel = ref(false);

const modelOptions = computed(() => {
  return store.availableModels.map(m => ({ label: m.name, value: m.model }));
});

onMounted(() => {
  if (store.availableModels.length === 0) {
    store.loadModels();
  }
});

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
  width: 100%;
}

.composer-panel {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e1e5ec;
  border-radius: 12px;
  box-shadow: 0 4px 16px -4px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.composer-panel:focus-within {
  border-color: #cbd5e1;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08); /* 更柔和聚焦阴影 */
}

/* 输入区 */
.composer-input-wrapper {
  padding: 8px 6px;
}

:deep(.t-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 8px 12px;
  resize: none;
  color: #334155;
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

/* 分割线 */
.composer-divider {
  height: 1px;
  background-color: #f1f5f9;
  margin: 0 12px;
}

/* 底部操作区 */
.composer-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px 10px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 16px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

/* 发送按钮 */
.composer-actions {
  display: flex;
  align-items: center;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 20px;
  height: 36px;
  border-radius: 6px;
  border: none;
  background: #cbd5e1; /* 图中那种较浅的蓝灰色/禁用态 */
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
  cursor: not-allowed;
  transition: all 0.2s;
}

.send-btn.active {
  background: #b0c4de; /* 比如一个稍微深一点的蓝色，假设为主题色，你说的：如果有内容发声变化 */
  background: #60a5fa; /* 让它在可按时变成明显的蓝色 */
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(96, 165, 250, 0.3);
}

.send-btn.active:hover {
  background: #3b82f6; 
}

.send-btn.active:active {
  transform: translateY(1px);
}

/* 说明文字 */
.composer-note {
  margin-top: 14px;
  color: #9aa7ba;
  text-align: center;
  font-size: 12px;
}
</style>
