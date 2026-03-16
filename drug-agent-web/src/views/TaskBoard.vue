<template>
  <div class="board-page">
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

    <section class="board-main">
      <div class="board-shell">
        <header class="board-header">
          <div>
            <h1>Agent 任务调度看板</h1>
            <p>管理并复核由通用 Agent 引擎驱动的所有自动化审查任务</p>
          </div>

          <button class="filter-btn" type="button">
            <el-icon><Filter /></el-icon>
            <span>高级筛选</span>
          </button>
        </header>

        <section class="summary-grid">
          <article
            v-for="item in summaryCards"
            :key="item.label"
            class="summary-card"
            :class="item.accent"
          >
            <div class="summary-icon" :class="item.theme">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="summary-meta">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </div>
          </article>
        </section>

        <section class="scene-tabs">
          <button
            v-for="tab in sceneTabs"
            :key="tab.key"
            class="scene-tab"
            :class="{ active: activeTab === tab.key }"
            type="button"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </section>

        <section class="task-grid">
          <article
            v-for="task in filteredTasks"
            :key="task.id"
            class="task-card"
            :class="task.cardClass"
          >
            <div class="task-head">
              <div class="task-type">
                <div class="task-type-icon" :class="task.iconTheme">
                  <el-icon><component :is="task.icon" /></el-icon>
                </div>
                <span>{{ task.type }}</span>
              </div>
              <span v-if="task.statusTag" class="status-tag" :class="task.statusTagClass">
                {{ task.statusTag }}
              </span>
            </div>

            <h3>{{ task.title }}</h3>
            <p class="task-id">{{ task.id }}</p>

            <div class="risk-box" :class="task.riskClass">
              <div class="risk-title">{{ task.riskTitle }}</div>
              <div class="risk-desc">{{ task.riskDesc }}</div>
            </div>

            <div v-if="task.progress !== undefined" class="progress-wrap">
              <div class="progress-meta">
                <span>{{ task.progressLabel }}</span>
                <strong>{{ task.progress }}%</strong>
              </div>
              <div class="progress-track">
                <div class="progress-bar" :style="{ width: `${task.progress}%` }"></div>
              </div>
            </div>

            <div class="task-footer">
              <div class="task-time">
                <el-icon><Clock /></el-icon>
                <span>{{ task.timeText }}</span>
              </div>
              <button class="task-action" :class="task.actionClass" type="button">
                {{ task.actionText }}
              </button>
            </div>
          </article>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Clock,
  Document,
  Files,
  Filter,
  List,
  Monitor,
  QuestionFilled,
  Reading,
  RefreshLeft,
  Setting,
  Warning
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const activeTab = ref('all')

const workspaceItems = [
  { key: 'hub', label: '工作台 (Agent Hub)', icon: Monitor, path: '/agent' },
  { key: 'tasks', label: '任务看板', icon: List, path: '/agent/tasks' },
  { key: 'knowledge', label: '合规知识库', icon: Reading },
  { key: 'audit', label: '全局审计日志', icon: RefreshLeft }
]

const settingItems = [
  { key: 'prefs', label: '偏好与配置', icon: Setting },
  { key: 'help', label: '使用帮助', icon: QuestionFilled }
]

const summaryCards = [
  { label: '执行中任务', value: '1', icon: RefreshLeft, theme: 'theme-blue', accent: 'accent-blue' },
  { label: '待人工复核', value: '1', icon: Monitor, theme: 'theme-gold', accent: 'accent-gold' },
  { label: '检出高风险项', value: '1', icon: Warning, theme: 'theme-red', accent: 'accent-red' }
]

const sceneTabs = [
  { key: 'all', label: '全部场景' },
  { key: 'tender', label: '标书审查' },
  { key: 'contract', label: '合同预审' },
  { key: 'risk', label: '合规预警' }
]

