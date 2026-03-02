# 模块E：前端页面 — 技术实现方案

> **版本**：v1.0 | **日期**：2026-03-02 | **技术栈**：Vue 3 + Vite + Element Plus + ECharts + Axios

---

## 一、模块概述

前端模块负责构建系统的 3 个核心页面：**药品监管面板**、**合规对话界面**、**知识库管理页**。采用 Vue 3 组合式 API + Element Plus UI 框架，通过 Axios 与后端 REST API 交互。

### 页面导航关系

```
┌─────────────────────────────────────────────────────┐
│            顶部导航栏 (NavBar.vue)                    │
│   [📊 药品监管]    [💬 合规对话]    [📚 知识库管理]     │
├─────────────────────────────────────────────────────┤
│                                                     │
│              <router-view />                        │
│        ┌─────────────────────────┐                   │
│        │ /drug       → DrugMonitor.vue    │           │
│        │ /compliance → ComplianceChat.vue │           │
│        │ /knowledge  → KnowledgeBase.vue  │           │
│        └─────────────────────────┘                   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## 二、项目结构

```
drug-agent-web/
├── public/
│   └── favicon.ico
├── src/
│   ├── api/                           // API 请求封装
│   │   ├── request.js                 // Axios 实例(拦截器/baseURL)
│   │   ├── drug.js                    // 药品模块 API
│   │   ├── compliance.js              // 合规对话 API
│   │   └── knowledge.js               // 知识库 API
│   ├── views/                         // 页面组件
│   │   ├── DrugMonitor.vue            // 药品监管面板(E1)
│   │   ├── ComplianceChat.vue         // 合规对话界面(E2)
│   │   └── KnowledgeBase.vue          // 知识库管理页(E3)
│   ├── components/                    // 可复用组件
│   │   ├── NavBar.vue                 // 顶部导航
│   │   ├── MessageBubble.vue          // 对话消息气泡
│   │   ├── FileUpload.vue             // 通用文件上传
│   │   ├── AnalysisReport.vue         // 分析报告卡片
│   │   └── SessionList.vue            // 会话列表侧边栏
│   ├── router/
│   │   └── index.js                   // 路由配置
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
└── package.json
```

---

## 三、技术选型与初始化

### 3.1 项目初始化

```bash
# 使用 Vite 创建 Vue 3 项目
npm create vite@latest drug-agent-web -- --template vue

# 安装依赖
cd drug-agent-web
npm install
npm install element-plus @element-plus/icons-vue    # UI 框架
npm install axios                                    # HTTP 请求
npm install vue-router@4                             # 路由
npm install echarts                                  # 图表
npm install marked                                    # Markdown 渲染
npm install highlight.js                              # 代码高亮
```

### 3.2 Axios 封装

```javascript
// src/api/request.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
    baseURL: 'http://localhost:8123/api',
    timeout: 60000      // AI接口可能较慢，设60秒
})

// 请求拦截器
request.interceptors.request.use(config => {
    // 后续可加 Token
    return config
})

// 响应拦截器
request.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            ElMessage.error(res.message || '请求失败')
            return Promise.reject(new Error(res.message))
        }
        return res.data
    },
    error => {
        ElMessage.error(error.response?.data?.message || '网络异常')
        return Promise.reject(error)
    }
)

export default request
```

### 3.3 路由配置

```javascript
// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/',           redirect: '/drug' },
    { path: '/drug',       name: 'DrugMonitor',     component: () => import('../views/DrugMonitor.vue') },
    { path: '/compliance', name: 'ComplianceChat',   component: () => import('../views/ComplianceChat.vue') },
    { path: '/knowledge',  name: 'KnowledgeBase',    component: () => import('../views/KnowledgeBase.vue') },
]

