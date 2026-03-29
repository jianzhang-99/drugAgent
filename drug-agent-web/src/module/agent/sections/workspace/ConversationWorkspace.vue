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

        <div v-if="store.sending" class="message-row role-assistant">
          <div class="thinking-card">
            <t-loading />
            <span>智能体正在执行深层审查流程...</span>
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

.thinking-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  border: 1px solid rgba(19, 49, 59, 0.08);
  border-radius: 18px;
  background: #f7f9fc;
  color: #64758e;
  font-size: 14px;
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
</style>