const taskItems = [
  {
    id: 'T-001',
    scene: 'tender',
    type: '标书审查',
    icon: Document,
    iconTheme: 'theme-indigo',
    title: '年度设备采购标书分析',
    riskTitle: '高风险',
    riskDesc: '发现 87% 语义重合，疑似围标',
    riskClass: 'risk-red',
    timeText: '10分钟前',
    actionText: '查看审查报告',
    actionClass: 'action-muted',
    cardClass: ''
  },
  {
    id: 'T-002',
    scene: 'contract',
    type: '合同预审',
    icon: Files,
    iconTheme: 'theme-green',
    title: '骨科耗材供应商框架协议审核',
    riskTitle: '中风险',
    riskDesc: '提取到 3 条合规风险条款，需人工确认',
    riskClass: 'risk-gold',
    timeText: '待处理',
    actionText: '立即人工复核',
    actionClass: 'action-gold',
    statusTag: '待复核',
    statusTagClass: 'tag-gold',
    cardClass: 'card-gold'
  },
  {
    id: 'T-003',
    scene: 'risk',
    type: '合规预警',
    icon: Warning,
    iconTheme: 'theme-amber',
    title: 'Q1 耗材价格异常波动扫描',
    riskTitle: '分析中',
    riskDesc: '正在进行同比环比计算...',
    riskClass: 'risk-slate',
    timeText: 'Agent 推理中...',
    actionText: '查看实时日志',
    actionClass: 'action-muted',
    progress: 65,
    progressLabel: 'Agent 推理中...',
    cardClass: ''
  },
  {
    id: 'T-004',
    scene: 'tender',
    type: '标书审查',
    icon: Document,
    iconTheme: 'theme-indigo',
    title: '心血管介入类试剂标书查重',
    riskTitle: '低风险',
    riskDesc: '未见明显异常',
    riskClass: 'risk-green',
    timeText: '2小时前',
    actionText: '查看审查报告',
    actionClass: 'action-muted',
    cardClass: ''
  }
]

const activeMenu = computed(() => {
  if (route.path.startsWith('/agent/tasks')) return 'tasks'
  return 'hub'
})

const filteredTasks = computed(() => {
  if (activeTab.value === 'all') return taskItems
  return taskItems.filter((item) => item.scene === activeTab.value)
})

const handleMenuClick = (item) => {
  if (item.path) {
    router.push(item.path)
  }
}
</script>

