<template>
  <div class="workspace-page">
    <aside class="workspace-sidebar">
      <section class="sidebar-group">
        <p class="sidebar-label">我的空间</p>
        <button
          v-for="item in workspaceItems"
          :key="item.key"
          class="sidebar-item"
          :class="{ active: item.key === activeMenu }"
          type="button"
          @click="handleMenuClick(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </section>

      <section class="sidebar-group sidebar-settings">
        <p class="sidebar-label">系统设置</p>
        <button
          v-for="item in settingItems"
          :key="item.key"
          class="sidebar-item"
          type="button"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </section>
    </aside>

    <section class="workspace-main">
      <div class="workspace-content">
        <section class="hero-panel">
          <div class="hero-mark">
            <div class="hero-mark-inner">✦</div>
          </div>
          <h1>有什么我可以帮您分析的？</h1>
          <p>直接描述您的监管需求，或上传附件交由 Agent 处理</p>
        </section>

        <section class="intent-box">
          <textarea
            v-model="promptText"
            class="intent-input"
            placeholder="例如：帮我审查一下本周上传的三个医用耗材标书是否有围标嫌疑..."
          />

          <div class="intent-toolbar">
            <div class="toolbar-left">
              <button class="tool-btn" type="button" aria-label="上传附件">
                <el-icon><Upload /></el-icon>
              </button>
              <button class="tool-btn" type="button" aria-label="知识库引用">
                <el-icon><Notebook /></el-icon>
              </button>
            </div>

            <button class="route-btn" type="button">
              <el-icon><Position /></el-icon>
              <span>执行路由</span>
            </button>
          </div>
        </section>

        <section class="suggest-section">
          <h2>常用监管工作流建议</h2>

          <div class="suggest-grid">
            <article
              v-for="card in workflowCards"
              :key="card.title"
              class="suggest-card"
            >
              <div class="suggest-icon" :class="card.theme">
                <el-icon><component :is="card.icon" /></el-icon>
              </div>
              <h3>{{ card.title }}</h3>
              <p>{{ card.description }}</p>
            </article>
          </div>
        </section>

        <p class="bottom-tip">
          Agent 会自动将您的指令解析为 `AgentContext`，并在右上角分配后台任务。
        </p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Document,
  Files,
  Notebook,
  Position,
  Setting,
  Upload,
  Warning,
  Reading,
  List,
  RefreshLeft,
  QuestionFilled
} from '@element-plus/icons-vue'

const promptText = ref('')
const route = useRoute()
const router = useRouter()

const workspaceItems = [
  { key: 'hub', label: '工作台 (Agent Hub)', icon: Position, path: '/agent' },
  { key: 'tasks', label: '任务看板', icon: List, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading },
  { key: 'audit', label: '全局审计日志', icon: RefreshLeft }
]

const settingItems = [
  { key: 'prefs', label: '偏好与配置', icon: Setting },
  { key: 'help', label: '使用帮助', icon: QuestionFilled }
]

const workflowCards = [
  {
    title: '标书审查',
    description: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
    icon: Document,
    theme: 'theme-indigo'
  },
  {
    title: '合同预审',
    description: '审查最新版的采购合同，基于合规知识库提取潜在风险条款。',
    icon: Files,
    theme: 'theme-green'
  },
  {
    title: '合规预警',
    description: '分析近 3 个月的骨科耗材采购数据，生成异常波动预警报告。',
    icon: Warning,
    theme: 'theme-amber'
  }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  return 'hub'
})

const handleMenuClick = (item) => {
  if (item.path) {
    router.push(item.path)
  }
}
</script>

