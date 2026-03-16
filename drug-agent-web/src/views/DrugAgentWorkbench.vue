<template>
  <div class="drug-agent-page">
    <section class="page-hero">
      <div>
        <h2>Drug Agent 工作台</h2>
        <p>一个入口管理法规问答、合规审查和药品分析路由</p>
      </div>
      <div class="hero-badge">MVP 调度入口</div>
    </section>

    <div class="workbench-grid">
      <section class="assistant-panel">
        <div class="panel-head">
          <div>
            <h3>智能调度对话</h3>
            <p>可手动指定场景，也可交给统一 Agent 自动路由</p>
          </div>
          <el-tag type="primary" effect="dark">/agent/drug/chat</el-tag>
        </div>

        <div class="scenario-bar">
          <el-radio-group v-model="form.sceneHint" size="large">
            <el-radio-button label="">自动识别</el-radio-button>
            <el-radio-button label="GENERAL_QA">法规问答</el-radio-button>
            <el-radio-button label="COMPLIANCE_REVIEW">合规审查</el-radio-button>
            <el-radio-button label="DRUG_ANALYSIS">药品分析</el-radio-button>
          </el-radio-group>
        </div>

        <div class="chat-board" ref="messageContainerRef">
          <MessageBubble v-for="(msg, idx) in messages" :key="idx" :msg="msg" />
          <div v-if="sending" class="thinking-row">
            <el-icon class="thinking-icon"><Loading /></el-icon>
            <span>Drug Agent 正在判断场景并生成结果...</span>
          </div>
        </div>

        <div class="composer">
          <el-input
            v-model="form.query"
            type="textarea"
            :rows="4"
            placeholder="例如：帮我分析最近一个月阿莫西林的异常用量；或审查这份材料是否符合药品管理法"
            @keyup.ctrl.enter="handleSend"
          />
          <div class="composer-actions">
            <div class="hint-text">按 Ctrl+Enter 发送</div>
            <el-button type="primary" :loading="sending" @click="handleSend">
              发送到 Drug Agent
            </el-button>
          </div>
        </div>
      </section>

      <aside class="insight-panel">
        <div class="summary-card">
          <div class="summary-title">最近一次路由结果</div>
          <div class="summary-scene">{{ sceneLabel(lastResult.scene) || '等待请求' }}</div>
          <div class="summary-badges">
            <span class="route-badge">{{ routeReasonLabel(lastResult.routeReason) }}</span>
            <span class="risk-badge" :class="riskClass(lastResult.riskLevel)">
              风险：{{ riskLabel(lastResult.riskLevel) }}
            </span>
          </div>
          <div class="summary-trace">Trace: {{ lastResult.traceId || '--' }}</div>
        </div>

        <div class="info-card">
          <div class="info-header">结果摘要</div>
          <div v-if="lastResult.answer" class="summary-answer">
            {{ lastResult.answer }}
          </div>
          <div v-else class="empty-text">发送请求后会在这里展示本次结果摘要。</div>
        </div>

        <div class="info-card">
          <div class="info-header">执行步骤</div>
          <div v-if="lastResult.steps?.length" class="steps-list">
            <div v-for="step in lastResult.steps" :key="step" class="step-item">{{ step }}</div>
          </div>
          <div v-else class="empty-text">发送一次请求后会显示执行链路。</div>
        </div>

        <div class="info-card">
          <div class="info-header">证据与说明</div>
          <div v-if="lastResult.evidenceList?.length" class="evidence-list">
            <div v-for="(item, idx) in lastResult.evidenceList" :key="idx" class="evidence-item">
              <div class="evidence-title">{{ item.title }}</div>
              <div class="evidence-content">{{ item.content }}</div>
              <div class="evidence-source">{{ item.source }}</div>
            </div>
          </div>
          <div v-else class="empty-text">这里会展示工作流返回的证据和说明。</div>
        </div>

        <div class="tips-card">
          <div class="tips-title">示例问题</div>
          <el-button text @click="fillExample('药品经营许可证申请需要满足哪些条件？')">法规问答</el-button>
          <el-button text @click="fillExample('请帮我审查这份采购材料是否存在合规风险')">合规审查</el-button>
          <el-button text @click="fillExample('请分析近30天阿莫西林用量是否存在异常')">药品分析</el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MessageBubble from '@/components/MessageBubble.vue'
