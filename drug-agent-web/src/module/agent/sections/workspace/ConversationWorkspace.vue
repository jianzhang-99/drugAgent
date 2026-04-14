<template>
  <section class="workspace-shell">
    <div v-if="!store.activeSessionId" class="workspace-empty">
      <div class="empty-content">
        <div class="hero-mark">
          <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
        </div>
        <h2>有什么我可以帮您分析的?</h2>
        <p>直接描述您的监管需求，智能体将自动分发到对应的工作流</p>

        <div class="task-grid">
          <button
            v-for="action in quickActions"
            :key="action.label"
            type="button"
            class="task-card"
            @click="handleQuickAction(action.prompt)"
          >
            <div :class="['task-icon', action.color]" v-html="action.icon"></div>
            <div class="task-title">{{ action.label }}</div>
            <div class="task-desc">{{ action.desc }}</div>
          </button>
        </div>
      </div>
    </div>

    <div v-else class="conversation-shell">


      <div ref="messageListRef" class="message-list">
        <div
          v-for="msg in store.activeMessages"
          :key="msg.id"
          :class="['message-row', `role-${msg.role}`]"
        >
          <MessageRenderer :message="msg" />
        </div>

        <div v-if="store.sending && !store.streaming" class="message-row role-assistant">
          <div class="avatar agent-avatar">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"/></svg>
          </div>
          <div class="thinking-card">
            <div class="pulsing-halo">
              <div class="pulse-core"></div>
            </div>
            <div class="thinking-text">
              <div class="thinking-main">
                智能体调度与推理中<span class="dot-anim"><span>.</span><span>.</span><span>.</span></span>
              </div>
              <div class="thinking-steps-hint">
                <span class="hint-item">&#x2713; 场景识别</span>
                <span class="hint-sep">&rarr;</span>
                <span class="hint-item hint-active">文档解析</span>
                <span class="hint-sep">&rarr;</span>
                <span class="hint-item">规则命中</span>
                <span class="hint-sep">&rarr;</span>
                <span class="hint-item">报告生成</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="composer-dock">
      <WorkspaceComposer />
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import MessageRenderer from '../../components/MessageRenderer.vue';
import WorkspaceComposer from './WorkspaceComposer.vue';

const store = useAgentStore();
const messageListRef = ref<HTMLElement>();

const quickActions = [
  {
    icon: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>',
    color: 'indigo',
    label: '标书审查',
    desc: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
  },
  {
    icon: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10.4 12.6a2 2 0 1 1 3 3L8 21l-4 1 1-4Z"/><path d="M16 10.5 22 16"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M22 6l-6-6"/><path d="M7 21h10a2 2 0 0 0 2-2V8m0 0H14a2 2 0 0 1-2-2V2H7a2 2 0 0 0-2 2v10.5"/></svg>',
    color: 'green',
    label: '合同预审',
    desc: '审查最新版采购合同，基于合规知识库提取潜在风险条款。',
    prompt: '审查最新版采购合同，基于合规知识库提取潜在风险条款。',
  },
  {
    icon: '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>',
    color: 'amber',
    label: '合规预警',
    desc: '分析近 3 个月骨科耗材采购数据，生成异常波动预警报告。',
    prompt: '分析近 3 个月骨科耗材采购数据，生成异常波动预警报告。',
  },
];

// 统一的滚动到底部处理
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
    }
  });
}

// 仅在用户发送消息或上传文件时滚动
watch(
  () => store.sending || store.uploading,
  (isActive) => {
    if (isActive) {
      scrollToBottom();
    }
  }
);

// 监听消息列表本身改变（例如切换会话、初次加载、发新消息等）
watch(
  () => store.activeMessages.length,
  () => {
    scrollToBottom();
  }
);

// 监听 activeSessionId 改变自动到底部
watch(
  () => store.activeSessionId,
  () => {
    scrollToBottom();
  }
);

// 流式内容变化时滚动到底部
watch(
  () => store.streamingContent,
  () => {
    if (store.streaming) {
      scrollToBottom();
    }
  }
);

async function handleQuickAction(prompt: string) {
  if (!store.activeSessionId) {
    await store.createSession();
  }
  await store.sendMessage(prompt);
}
</script>

<style scoped>
.workspace-shell {
  position: relative;
  display: flex;
  height: calc(100vh - 74px);
  flex-direction: column;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
}