<style scoped>
.workspace-page {
  display: grid;
  grid-template-columns: 398px minmax(0, 1fr);
  min-height: calc(100vh - 86px);
  background: linear-gradient(180deg, #f8fbff 0%, #f5f8fc 100%);
}

.workspace-sidebar {
  background: rgba(255, 255, 255, 0.88);
  border-right: 1px solid #dfe7f2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 28px 18px 24px;
}

.sidebar-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-settings {
  padding-top: 36px;
  border-top: 1px solid #e8eef7;
}

.sidebar-label {
  margin: 0 0 8px 18px;
  font-size: 13px;
  font-weight: 700;
  color: #91a1ba;
}

.sidebar-item {
  height: 64px;
  border: 0;
  border-radius: 24px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 18px;
  color: #5c6c85;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.sidebar-item:hover {
  background: #eef4ff;
  color: #315ddc;
  transform: translateX(2px);
}

.sidebar-item.active {
  background: #e7f0ff;
  color: #315ddc;
}

.sidebar-item .el-icon {
  font-size: 22px;
}

.workspace-main {
  min-width: 0;
}

.workspace-content {
  max-width: 1440px;
  margin: 0 auto;
  padding: 52px 40px 48px;
}

.hero-panel {
  text-align: center;
  padding-top: 86px;
}

.hero-mark {
  display: flex;
  justify-content: center;
  margin-bottom: 22px;
}

.hero-mark-inner {
  width: 88px;
  height: 88px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  font-size: 40px;
  color: #fff;
  background: linear-gradient(135deg, #4e87ff 0%, #4c5dff 100%);
  box-shadow: 0 24px 60px rgba(87, 116, 255, 0.28);
}

.hero-panel h1 {
  margin: 0;
  font-size: 42px;
  line-height: 1.12;
  color: #24324a;
  font-weight: 800;
}

.hero-panel p {
  margin: 14px 0 0;
  font-size: 16px;
  color: #7d8da8;
  font-weight: 600;
}

.intent-box {
  margin: 44px auto 0;
  max-width: 1380px;
  min-height: 288px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #dce6f4;
  box-shadow: 0 18px 46px rgba(70, 95, 140, 0.08);
  padding: 28px 30px 24px;
}

.intent-input {
  width: 100%;
  min-height: 168px;
  border: 0;
  resize: none;
  padding: 0;
  font-size: 18px;
  line-height: 1.6;
  color: #2d3d57;
  background: transparent;
  outline: none;
  font-family: inherit;
}

.intent-input::placeholder {
  color: #bbc4d3;
  font-weight: 600;
}

.intent-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.toolbar-left {
  display: flex;
  gap: 18px;
}

.tool-btn {
  width: 40px;
  height: 40px;
  border: 0;
  background: transparent;
  color: #8ea0bd;
  font-size: 20px;
  display: grid;
  place-items: center;
  cursor: pointer;
}

.route-btn {
  height: 56px;
  min-width: 186px;
  border: 0;
  border-radius: 999px;
  padding: 0 22px;
  background: #c7d5e8;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.route-btn .el-icon {
  font-size: 16px;
}

.suggest-section {
  margin-top: 54px;
}

.suggest-section h2 {
  margin: 0 0 28px 10px;
  font-size: 16px;
  color: #8fa0ba;
  font-weight: 700;
}

.suggest-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 26px;
}

.suggest-card {
  min-height: 210px;
  border-radius: 24px;
  padding: 22px 24px 24px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid #dce6f4;
  box-shadow: 0 14px 38px rgba(70, 95, 140, 0.06);
}

.suggest-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 24px;
  margin-bottom: 18px;
}

.theme-indigo {
  color: #4c5dff;
  background: #eef0ff;
}

.theme-green {
  color: #0eaf7f;
  background: #e9fbf4;
}

.theme-amber {
  color: #db8a13;
  background: #fff7e4;
}

.suggest-card h3 {
  margin: 0;
  font-size: 18px;
  color: #24324a;
}

.suggest-card p {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.55;
  color: #6d7f9a;
  font-weight: 500;
}

.bottom-tip {
  margin: 40px 0 0;
  text-align: center;
  font-size: 14px;
  color: #8ea0bd;
  font-weight: 600;
}

@media (max-width: 1500px) {
  .workspace-page {
    grid-template-columns: 320px minmax(0, 1fr);
  }

  .hero-panel h1 {
    font-size: 36px;
  }

  .hero-panel p,
  .intent-input {
    font-size: 17px;
  }

  .suggest-card h3 {
    font-size: 17px;
  }

  .suggest-card p {
    font-size: 14px;
  }
}

@media (max-width: 1100px) {
  .workspace-page {
    grid-template-columns: 1fr;
  }

  .workspace-sidebar {
    border-right: 0;
    border-bottom: 1px solid #dfe7f2;
    gap: 24px;
  }

  .suggest-grid {
    grid-template-columns: 1fr;
  }

  .workspace-content {
    padding: 28px 20px 40px;
  }

  .hero-panel {
    padding-top: 18px;
  }
}

@media (max-width: 760px) {
  .sidebar-item {
    font-size: 18px;
    height: 56px;
  }

  .hero-mark-inner {
    width: 74px;
    height: 74px;
    font-size: 34px;
  }

  .hero-panel h1 {
    font-size: 28px;
  }

  .hero-panel p {
    font-size: 15px;
  }

  .intent-box {
    border-radius: 28px;
    padding: 24px 20px 20px;
    min-height: 280px;
  }

  .intent-input {
    min-height: 160px;
    font-size: 16px;
  }

  .intent-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 18px;
  }

  .route-btn {
    width: 100%;
  }
}
</style>