import { streamDrugAgentChat } from '@/api/drug-agent'

const form = reactive({
  sessionId: `drug-agent-session-${Date.now()}`,
  userId: 'demo-user',
  query: '',
  sceneHint: '',
  fileIds: []
})

const messages = ref([
  {
    role: 'assistant',
    content: '这里是 Drug Agent 工作台。你可以直接提问，也可以手动指定场景，观察系统如何路由到不同工作流。'
  }
])
const sending = ref(false)
const lastResult = ref({})
const messageContainerRef = ref(null)

const sceneLabel = (scene) => {
  const labels = {
    GENERAL_QA: '法规问答',
    COMPLIANCE_REVIEW: '合规审查',
    DRUG_ANALYSIS: '药品分析',
    UNKNOWN: '未知场景'
  }
  return labels[scene] || scene
}

const routeReasonLabel = (reason) => {
  const labels = {
    sceneHint: '手动指定场景',
    fileIds: '检测到附件',
    regulationKeywords: '命中法规关键词',
    reviewKeywords: '命中审查关键词',
    analysisKeywords: '命中分析关键词',
    fallback: '未命中规则，走兜底',
    unknown: '暂无路由信息'
  }
  return labels[reason] || '暂无路由信息'
}

const riskLabel = (riskLevel) => {
  const labels = {
    NONE: '无明显风险',
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    CRITICAL: '严重风险',
    PENDING: '待分析',
    UNKNOWN: '未知'
  }
  return labels[riskLevel] || (riskLevel || '未知')
}

const riskClass = (riskLevel) => {
  const value = (riskLevel || 'UNKNOWN').toLowerCase()
  return `risk-${value}`
}