.workspace-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 40px 48px 120px;
}

.empty-content {
  width: min(1160px, 100%);
  text-align: center;
}

.hero-mark {
  display: flex;
  width: 72px;
  height: 72px;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #38bdf8, #818cf8, #c084fc);
  color: #fff;
  box-shadow: 0 16px 32px rgba(129, 140, 248, 0.25);
}

.empty-content h2 {
  margin: 0 0 12px;
  color: #0f172a;
  font-size: clamp(32px, 3.5vw, 48px);
  line-height: 1.08;
  font-weight: 800;
}

.empty-content p {
  margin: 0;
  color: #7d90aa;
  font-size: 18px;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
  margin-top: 58px;
}

.task-card {
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  background: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05);
  padding: 24px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.task-card:hover {
  transform: translateY(-4px);
  border-color: #cbd5e1;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
}

.task-icon {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  margin-bottom: 20px;
}

.task-icon.indigo {
  background: #e0e7ff;
  color: #6366f1;
}

.task-icon.green {
  background: #dcfce7;
  color: #22c55e;
}

.task-icon.amber {
  background: #fef3c7;
  color: #f59e0b;
}

.task-title {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 8px;
}

.task-desc {
  color: #64748b;
  font-size: 15px;
  line-height: 1.6;
}

.conversation-shell {
  flex: 1;
  min-height: 0;
  padding: 22px 0 228px;
  position: relative;
}

.conversation-shell::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 48px;
  background: linear-gradient(180deg, #ffffff 10%, rgba(255, 255, 255, 0) 100%);
  z-index: 10;
  pointer-events: none;
}

.message-list {
  height: 100%;
  overflow-y: auto;
  padding: 0 48px;
}

.message-row {
  display: flex;
  margin-bottom: 18px;
}

.role-user {
  justify-content: flex-end;
}

.role-assistant,
.role-system {
  justify-content: flex-start;
}

.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.agent-avatar {
  background: linear-gradient(135deg, #818cf8, #c084fc);
  color: #fff;
  box-shadow: 0 4px 10px rgba(139, 92, 246, 0.2);
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-content {
  padding: 14px 18px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;
}

.thinking-card {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border: 1px solid rgba(59, 130, 246, 0.16);
  border-radius: 12px;
  border-bottom-left-radius: 4px;
  background: #f8fafc;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.05);
}

.pulsing-halo {
  position: relative;
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pulse-core {
  width: 8px;
  height: 8px;
  background: #3b82f6;
  border-radius: 50%;
}

.pulsing-halo::before {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  border: 2px solid #3b82f6;
  border-radius: 50%;
  animation: pulse-ring 1.5s cubic-bezier(0.215, 0.61, 0.355, 1) infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.6); opacity: 1; }
  100% { transform: scale(1.6); opacity: 0; }
}

.thinking-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.thinking-main {
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

.thinking-sub {
  color: #64748b;
  font-size: 12px;
}

/* 打点动画 */
.dot-anim span {
  display: inline-block;
  animation: dot-bounce 1.4s infinite both;
}
.dot-anim span:nth-child(2) { animation-delay: 0.2s; }
.dot-anim span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.3; }
  40% { transform: translateY(-4px); opacity: 1; }
}

/* 流水线步骤提示 */
.thinking-steps-hint {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 4px;
  flex-wrap: wrap;
}

.hint-item {
  font-size: 11px;
  color: #94a3b8;
  transition: color 0.3s;
}

.hint-item.hint-active {
  color: #3b82f6;
  font-weight: 600;
}

.hint-sep {
  font-size: 11px;
  color: #cbd5e1;
}

.composer-dock {
  position: absolute;
  left: 42px;
  right: 42px;
  bottom: 18px;
}

@media (max-width: 1200px) {
  .task-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .workspace-shell {
    height: auto;
    min-height: calc(100vh - 74px);
  }

  .workspace-empty {
    padding: 36px 20px 244px;
  }

  .message-list {
    padding: 0 20px;
  }

  .composer-dock {
    left: 16px;
    right: 16px;
  }
}



/* 流式输出样式 */
.assistant-content {
  background: #ffffff;
  color: #0f172a;
  border-bottom-left-radius: 6px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px -2px rgba(0, 0, 0, 0.04);
}
</style>
