<template>
  <workspace-layout>
    <section class="board-container">
      <header class="board-header">
        <div class="header-text">
          <h1>任务调度看板</h1>
          <p>实时管理由通用 Agent 引擎驱动的合规审查链路</p>
        </div>
        <button class="filter-trigger">
          <el-icon><Filter /></el-icon>
          <span>高级过滤</span>
        </button>
      </header>

      <div class="stats-overview">
        <div 
          v-for="stat in summaryStats" 
          :key="stat.label" 
          class="stat-pill"
          :class="stat.accent"
        >
          <div class="stat-icon" :class="stat.theme">
            <el-icon><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
        </div>
      </div>

      <nav class="category-nav">
        <button
          v-for="cat in categories"
          :key="cat.id"
          class="nav-item"
          :class="{ active: activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >
          {{ cat.label }}
        </button>
      </nav>

      <div class="tasks-vault">
        <article 
          v-for="task in filteredTasks" 
          :key="task.id" 
          class="glass-task-card"
          :class="{ 'highlight': task.isUrgent }"
        >
          <div class="card-top">
            <div class="type-badge" :style="{ '--badge-color': task.color }">
              <el-icon><component :is="task.icon" /></el-icon>
              <span>{{ task.type }}</span>
            </div>
            <span v-if="task.tag" class="status-pill" :class="task.tagClass">{{ task.tag }}</span>
          </div>

          <h3 class="task-title">{{ task.title }}</h3>
          <p class="task-sn">UID: {{ task.id }}</p>

          <div class="risk-indicator" :class="task.riskLevel">
            <div class="indicator-head">{{ task.riskLabel }}</div>
            <div class="indicator-body">{{ task.riskMsg }}</div>
          </div>

          <div v-if="task.progress" class="agent-stepper">
            <div class="step-meta">
              <span>推理进度</span>
              <strong>{{ task.progress }}%</strong>
            </div>
            <div class="step-track">
              <div class="step-fill" :style="{ width: task.progress + '%' }"></div>
            </div>
          </div>

          <footer class="card-footer">
            <div class="timestamp">
              <el-icon><Clock /></el-icon>
              <span>{{ task.time }}</span>
            </div>
            <button class="prime-action" :class="{ 'gold': task.isUrgent }">
              {{ task.action }}
            </button>
          </footer>
        </article>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Clock, Document, Files, Filter, RefreshLeft, Monitor, Warning } from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'

const activeCategory = ref('all')

const summaryStats = [
  { label: '执行中', value: '01', icon: RefreshLeft, theme: 'theme-blue', accent: 'acc-blue' },
  { label: '待复核', value: '01', icon: Monitor, theme: 'theme-gold', accent: 'acc-gold' },
  { label: '检出风险', value: '01', icon: Warning, theme: 'theme-red', accent: 'acc-red' }
]

const categories = [
  { id: 'all', label: '全场景' },
  { id: 'tender', label: '标书审查' },
  { id: 'contract', label: '合同预审' },
  { id: 'risk', label: '合规预警' }
]

const tasks = [
  {
    id: 'T-8821',
    scene: 'tender',
    type: '标书审查',
    icon: Document,
    color: '#4f46e5',
    title: '年度医疗设备招投标分析',
    riskLabel: '高风险命中',
    riskMsg: '系统检出 87% 的技术方案重合，疑似串通投标',
    riskLevel: 'risk-high',
    time: '12分钟前',
    action: '查看报告',
    isUrgent: false
  },
  {
    id: 'C-0912',
    scene: 'contract',
    type: '合同预审',
    icon: Files,
    color: '#059669',
    title: '骨科供应商战略合作协议',
    riskLabel: '待人工确认',
    riskMsg: '涉及 3 条合规敏感条款，Agent 无法自动放行',
    riskLevel: 'risk-wait',
    tag: '待处理',
    tagClass: 'pill-gold',
    time: '实时',
    action: '立即复核',
    isUrgent: true
  },
  {
    id: 'R-7731',
    scene: 'risk',
    type: '合规预警',
    icon: Warning,
    color: '#d97706',
    title: '耗材价格异常波动监测',
    riskLabel: '推理计算中',
    riskMsg: '正在基于近三月基准值进行异常偏移纠偏...',
    riskLevel: 'risk-info',
    progress: 72,
    time: '计算中',
    action: '实时日志',
    isUrgent: false
  },
  {
    id: 'T-8822',
    scene: 'tender',
    type: '标书审查',
    icon: Document,
    color: '#4f46e5',
    title: '心血管介入耗材查重',
    riskLabel: '合规',
    riskMsg: '未检出显著异常偏移，建议放行',
    riskLevel: 'risk-safe',
    time: '2小时前',
    action: '查看细节',
    isUrgent: false
  }
]