<style scoped>
.board-page {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: calc(100vh - 72px);
  background: linear-gradient(180deg, #f8fbff 0%, #f5f8fc 100%);
}

.workspace-sidebar {
  background: rgba(255, 255, 255, 0.9);
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
  height: 58px;
  border: 0;
  border-radius: 20px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 14px;
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
}

.sidebar-item.active {
  background: #1f2a44;
  color: #fff;
  box-shadow: 0 12px 26px rgba(31, 42, 68, 0.2);
}

.sidebar-item .el-icon {
  font-size: 20px;
}

.board-main {
  min-width: 0;
}

.board-shell {
  max-width: 1380px;
  margin: 0 auto;
  padding: 36px 36px 48px;
}

.board-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.board-header h1 {
  margin: 0;
  font-size: 28px;
  color: #1f2a44;
}

.board-header p {
  margin: 12px 0 0;
  font-size: 16px;
  color: #71829e;
}

.filter-btn {
  height: 56px;
  border-radius: 18px;
  padding: 0 24px;
  border: 1px solid #dce5f1;
  background: rgba(255, 255, 255, 0.94);
  color: #42536f;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  box-shadow: 0 10px 28px rgba(80, 99, 133, 0.08);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin-top: 28px;
}

.summary-card {
  min-height: 138px;
  border-radius: 26px;
  padding: 22px 26px;
  border: 1px solid #dce5f1;
  background: #fff;
  box-shadow: 0 12px 28px rgba(80, 99, 133, 0.08);
  display: flex;
  align-items: center;
  gap: 18px;
}

.accent-gold {
  box-shadow: inset 0 -5px 0 #ffb400, 0 12px 28px rgba(80, 99, 133, 0.08);
}

.accent-red {
  box-shadow: inset 0 -5px 0 #ff4268, 0 12px 28px rgba(80, 99, 133, 0.08);
}

.summary-icon {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  display: grid;
  place-items: center;
  font-size: 30px;
}

.theme-blue {
  background: #edf4ff;
  color: #3c68f5;
}

.theme-gold {
  background: #fff7e6;
  color: #d98200;
}

.theme-red {
  background: #ffecef;
  color: #ea3c5a;
}

.theme-indigo {
  background: #eef0ff;
  color: #4c5dff;
}

.theme-green {
  background: #e9fbf4;
  color: #0eaf7f;
}

.theme-amber {
  background: #fff7e4;
  color: #db8a13;
}

.summary-meta strong {
  display: block;
  font-size: 42px;
  color: #1f2a44;
  line-height: 1;
}

.summary-meta span {
  display: block;
  margin-top: 10px;
  font-size: 16px;
  color: #8c9ab0;
  font-weight: 700;
}

.scene-tabs {
  display: flex;
  gap: 20px;
  margin-top: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid #dde6f1;
}

.scene-tab {
  height: 48px;
  padding: 0 28px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #61728d;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}

.scene-tab.active {
  background: #1f2a44;
  color: #fff;
  box-shadow: 0 12px 24px rgba(31, 42, 68, 0.18);
}

.task-grid {
  margin-top: 28px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.task-card {
  border-radius: 30px;
  padding: 28px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid #dce5f1;
  box-shadow: 0 12px 30px rgba(80, 99, 133, 0.06);
}

.card-gold {
  border-color: #ffc331;
}

.task-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.task-type {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #4c5dff;
  font-size: 15px;
  font-weight: 700;
}

.task-type-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-size: 24px;
}

.status-tag {
  height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
}

.tag-gold {
  background: #fff3d3;
  color: #d98900;
}

.task-card h3 {
  margin: 28px 0 0;
  font-size: 24px;
  line-height: 1.35;
  color: #1f2a44;
}

.task-id {
  margin: 6px 0 0;
  color: #99a7bb;
  font-size: 14px;
  font-weight: 600;
}

.risk-box {
  margin-top: 26px;
  border-radius: 18px;
  padding: 18px 20px;
  border: 1px solid transparent;
}

.risk-title {
  font-size: 16px;
  font-weight: 800;
}

.risk-desc {
  margin-top: 10px;
  color: #52637f;
  font-size: 15px;
  line-height: 1.5;
}

.risk-red {
  background: #fff1f3;
  border-color: #ffb8c2;
  color: #ef3b5d;
}

.risk-gold {
  background: #fff9e9;
  border-color: #ffd566;
  color: #de8f00;
}

.risk-slate {
  background: #f6f8fc;
  border-color: #d9e2f0;
  color: #92a2ba;
}

.risk-green {
  background: #ebfbf2;
  border-color: #95efc1;
  color: #11a86d;
}

.progress-wrap {
  margin-top: 26px;
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #3a67ea;
  font-weight: 700;
}

.progress-track {
  margin-top: 10px;
  height: 10px;
  background: #e9eef8;
  border-radius: 999px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #4b75ff, #3564ef);
  border-radius: inherit;
}

.task-footer {
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid #e5edf6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.task-time {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #8a9ab0;
  font-size: 14px;
}

.task-action {
  height: 48px;
  border: 0;
  border-radius: 16px;
  padding: 0 22px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}

.action-muted {
  background: #f2f5fa;
  color: #42536f;
}

.action-gold {
  background: linear-gradient(180deg, #ffb51e, #ff9f0a);
  color: #fff;
  box-shadow: 0 12px 24px rgba(255, 159, 10, 0.28);
}

@media (max-width: 1200px) {
  .board-page {
    grid-template-columns: 1fr;
  }

  .workspace-sidebar {
    border-right: 0;
    border-bottom: 1px solid #dfe7f2;
  }

  .summary-grid,
  .task-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .board-shell {
    padding: 24px 16px 36px;
  }

  .board-header,
  .task-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .scene-tabs {
    overflow-x: auto;
    padding-bottom: 16px;
  }

  .scene-tab {
    white-space: nowrap;
  }
}
</style>