const scrollToBottom = () => {
  nextTick(() => {
    const el = messageContainerRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const fillExample = (text) => {
  form.query = text
}

const handleSend = async () => {
  if (!form.query.trim() || sending.value) {
    return
  }

  const query = form.query.trim()
  messages.value.push({ role: 'user', content: query })
  const assistantMessage = { role: 'assistant', content: '' }
  messages.value.push(assistantMessage)
  form.query = ''
  scrollToBottom()

  sending.value = true
  try {
    await streamDrugAgentChat({
      sessionId: form.sessionId,
      userId: form.userId,
      query,
      sceneHint: form.sceneHint || null,
      fileIds: form.fileIds
    }, {
      onMeta: (meta) => {
        lastResult.value = {
          ...lastResult.value,
          ...meta,
          answer: ''
        }
        assistantMessage.content = `## 路由场景：${sceneLabel(meta.scene)}\n\n**路由原因：** ${routeReasonLabel(meta.routeReason)}\n\n`
        scrollToBottom()
      },
      onDelta: (chunk) => {
        const text = typeof chunk === 'string' ? chunk : ''
        assistantMessage.content += text
        lastResult.value = {
          ...lastResult.value,
          answer: (lastResult.value.answer || '') + text
        }
        scrollToBottom()
      },
      onDone: (payload) => {
        lastResult.value = {
          ...lastResult.value,
          ...payload
        }
      },
      onError: (payload) => {
        throw new Error(typeof payload === 'string' ? payload : 'Drug Agent 流式调用失败')
      }
    })
  } catch (error) {
    ElMessage.error('Drug Agent 调用失败，请检查后端接口或代理配置。')
    assistantMessage.content = 'Drug Agent 当前不可用，请稍后重试。'
  } finally {
    sending.value = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.drug-agent-page {
  max-width: 1480px;
  margin: 0 auto;
}

.page-hero {
  margin-bottom: 16px;
  border-radius: 14px;
  padding: 18px 20px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background:
    radial-gradient(circle at right top, rgba(255, 255, 255, 0.18), transparent 30%),
    linear-gradient(135deg, #123463, #1d5db3 62%, #4b94ff);
  box-shadow: 0 16px 30px rgba(20, 76, 151, 0.2);
}

.page-hero h2 {
  margin: 0;
  font-size: 22px;
}

.page-hero p {
  margin: 6px 0 0;
  opacity: 0.9;
}

.hero-badge {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.32);
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 0.85fr);
  gap: 16px;
}

.assistant-panel,
.summary-card,
.info-card,
.tips-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dfe8f6;
  border-radius: 14px;
  box-shadow: 0 10px 28px rgba(37, 74, 132, 0.08);
}

.assistant-panel {
  padding: 18px;
  display: flex;
  flex-direction: column;
  min-height: 700px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.panel-head h3 {
  margin: 0;
  color: #183153;
}

.panel-head p {
  margin: 6px 0 0;
  color: #6d7f99;
}

.scenario-bar {
  margin-bottom: 14px;
}

.chat-board {
  flex: 1;
  min-height: 420px;
  max-height: 520px;
  overflow-y: auto;
  padding: 6px 2px;
}

.thinking-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 12px;
  background: #f4f8ff;
  color: #46658f;
}

.thinking-icon {
  animation: spin 1.2s linear infinite;
}

.composer {
  margin-top: 14px;
  border-top: 1px solid #ebf1fa;
  padding-top: 14px;
}

.composer-actions {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint-text {
  color: #7f8fa7;
  font-size: 13px;
}

.insight-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-card,
.info-card,
.tips-card {
  padding: 16px;
}

.summary-title,
.info-header,
.tips-title {
  font-size: 14px;
  color: #70829d;
  margin-bottom: 10px;
}

.summary-scene {
  font-size: 28px;
  font-weight: 700;
  color: #17365d;
}

.summary-badges {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.route-badge,
.risk-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 600;
}

.route-badge {
  color: #1f4f90;
  background: #ebf3ff;
  border: 1px solid #d5e4ff;
}

.risk-badge {
  border: 1px solid transparent;
}

.risk-none,
.risk-low {
  color: #23683d;
  background: #ebf7ef;
  border-color: #d1ead9;
}

.risk-medium,
.risk-pending {
  color: #9b5b00;
  background: #fff4df;
  border-color: #f0ddb6;
}

.risk-high,
.risk-critical {
  color: #a02b2b;
  background: #fdeaea;
  border-color: #f2c8c8;
}

.risk-unknown {
  color: #516274;
  background: #eef3f8;
  border-color: #dbe4ee;
}

.summary-trace {
  margin-top: 8px;
  font-size: 12px;
  color: #7d8ca1;
  word-break: break-all;
}

.steps-list,
.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-item {
  padding: 10px 12px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f7faff, #f1f6ff);
  border: 1px solid #e1ebfa;
  color: #2a4c79;
}

.evidence-item {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #e4ecf8;
  background: #fbfdff;
}

.evidence-title {
  font-weight: 700;
  color: #213a61;
}

.evidence-content {
  margin-top: 6px;
  color: #4f627e;
  line-height: 1.65;
}

.evidence-source {
  margin-top: 8px;
  font-size: 12px;
  color: #8091aa;
}

.empty-text {
  color: #8a98ae;
  line-height: 1.7;
}

.summary-answer {
  color: #40546f;
  line-height: 1.8;
  white-space: pre-wrap;
}

.tips-card :deep(.el-button) {
  display: block;
  width: 100%;
  justify-content: flex-start;
  margin-left: 0;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1080px) {
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .assistant-panel {
    min-height: auto;
  }

  .chat-board {
    max-height: 420px;
  }
}
</style>