export default createRouter({
    history: createWebHistory(),
    routes
})
```

---

## 四、E1 — 药品监管面板

### 4.1 页面布局

```
┌──────────────────────────────────────────────────────┐
│  Drug-Agent 药品智能监管系统      [知识库] [合规对话]  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌─── 数据导入区 ────────────────────────────────┐   │
│  │ [📤 上传Excel导入]  [📥 下载模板]              │   │
│  │ 导入结果: 成功985条, 失败15条 [查看详情]       │   │
│  └───────────────────────────────────────────────┘   │
│                                                      │
│  ┌─── 数据查询区 ────────────────────────────────┐   │
│  │ [药品名称🔍] [科室▼] [分类▼] [日期范围📅] [查询] │  │
│  │ ┌──────────────────────────────────────────┐  │   │
│  │ │ 药品名  │ 编码 │ 用量 │ 日期  │ 科室 │ 分类│  │  │
│  │ │ 阿莫西林│YP001│ 200 │01-15│ 内科│抗生素│  │  │
│  │ │ ...     │     │     │     │    │     │  │  │
│  │ └──────────────────────────────────────────┘  │   │
│  │                        [1] [2] [3] ... [12]   │   │
│  └───────────────────────────────────────────────┘   │
│                                                      │
│  ┌─── AI分析区 ──────────────────────────────────┐   │
│  │ [选择药品: 阿莫西林▼] [时间段: 2026-01~02]    │   │
│  │ [🔍 开始AI分析]                               │   │
│  │                                               │   │
│  │ ┌── 分析报告 ──────────────────────────────┐  │   │
│  │ │ 📈 趋势图 (ECharts折线图)                │  │   │
│  │ │ 📊 统计摘要 (日均/最大/标准差/环比)      │  │   │
│  │ │ ⚠️ 异常清单                              │  │   │
│  │ │ 🔴 风险等级: HIGH                        │  │   │
│  │ │ 💡 AI分析结论 + 建议列表                 │  │   │
│  │ └─────────────────────────────────────────┘  │   │
│  └───────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### 4.2 API 封装

```javascript
// src/api/drug.js
import request from './request'

// 导入 Excel
export const importExcel = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/drug/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 下载模板
export const downloadTemplate = () => {
    window.open('http://localhost:8123/api/drug/template', '_blank')
}

// 查询药品列表
export const queryDrugList = (params) => {
    return request.get('/drug/list', { params })
}

// 获取药品名称列表（下拉）
export const getDrugNames = () => request.get('/drug/names')

// AI 分析
export const analyzeDrug = (data) => {
    return request.post('/drug/analyze', data)
}
```

### 4.3 核心实现要点

```vue
<!-- DrugMonitor.vue — 关键逻辑 -->
<script setup>
import { ref, reactive } from 'vue'
import * as echarts from 'echarts'
import { queryDrugList, analyzeDrug, importExcel } from '@/api/drug'

// === 数据导入 ===
const importResult = ref(null)
const importing = ref(false)

const handleImport = async (uploadFile) => {
    importing.value = true
    try {
        importResult.value = await importExcel(uploadFile.raw)
        ElMessage.success(`导入成功 ${importResult.value.successCount} 条`)
        loadDrugList()  // 刷新列表
    } finally {
        importing.value = false
    }
}

// === 数据查询 ===
const queryForm = reactive({
    drugName: '', department: '', drugCategory: '',
    startDate: '', endDate: '', page: 1, size: 20
})
const tableData = ref([])
const total = ref(0)

const loadDrugList = async () => {
    const res = await queryDrugList(queryForm)
    tableData.value = res.records
    total.value = res.total
}

// === AI 分析 ===
const analyzeForm = reactive({ drugName: '', startDate: '', endDate: '' })
const analyzing = ref(false)
const report = ref(null)

const startAnalyze = async () => {
    analyzing.value = true
    try {
        report.value = await analyzeDrug(analyzeForm)
        renderChart(report.value.stats.dailyDetails)
    } finally {
        analyzing.value = false
    }
}

// === ECharts 趋势图 ===
const chartRef = ref(null)
const renderChart = (dailyData) => {
    const chart = echarts.init(chartRef.value)
    chart.setOption({
        title: { text: '药品用量趋势' },
        tooltip: { trigger: 'axis' },
        xAxis: {
            type: 'category',
            data: dailyData.map(d => d.usageDate)
        },
        yAxis: { type: 'value', name: '用量' },
        series: [{
            type: 'line',
            data: dailyData.map(d => d.dailyTotal),
            smooth: true,
            markPoint: {
                data: [
                    { type: 'max', name: '最大值' },
                    { type: 'min', name: '最小值' }
                ]
            }
        }]
    })
}
</script>
```

---

## 五、E2 — 合规对话界面

### 5.1 页面布局

