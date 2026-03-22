<template>
  <workspace-layout>
    <section class="board-container">
      <header class="board-header">
        <h1 class="board-title">任务调度看板</h1>
      </header>

      <div class="tasks-grid">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="task-card"
          :class="{ 'highlight': task.isUrgent || task.isRecent }"
          @click="handleTaskAction(task)"
        >
          <div class="card-top">
            <span class="scene-badge" :class="task.sceneClass">
              {{ task.scene }}
            </span>
            <span class="status-badge">
              {{ task.status }}
            </span>
          </div>

          <h3 class="task-name">{{ task.name }}</h3>
          <p class="task-id">{{ task.id }}</p>

          <div v-if="task.isRunning" class="progress-container">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: task.progress + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Clock, Document, Files, Filter, RefreshLeft, Monitor, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import { listTenderReviewCases } from '../api/drug-agent'
import { appendAuditLog, getRecentTenderTask, getTenderTasks, upsertTenderTask } from '../utils/local-state'

const route = useRoute()
const router = useRouter()
const activeCategory = ref('all')
const tenderTasks = ref([])

const categories = [
  { id: 'all', label: '全场景' },
  { id: 'tender', label: '标书审查' },
  { id: 'contract', label: '合同预审' },
  { id: 'risk', label: '合规预警' }
]

const mockTasks = [
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
    riskMsg: '涉及 3 条合规敏感条款，系统无法自动放行',
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

const recentTask = (() => {
  const parsed = getRecentTenderTask()
  if (!parsed?.caseId) return null
  return {
    id: parsed.caseId,
    scene: parsed.scene || 'tender',
    type: '标书审查',
    icon: Document,
    color: '#4f46e5',
    title: parsed.filenames?.length
      ? `新建标书审查任务（${parsed.filenames.length} 份文件）`
      : '新建标书审查任务',
    riskLabel: parsed.status === 'PENDING' ? '待解析' : parsed.status || '已创建',
    riskMsg: parsed.documentIds?.length
      ? `已创建任务，待处理文档 ${parsed.documentIds.length} 份`
      : '任务已创建，等待后续处理',
    riskLevel: 'risk-info',
    time: route.query.caseId === parsed.caseId ? '刚刚创建' : '最近创建',
    action: '查看报告',
    isUrgent: false,
    isRecent: true,
    tag: route.query.caseId === parsed.caseId ? '当前任务' : '最近任务',
    tagClass: 'pill-gold'
  }
})()

const formatRelativeTime = (createdAt) => {
  if (!createdAt) return '未知时间'
  const createdTime = new Date(createdAt).getTime()
  if (Number.isNaN(createdTime)) return '未知时间'

  const diffMs = Date.now() - createdTime
  const diffMinutes = Math.max(1, Math.floor(diffMs / 60000))

  if (diffMinutes < 60) return `${diffMinutes}分钟前`

  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours}小时前`

  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}天前`
}

const toTenderTaskCard = (task) => ({
  id: task.caseId,
  scene: 'tender',
  type: '标书审查',
  icon: Document,
  color: '#4f46e5',
  title: `标书审查任务（${task.documentIds?.length || 0} 份文件）`,
  riskLabel: task.status === 'PENDING' ? '待解析' : task.status,
  riskMsg: `提交人：${task.submittedBy || 'anonymous'}，待处理文档 ${task.documentIds?.length || 0} 份`,
  riskLevel: task.status === 'FAILED' ? 'risk-high' : 'risk-info',
  time: route.query.caseId === task.caseId ? '刚刚创建' : formatRelativeTime(task.createdAt),
  action: '查看报告',
  isUrgent: false,
  isRecent: route.query.caseId === task.caseId,
  tag: route.query.caseId === task.caseId ? '当前任务' : undefined,
  tagClass: route.query.caseId === task.caseId ? 'pill-gold' : undefined
})

const allTasks = computed(() => {
  const mergedTenderTasks = [...tenderTasks.value]

  if (recentTask && !mergedTenderTasks.some((task) => task.id === recentTask.id)) {
    mergedTenderTasks.unshift(recentTask)
  }

  return [...mergedTenderTasks, ...mockTasks.filter((task) => task.scene !== 'tender')]
})

