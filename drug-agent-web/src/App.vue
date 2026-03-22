<template>
  <div class="flex h-screen bg-slate-100 overflow-hidden">
    <!-- Sidebar -->
    <AppSidebar
      :collapsed="sidebarCollapsed"
      @new-chat="handleNewChat"
      @load-session="handleLoadSession"
      @toggle-collapse="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- Main Content Area -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Header -->
      <AppHeader
        :active-task-count="activeTaskCount"
        @toggle-task-pane="taskPaneOpen = !taskPaneOpen"
      />

      <!-- Page Content -->
      <main class="flex-1 overflow-hidden bg-white">
        <RouterView />
      </main>
    </div>

    <!-- Task Pane -->
    <TaskPane
      :is-open="taskPaneOpen"
      :tasks="tasks"
      @close="taskPaneOpen = false"
      @select-task="handleSelectTask"
    />

    <!-- Click Overlay to close TaskPane -->
    <div
      v-if="taskPaneOpen"
      class="fixed inset-0 z-40"
      @click="taskPaneOpen = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { RouterView, useRouter, useRoute } from 'vue-router'
import AppSidebar from './components/layout/AppSidebar.vue'
import AppHeader from './components/layout/AppHeader.vue'
import TaskPane from './components/layout/TaskPane.vue'
import { useSessionStore } from './stores/session'

const router = useRouter()
const route = useRoute()
const sessionStore = useSessionStore()

// Sidebar State
const sidebarCollapsed = ref(false)

// Task Pane State
const taskPaneOpen = ref(false)

// Tasks (global state)
const tasks = ref([
  {
    id: 'T-001',
    name: '年度设备采购标书分析',
    progress: 100,
    status: 'completed'
  },
  {
    id: 'T-002',
    name: '合同条款风险提取',
    progress: 65,
    status: 'running'
  }
])

const activeTaskCount = computed(() => {
  return tasks.value.filter(t => t.status === 'running').length
})

// Initialize sessions from store or use mock data
onMounted(() => {
  if (sessionStore.sessions.length === 0) {
    // Use mock data for demo
    sessionStore.sessions = [
      {
        id: 'sess_001',
        title: '年度设备采购标书比对',
        dateGroup: '今天',
        scene: 'TENDER',
        messages: [
          {
            role: 'user',
            content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。',
            attachments: ['样例A_XX医院标书.docx', '样例B_XX药房投标.pdf'],
            time: '14:20'
          },
          {
            role: 'agent',
            content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。',
            time: '14:22',
            result: {
              scene: 'TENDER',
              riskLevel: 'High',
              summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。',
              evidenceList: [
                '片段A：设备的额定功率需满足 1500W-1800W 区间，且外壳需采用医用级 ABS 材质。',
                '片段B：该机器额定功率符合 1500W 至 1800W，外壳材料为医用级 ABS。'
              ],
              steps: ['文档解析与 OCR', '语义块向量化抽取', '相似度比对与规则过滤', 'LLM 生成审查意见'],
              traceId: 'TRC-99281-A'
            }
          }
        ],
        updatedAt: new Date().toISOString()
      },
      {
        id: 'sess_002',
        title: '骨科耗材供应商协议预审',
        dateGroup: '昨天',
        scene: 'CONTRACT',
        messages: [
          {
            role: 'user',
            content: '审查一下这份最新的采购合同框架，按知识库提取风险条款。',
            attachments: ['骨科耗材采购合同_v3.pdf'],
            time: '16:05'
          },
          {
            role: 'agent',
            content: '合同预审完毕。整体结构完整，但提取到几处需要关注的潜在风险条款。',
            time: '16:06',
            result: {
              scene: 'CONTRACT',
              riskLevel: 'Medium',
              summary: '发现 3 条倾向于供应商的免责声明及付款周期违规条款。',
              evidenceList: ['条款 4.2: 甲方需在收到发票后 5 个工作日内结清全款 (违背常规 30 天周期)'],
              steps: ['合同条款结构化切分', '规则引擎强匹配', '风险评级测算'],
              traceId: 'TRC-99282-B'
            }
          }
        ],
        updatedAt: new Date(Date.now() - 86400000).toISOString()
      },
      {
        id: 'sess_003',
        title: '药品集中采购合规检查',
        dateGroup: '过去7天',
        scene: 'COMPLIANCE',
        messages: [
          {
            role: 'user',
            content: '分析近3个月的药品采购数据，生成合规预警报告。',
            time: '09:30'
          },
          {
            role: 'agent',
            content: '数据已分析完毕。检测到部分采购行为存在异常波动，需关注。',
            time: '09:32',
            result: {
              scene: 'COMPLIANCE',
              riskLevel: 'Medium',
              summary: '发现 2 个品类的采购价格异常波动，建议进行复核。',
              evidenceList: ['某品牌降压药近期采购价上涨 15%，超出正常范围'],
              steps: ['数据采集与清洗', '异常检测算法', '风险等级划分'],
              traceId: 'TRC-99283-C'
            }
          }
        ],
        updatedAt: new Date(Date.now() - 86400000 * 5).toISOString()
      }
    ]
    sessionStore.persist()
  }
})

// Actions
const handleNewChat = () => {
  sessionStore.setActiveSession(null)
  router.push('/workspace')
}

const handleLoadSession = (sessionId) => {
  sessionStore.setActiveSession(sessionId)
  router.push('/workspace?sessionId=' + sessionId)
}

const handleSelectTask = (task) => {
  console.log('Selected task:', task)
  taskPaneOpen.value = false
}
</script>