```
┌──────────────────────────────────────────────────────┐
│  Drug-Agent 合规对话              [监管面板] [知识库]  │
├──────────┬───────────────────────────────────────────┤
│ 会话列表  │  📎 当前文件: 采购记录2026Q1.pdf          │
│          │─────────────────────────────────────────  │
│ > 会话1  │                                          │
│   会话2  │  🤖 您好！我已读取文件内容，请问          │
│   会话3  │     您想了解哪方面的合规情况？            │
│          │                                          │
│          │  👤 这份采购记录是否符合药品管理法？       │
│          │                                          │
│          │  🤖 ## 合规判断：⚠️ 部分合规              │
│          │     ### 分析                              │
│          │     根据文件内容和《药品管理法》...        │
│          │                                          │
│          │                                          │
│ [+新对话] │  ┌────────────────────────────────────┐  │
│          │  │[📎上传文件] 输入合规问题...    [发送]│  │
│          │  └────────────────────────────────────┘  │
└──────────┴───────────────────────────────────────────┘
```

### 5.2 API 封装

```javascript
// src/api/compliance.js
import request from './request'

// 上传文件
export const uploadFile = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/compliance/file/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

// 发送对话
export const sendChat = (data) => {
    return request.post('/compliance/chat', data)
}

// 获取会话列表
export const getSessions = (type = 'compliance') => {
    return request.get('/compliance/sessions', { params: { type } })
}

// 获取会话消息
export const getMessages = (sessionId) => {
    return request.get(`/compliance/sessions/${sessionId}/messages`)
}

// 创建新会话
export const createSession = (data) => {
    return request.post('/compliance/sessions', data)
}

// 删除会话
export const deleteSession = (id) => {
    return request.delete(`/compliance/sessions/${id}`)
}

// 获取文件列表
export const getFiles = () => request.get('/compliance/files')

// 删除文件
export const deleteFile = (id) => request.delete(`/compliance/files/${id}`)
```

### 5.3 消息气泡组件

```vue
<!-- components/MessageBubble.vue -->
<template>
  <div class="message-bubble" :class="{ 'is-user': msg.role === 'user' }">
    <div class="avatar">
      {{ msg.role === 'user' ? '👤' : '🤖' }}
    </div>
    <div class="content">
      <!-- AI 消息用 Markdown 渲染 -->
      <div v-if="msg.role === 'assistant'" v-html="renderedContent" />
      <!-- 用户消息纯文本 -->
      <div v-else>{{ msg.content }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
    msg: { type: Object, required: true }
})

const renderedContent = computed(() => {
    return marked.parse(props.msg.content || '', { breaks: true })
})
</script>

<style scoped>
.message-bubble {
    display: flex;
    gap: 12px;
    padding: 16px;
    margin-bottom: 12px;
}
.message-bubble.is-user {
    flex-direction: row-reverse;
    background: #f0f7ff;
    border-radius: 12px;
}
.avatar {
    font-size: 24px;
    flex-shrink: 0;
}
.content {
    flex: 1;
    line-height: 1.6;
    word-break: break-word;
}
</style>
```

### 5.4 对话页面核心逻辑

```vue
<!-- ComplianceChat.vue — 关键逻辑 -->
<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { sendChat, getSessions, getMessages, createSession, uploadFile } from '@/api/compliance'

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const currentFile = ref(null)
const sending = ref(false)
const msgContainerRef = ref(null)

// 加载会话列表
const loadSessions = async () => {
    sessions.value = await getSessions()
    if (sessions.value.length > 0 && !currentSessionId.value) {
        switchSession(sessions.value[0].id)
    }
}

// 切换会话
const switchSession = async (sessionId) => {
    currentSessionId.value = sessionId
    messages.value = await getMessages(sessionId)
    scrollToBottom()
}

// 发送消息
const handleSend = async () => {
    if (!inputText.value.trim() || sending.value) return
    
    const userMsg = inputText.value
    inputText.value = ''
    
    // 乐观更新：先在界面显示用户消息
    messages.value.push({ role: 'user', content: userMsg })
    scrollToBottom()
    
    // 显示加载状态
    sending.value = true
    messages.value.push({ role: 'assistant', content: '正在思考中...', loading: true })
    
    try {
        const reply = await sendChat({
            sessionId: currentSessionId.value,
            fileId: currentFile.value?.id,
            message: userMsg
        })
        
        // 替换加载消息
        messages.value[messages.value.length - 1] = {
            role: 'assistant',
            content: reply.content
        }
    } catch (e) {
        messages.value[messages.value.length - 1] = {
            role: 'assistant',
            content: '❌ 服务异常，请稍后重试'
        }
    } finally {
        sending.value = false
        scrollToBottom()
    }
}

// 上传文件
const handleFileUpload = async (uploadFile) => {
    const res = await uploadFile(uploadFile.raw)
    currentFile.value = res
    ElMessage.success(`文件 ${res.fileName} 上传成功，正在解析...`)
}

// 创建新对话
const handleNewChat = async () => {
    const session = await createSession({ sessionType: 'compliance' })
    currentSessionId.value = session.id
    messages.value = []
    currentFile.value = null
    loadSessions()
}

// 滚动到底部
const scrollToBottom = () => {
    nextTick(() => {
        const container = msgContainerRef.value
        if (container) container.scrollTop = container.scrollHeight
    })
}

onMounted(loadSessions)
</script>
```