const filteredTasks = computed(() => {
  return activeCategory.value === 'all' 
    ? tasks 
    : tasks.filter(t => t.scene === activeCategory.value)
})
</script>

<style scoped>
.board-container {
  padding: 40px;
  max-width: 1400px;
  margin: 0 auto;
}

.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

h1 {
  font-size: 28px;
  font-weight: 850;
  margin: 0;
}

.header-text p {
  color: var(--text-sub);
  margin: 8px 0 0;
  font-weight: 500;
}

.filter-trigger {
  height: 48px;
  padding: 0 24px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  color: var(--text-main);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: var(--transition-smooth);
}

.filter-trigger:hover {
  border-color: var(--primary-color);
  transform: translateY(-2px);
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 40px;
}

.stat-pill {
  background: white;
  padding: 24px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: var(--shadow-sm);
}

.acc-gold { border-bottom: 4px solid var(--warning-color); }
.acc-red { border-bottom: 4px solid var(--danger-color); }
.acc-blue { border-bottom: 4px solid var(--primary-color); }

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-size: 28px;
}

.theme-blue { background: #eef2ff; color: #4f46e5; }
.theme-gold { background: #fffbeb; color: #d97706; }
.theme-red { background: #fef2f2; color: #dc2626; }

.stat-value {
  display: block;
  font-size: 32px;
  font-weight: 900;
  line-height: 1;
}

.stat-label {
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 700;
  margin-top: 4px;
  display: block;
}

.category-nav {
  display: flex;
  gap: 12px;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}

.nav-item {
  height: 40px;
  padding: 0 24px;
  border-radius: 99px;
  border: 0;
  background: transparent;
  color: var(--text-sub);
  font-weight: 700;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.nav-item.active {
  background: var(--text-main);
  color: white;
}

.tasks-vault {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.glass-task-card {
  background: var(--card-glass);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 28px;
  box-shadow: var(--shadow-sm);
  transition: var(--transition-smooth);
}

.glass-task-card.highlight {
  border-color: var(--warning-color);
  box-shadow: 0 12px 32px rgba(217, 119, 6, 0.1);
}

.glass-task-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.type-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--badge-color);
  font-weight: 800;
  font-size: 14px;
}

.status-pill {
  padding: 4px 12px;
  border-radius: 99px;
  font-size: 12px;
  font-weight: 800;
}

.pill-gold { background: #fff7ed; color: #c2410c; }

.task-title {
  margin: 24px 0 6px;
  font-size: 22px;
  font-weight: 800;
}

.task-sn {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
}

.risk-indicator {
  margin: 24px 0;
  padding: 20px;
  border-radius: var(--radius-md);
  border-left: 6px solid;
}

.risk-high { background: #fef2f2; border-color: #ef4444; color: #b91c1c; }
.risk-wait { background: #fffbeb; border-color: #f59e0b; color: #b45309; }
.risk-info { background: #f8fafc; border-color: #64748b; color: #334155; }
.risk-safe { background: #f0fdf4; border-color: #10b981; color: #047857; }

.indicator-head { font-weight: 900; font-size: 15px; }
.indicator-body { font-size: 14px; margin-top: 8px; opacity: 0.9; }

.agent-stepper { margin-bottom: 24px; }
.step-meta { display: flex; justify-content: space-between; font-weight: 700; font-size: 13px; color: var(--primary-color); }
.step-track { height: 8px; background: #e2e8f0; border-radius: 99px; margin-top: 8px; overflow: hidden; }
.step-fill { height: 100%; background: var(--primary-color); border-radius: inherit; }

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.timestamp { display: flex; align-items: center; gap: 8px; color: var(--text-muted); font-size: 13px; font-weight: 600; }
.prime-action {
  height: 44px;
  padding: 0 20px;
  border-radius: 12px;
  border: 0;
  background: var(--primary-bg);
  color: var(--primary-color);
  font-weight: 800;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.prime-action:hover { background: var(--primary-color); color: white; }
.prime-action.gold { background: var(--warning-color); color: white; box-shadow: 0 8px 20px rgba(217, 119, 6, 0.25); }

@media (max-width: 900px) {
  .tasks-vault, .stats-overview { grid-template-columns: 1fr; }
  .board-container { padding: 20px; }
}
</style>
