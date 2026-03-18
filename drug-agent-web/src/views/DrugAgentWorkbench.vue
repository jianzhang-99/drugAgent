<template>
  <workspace-layout>
    <section class="workbench-canvas">
      <div class="hero-section">
        <div class="hero-badge">
          <div class="badge-inner">✦</div>
        </div>
        <h1>有什么我可以帮您分析的？</h1>
        <p>描述您的监管需求，或直接上传附件交由 AI 引擎处理</p>
      </div>

      <div class="input-container">
        <textarea
          v-model="promptText"
          class="main-prompt"
          placeholder="例如：帮我审查本周上传的三个医用耗材标书是否有围标嫌疑..."
        />
        <div class="input-actions">
          <div class="action-group">
            <button class="icon-tool" title="上传附件"><el-icon><Upload /></el-icon></button>
            <button class="icon-tool" title="知识库"><el-icon><Notebook /></el-icon></button>
          </div>
          <button class="submit-trigger">
            <el-icon><Position /></el-icon>
            <span>执行指令</span>
          </button>
        </div>
      </div>

      <div class="quick-starts">
        <header class="section-header">
          <h2>常用监管工作流</h2>
        </header>
        <div class="cards-layout">
          <suggest-card
            v-for="card in workflowCards"
            :key="card.title"
            v-bind="card"
          />
        </div>
      </div>
      
      <footer class="legal-footer">
        Agent 会自动将您的指令解析为 AgentContext，任务执行详情可在看板中追踪。
      </footer>
    </section>
  </workspace-layout>
</template>

<script setup>
import { ref } from 'vue'
import { Document, Files, Notebook, Position, Upload, Warning } from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import SuggestCard from '../components/common/SuggestCard.vue'

const promptText = ref('')

const workflowCards = [
  {
    title: '标投标审查',
    description: '对比多份标单文件，深度识别语义碰撞、串标及异常围标风险。',
    icon: Document,
    theme: 'theme-indigo'
  },
  {
    title: '合同合规性',
    description: '基于最新行业规范，自动提取合同中的潜在法律风险与待议条款。',
    icon: Files,
    theme: 'theme-green'
  },
  {
    title: '价格预警',
    description: '分析特定耗材的历史采购趋势，生成异常价格波动及合规性预警。',
    icon: Warning,
    theme: 'theme-amber'
  }
]
</script>

<style scoped>
.workbench-canvas {
  max-width: 1100px;
  margin: 0 auto;
  padding: 80px 40px;
}

.hero-section {
  text-align: center;
  margin-bottom: 60px;
}

.hero-badge {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.badge-inner {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, var(--primary-color), var(--primary-deep));
  border-radius: 20px;
  display: grid;
  place-items: center;
  color: white;
  font-size: 32px;
  box-shadow: var(--shadow-lg);
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

h1 {
  font-size: 38px;
  font-weight: 850;
  letter-spacing: -0.5px;
  margin-bottom: 12px;
}

.hero-section p {
  font-size: 16px;
  color: var(--text-sub);
  font-weight: 500;
}

.input-container {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-md);
  margin-bottom: 60px;
  transition: var(--transition-smooth);
}

.input-container:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 4px var(--primary-bg);
}

.main-prompt {
  width: 100%;
  min-height: 140px;
  border: none;
  resize: none;
  outline: none;
  font-size: 18px;
  color: var(--text-main);
  font-family: inherit;
  margin-bottom: 16px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-group {
  display: flex;
  gap: 12px;
}

.icon-tool {
  width: 44px;
  height: 44px;
  border: 0;
  background: var(--primary-bg);
  color: var(--primary-color);
  border-radius: 12px;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.icon-tool:hover {
  background: var(--primary-color);
  color: white;
}

.submit-trigger {
  background: var(--primary-color);
  color: white;
  border: 0;
  padding: 0 28px;
  height: 48px;
  border-radius: 99px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.submit-trigger:hover {
  transform: scale(1.05);
  box-shadow: 0 8px 24px rgba(var(--brand-hull), 85%, 55%, 0.3);
}

.section-header h2 {
  font-size: 14px;
  text-transform: uppercase;
  color: var(--text-muted);
  letter-spacing: 1.5px;
  font-weight: 800;
  margin-bottom: 24px;
}

.cards-layout {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.legal-footer {
  margin-top: 60px;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .cards-layout { grid-template-columns: 1fr; }
  .workbench-canvas { padding: 40px 20px; }
  h1 { font-size: 28px; }
  .input-actions { flex-direction: column; gap: 20px; }
  .submit-trigger { width: 100%; }
}
</style>
