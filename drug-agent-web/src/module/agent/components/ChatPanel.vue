<template>
  <div class="chat-panel">
    <div v-if="!store.activeSessionId" class="welcome-state">
      <div class="welcome-content">
        <div class="welcome-logo">✦</div>
        <h2>有什么我可以帮您分析的?</h2>
        <p>直接描述您的监管需求，智能体将自动分发到对应的工作流</p>

        <div class="quick-actions">
          <button
            v-for="action in quickActions"
            :key="action.label"
            type="button"
            class="quick-card"
            @click="handleQuickAction(action.prompt)"
          >
            <div class="quick-icon" :class="action.iconClass">{{ action.icon }}</div>
            <div class="quick-title">{{ action.label }}</div>
            <div class="quick-desc">{{ action.desc }}</div>
          </button>
        </div>
      </div>
    </div>

    <div v-else class="chat-shell">
      <div class="message-list" ref="messageListRef">
        <div
          v-for="msg in store.activeMessages"
          :key="msg.id"
          :class="['message-item', `message-${msg.role}`]"
        >
          <MessageRenderer :message="msg" />
        </div>

        <div v-if="store.sending" class="message-item message-assistant">
          <div class="avatar agent-avatar">
            <span>🤖</span>
          </div>
          <div class="loading-indicator">
            <t-loading />
            <span>智能体正在思考...</span>
          </div>
        </div>
      </div>
    </div>

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

const quickActions = [
  {
    icon: '▣',
    iconClass: 'indigo',
    label: '标书审查',
    desc: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    prompt: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
  },
  {
    icon: '◈',
    iconClass: 'green',
    label: '合同预审',
    desc: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
    prompt: '审查最新版本的采购合同，基于合规知识库提取潜在风险条款。',
  },
  {
    icon: '△',
    iconClass: 'amber',
    label: '合规预警',
    desc: '分析近 3 个月的骨科耗材采购数据，生成异常波动预警报告。',
    prompt: '分析近 3 个月的骨科耗材采购数据，生成异常波动预警报告。',
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
.chat-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.welcome-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px 220px;
}

.welcome-content {
  width: min(1160px, 100%);
  text-align: center;
}

.welcome-logo {
  width: 92px;
  height: 92px;
  margin: 0 auto 28px;
  border-radius: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3b82f6, #4f46e5);
  color: white;
  font-size: 42px;
  box-shadow: 0 20px 36px rgba(79, 70, 229, 0.22);
}

.welcome-content h2 {
  margin: 0 0 12px;
  font-size: 58px;
  line-height: 1.1;
  font-weight: 800;
  color: #20314d;
}

.welcome-content p {
  margin: 0;
  font-size: 18px;
  color: #7183a0;
}

.quick-actions {
  margin-top: 62px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.quick-card {
  border: 1px solid #dfe7f2;
  background: #fff;
  border-radius: 28px;
  padding: 26px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.04);
}

.quick-icon {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 18px;
}

.quick-icon.indigo {
  background: #eef2ff;
  color: #5b5cf0;
}

.quick-icon.green {
  background: #ecfdf5;
  color: #10b981;
}

.quick-icon.amber {
  background: #fff7ed;
  color: #f59e0b;
}

.quick-title {
  font-size: 22px;
  font-weight: 800;
  color: #243552;
  margin-bottom: 8px;
}

.quick-desc {
  font-size: 16px;
  line-height: 1.7;
  color: #7183a0;
}

.chat-shell {
  flex: 1;
  min-height: 0;
  padding: 24px 0 230px;
}

.message-list {
  height: 100%;
  overflow-y: auto;
  padding: 20px 48px;
}

.message-item {
  margin-bottom: 24px;
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
  gap: 12px;
  padding: 14px 18px;
  background: #f7f9fc;
  border-radius: 18px;
  color: #61748f;
  font-size: 14px;
  border: 1px solid rgba(19, 49, 59, 0.06);
}

.agent-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.composer-area {
  position: absolute;
  left: 44px;
  right: 44px;
  bottom: 18px;
}

@media (max-width: 1200px) {
  .welcome-content h2 {
    font-size: 44px;
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