---

## 六、E3 — 知识库管理页

### 6.1 页面布局

```
┌──────────────────────────────────────────────────────┐
│  Drug-Agent 知识库管理            [监管面板] [对话]    │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ┌─── 上传法规文件 ─────────────────────────────┐    │
│  │  [📤 上传法规文件(PDF/Word)]                  │    │
│  │  支持格式: PDF / Word / Excel                │    │
│  └──────────────────────────────────────────────┘    │
│                                                      │
│  ┌─── 已入库文件列表 ───────────────────────────┐    │
│  │ 文件名          │ 片段数 │ 状态  │ 时间  │操作│    │
│  │ 药品管理法.pdf  │  45   │ ✅完成 │03-01│ 🗑 │    │
│  │ GSP规范.docx    │  38   │ ✅完成 │03-01│ 🗑 │    │
│  │ 处方管理办法.pdf │  --   │ ⏳处理中│03-02│ - │    │
│  └──────────────────────────────────────────────┘    │
│                                                      │
│  ┌─── 检索效果测试 (P2) ────────────────────────┐    │
│  │  [输入关键词测试检索] [🔍测试]                │    │
│  │                                              │    │
│  │  结果1 (相似度0.87): "第五十三条 药品经营..." │    │
│  │  结果2 (相似度0.72): "第四十一条 药品生产..."│    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
```

### 6.2 API 封装

```javascript
// src/api/knowledge.js
import request from './request'

export const uploadKnowledge = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/knowledge/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}

export const getKnowledgeList = () => request.get('/knowledge/list')

export const deleteKnowledge = (id) => request.delete(`/knowledge/${id}`)

export const testSearch = (query, topK = 5) => {
    return request.post('/knowledge/test-search', { query, topK })
}
```

---

## 七、公共样式规范

### 7.1 全局样式变量

```css
/* src/styles/variables.css */
:root {
    --primary-color: #409eff;
    --success-color: #67c23a;
    --warning-color: #e6a23c;
    --danger-color: #f56c6c;
    --bg-color: #f5f7fa;
    --card-bg: #ffffff;
    --text-primary: #303133;
    --text-secondary: #909399;
    --border-color: #dcdfe6;
    --border-radius: 8px;
    --shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
```

### 7.2 风险等级样式

```css
/* 分析报告中的风险等级标签 */
.risk-tag-LOW      { background: #67c23a; color: white; }
.risk-tag-MEDIUM   { background: #e6a23c; color: white; }
.risk-tag-HIGH     { background: #f56c6c; color: white; }
.risk-tag-CRITICAL { background: #c00000; color: white; animation: pulse 1s infinite; }
```

---

## 八、Vite 开发代理配置

```javascript
// vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: { '@': path.resolve(__dirname, 'src') }
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8123',
                changeOrigin: true
            }
        }
    }
})
```

> **代理说明**：开发模式下，前端 `localhost:5173` 的 `/api` 请求自动转发到后端 `localhost:8123`，无需跨域配置。

---

## 九、构建 & 部署

```bash
# 开发模式
npm run dev                 # → http://localhost:5173

# 生产构建
npm run build               # → dist/ 目录

# 部署方式（2选1）
# 方式A: 后端静态资源 — 将 dist/ 复制到 Spring Boot 的 resources/static/
# 方式B: Nginx 反向代理 — Nginx serve dist/ + 代理 /api 到后端
```
