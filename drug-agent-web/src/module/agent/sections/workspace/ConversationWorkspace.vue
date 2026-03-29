<template>
  <section class="workspace-shell">
    <div v-if="!store.activeSessionId" class="workspace-empty">
      <div class="empty-content">
        <div class="hero-mark">✦</div>
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
            <div :class="['task-icon', action.color]">{{ action.icon }}</div>
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
    icon: '▣',
    color: 'indigo',
    label: '标书审查',
    desc: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
  },
  {
    icon: '◈',
    color: 'green',
    label: '合同预审',
    desc: '审查最新版采购合同，基于合规知识库提取潜在风险条款。',
    prompt: '审查最新版采购合同，基于合规知识库提取潜在风险条款。',
  },
  {
    icon: '△',
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
  width: 92px;
  height: 92px;
  align-items: center;
  justify-content: center;
  margin: 0 auto 30px;
  border-radius: 28px;
  background: linear-gradient(135deg, #3d79f7, #4b4de7);
  color: #fff;
  font-size: 40px;
  box-shadow: 0 24px 48px rgba(76, 93, 235, 0.24);
}

.empty-content h2 {
  margin: 0 0 10px;
  color: #20314c;
  font-size: clamp(40px, 4.2vw, 60px);
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
  border: 1px solid #dbe5f0;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
  padding: 22px 24px;
  text-align: left;
  cursor: pointer;
  transition: transform 140ms ease, box-shadow 140ms ease, border-color 140ms ease;
}

.task-card:hover {
  transform: translateY(-2px);
  border-color: #ccd9ea;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.07);
}

.task-icon {
  display: flex;
  width: 54px;
  height: 54px;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  margin-bottom: 18px;
  font-size: 24px;
}

.task-icon.indigo {
  background: #eef2ff;
  color: #595bf0;
}

.task-icon.green {
  background: #eafaf3;
  color: #18a571;
}

.task-icon.amber {
  background: #fff7eb;
  color: #e89a11;
}

.task-title {
  color: #233450;
  font-size: 21px;
  font-weight: 800;
  margin-bottom: 8px;
}

.task-desc {
  color: #6f82a0;
  font-size: 16px;
  line-height: 1.7;
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