const filteredTasks = computed(() => {
  return activeCategory.value === 'all'
    ? allTasks.value
    : allTasks.value.filter(t => t.scene === activeCategory.value)
})

const summaryStats = computed(() => {
  const taskList = allTasks.value
  return [
    {
      label: '执行中',
      value: String(taskList.filter((task) => ['risk', 'tender'].includes(task.scene) && ['risk-info'].includes(task.riskLevel)).length).padStart(2, '0'),
      icon: RefreshLeft,
      theme: 'theme-blue',
      accent: 'acc-blue'
    },
    {
      label: '待复核',
      value: String(taskList.filter((task) => task.riskLevel === 'risk-wait').length).padStart(2, '0'),
      icon: Monitor,
      theme: 'theme-gold',
      accent: 'acc-gold'
    },
    {
      label: '检出风险',
      value: String(taskList.filter((task) => task.riskLevel === 'risk-high').length).padStart(2, '0'),
      icon: Warning,
      theme: 'theme-red',
      accent: 'acc-red'
    }
  ]
})

const loadTenderTasks = async () => {
  try {
    const response = await listTenderReviewCases()
    const serverTasks = Array.isArray(response) ? response : []
    serverTasks.forEach(upsertTenderTask)
    const localTasks = getTenderTasks()
    const merged = [...serverTasks]
    localTasks.forEach((task) => {
      if (!merged.some((item) => item.caseId === task.caseId)) {
        merged.push(task)
      }
    })
    tenderTasks.value = merged.map(toTenderTaskCard)
  } catch (error) {
    console.error('Load tender tasks failed:', error)
    ElMessage.warning('标书审查任务加载失败，当前展示部分演示数据')
  }
}

const tasks = ref([])

const initTasks = () => {
  tasks.value = filteredTasks.value.map(task => ({
    id: task.id,
    name: task.title,
    scene: task.type,
    sceneClass: task.scene,
    status: task.riskLevel === 'risk-info' ? '运行中' : '已完成',
    progress: task.progress || 0,
    isRunning: task.riskLevel === 'risk-info',
    isUrgent: task.isUrgent || task.tag === '待处理',
    isRecent: task.isRecent
  }))
}

onMounted(() => {
  loadTenderTasks()
})

watch(() => filteredTasks.value, () => {
  initTasks()
}, { immediate: true })

const handleTaskAction = (task) => {
  if (task.scene === 'tender') {
    appendAuditLog({
      id: `audit-${Date.now()}`,
      type: 'TASK_VIEWED',
      title: '查看任务详情',
      detail: `查看任务 ${task.id}`,
      createdAt: new Date().toISOString()
    })
    router.push(`/agent/tasks/${task.id}`)
    return
  }
  ElMessage.info('该场景详情页将在后续版本接入，当前先展示看板信息')
}


<style scoped>
.board-container {
  padding: 32px;
  max-width: 1440px;
  margin: 0 auto;
}

.board-header {
  margin-bottom: 24px;
}

.board-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.tasks-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 24px;
}

@media (min-width: 768px) {
  .tasks-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1280px) {
  .tasks-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.task-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 24px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.task-card:hover {
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  transform: translateY(-4px);
}

.task-card.highlight {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.1);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.scene-badge {
  padding: 6px 10px;
  background: #eef2ff;
  color: #4f46e5;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.scene-badge.contract {
  background: #ecfdf5;
  color: #059669;
}

.scene-badge.risk {
  background: #fef3c7;
  color: #d97706;
}

.status-badge {
  font-size: 10px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
}

.task-name {
  font-size: 16px;
  font-weight: 700;
  color: #334155;
  margin: 0 0 4px;
  line-height: 1.4;
}

.task-id {
  font-size: 11px;
  font-family: 'SF Mono', Monaco, Consolas, monospace;
  color: #94a3b8;
  margin: 0 0 16px;
}

.progress-container {
  margin-top: 16px;
}

.progress-bar {
  width: 100%;
  height: 6px;
  background: #f1f5f9;
  border-radius: 9999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #2563eb;
  border-radius: inherit;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}
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
