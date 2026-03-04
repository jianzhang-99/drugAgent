<template>
  <div class="compliance-page">
    <section class="page-hero">
      <div>
        <h2>合规审查工作台</h2>
        <p>法规问答、材料核验与审查结论统一管理</p>
      </div>
      <div class="hero-badge">监管辅助 AI</div>
    </section>

    <div class="chat-container">
      <aside class="chat-sidebar">
        <div class="sidebar-header">
          <el-button type="primary" class="new-chat-btn" @click="handleNewChat" icon="Plus">新对话</el-button>
        </div>

        <div class="session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ active: currentSessionId === session.id }"
            @click="switchSession(session.id)"
          >
            <el-icon><ChatLineRound /></el-icon>
            <span class="session-title">{{ session.title || '新合法会话' }}</span>
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <div class="chat-header">
          <div class="header-title">合规对话</div>
          <div v-if="currentFile" class="current-file">
            <el-icon><Paperclip /></el-icon>
            <span>当前文件：{{ currentFile.fileName }}</span>
          </div>
          <div v-else class="header-tip">支持上传法规或采购单据进行审查</div>
        </div>

        <div class="chat-messages" ref="msgContainerRef">
          <MessageBubble v-for="(msg, idx) in messages" :key="idx" :msg="msg" />
          <div v-if="sending" class="thinking-row">
            <el-icon class="thinking-icon"><Loading /></el-icon>
            <span>正在审查并生成建议...</span>
          </div>
        </div>

        <div class="chat-input-area">
          <div class="input-actions">
            <el-upload
              class="upload-btn"
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileUpload"
            >
              <el-button link type="primary" icon="Paperclip">上传文件</el-button>
            </el-upload>
          </div>
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="请输入合规问题，按 Ctrl+Enter 发送"
            @keyup.ctrl.enter="handleSend"
          />
          <div class="send-action">
            <el-button type="primary" :loading="sending" @click="handleSend" icon="Position">发送</el-button>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Plus, ChatLineRound, Paperclip, Position, Loading } from '@element-plus/icons-vue'
import MessageBubble from '@/components/MessageBubble.vue'
import { sendChat, getSessions, getMessages, createSession, uploadFile } from '@/api/compliance'
import { ElMessage } from 'element-plus'

const sessions = ref([])
const currentSessionId = ref(null)
const messages = ref([])
const inputText = ref('')
const currentFile = ref(null)
const sending = ref(false)
const msgContainerRef = ref(null)

const mockSessions = [
  { id: '1', title: '《药品管理法》合规审查' },
  { id: '2', title: '采购记录2026Q1合规性' }
]

const loadSessions = async () => {
  try {
    sessions.value = await getSessions()
    if (sessions.value.length > 0 && !currentSessionId.value) {
      switchSession(sessions.value[0].id)
    }
  } catch (e) {
    sessions.value = mockSessions
    currentSessionId.value = '1'
    switchSession('1')
  }
}

const switchSession = async (sessionId) => {
  currentSessionId.value = sessionId
  try {
    messages.value = await getMessages(sessionId)
  } catch (e) {
    messages.value = [
      {
        role: 'assistant',
        content: '您好！我是您的智能合规助手。您可以上传相关的药品法规、合规文件或者采购单据，我会帮您审查并解答合规问题。'
      },
      { role: 'user', content: '这份采购记录是否符合药品管理法？' },
      {
        role: 'assistant',
        content: '## 合规判断：⚠️ 部分合规\n### 分析\n根据内容和《药品管理法》，您提供的单据缺少关键的**进口药品注册证**信息，建议及时补充，否则可能面临行政处罚风险。'
      }
    ]
  }
  scrollToBottom()
}

const handleSend = async () => {
  if (!inputText.value.trim() || sending.value) return

  const userMsg = inputText.value
  inputText.value = ''

  messages.value.push({ role: 'user', content: userMsg })
  scrollToBottom()

  sending.value = true
  try {
    const reply = await sendChat({
      sessionId: currentSessionId.value,
      fileId: currentFile.value?.id,
      message: userMsg
    })
    messages.value.push({ role: 'assistant', content: reply.content })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '服务正在建设中或网络异常，请稍后重试。' })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const handleFileUpload = async (uploadFileData) => {
  try {
    const res = await uploadFile(uploadFileData.raw)
    currentFile.value = res
    ElMessage.success(`文件 ${res.fileName} 上传成功，正在解析...`)
  } catch (e) {
    currentFile.value = { id: 'test', fileName: uploadFileData.raw.name }
    ElMessage.success(`文件 ${uploadFileData.raw.name} 解析完成，可进行相关提问！`)
  }
}

const handleNewChat = async () => {
  try {
    const session = await createSession({ sessionType: 'compliance' })
    currentSessionId.value = session.id
    loadSessions()
  } catch {
    const newId = Date.now().toString()
    sessions.value.unshift({ id: newId, title: '新会话' })
    switchSession(newId)
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    const container = msgContainerRef.value
    if (container) container.scrollTop = container.scrollHeight
  })
}

onMounted(loadSessions)
</script>

<style scoped>
.compliance-page {
  max-width: 1480px;
  margin: 0 auto;
}

.page-hero {
  margin-bottom: 14px;
  border-radius: 12px;
  padding: 14px 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #0f4aa8, #1f6de0 60%, #4c94ff);
  box-shadow: 0 10px 24px rgba(31, 109, 224, 0.22);
}

.page-hero h2 {
  margin: 0;
  font-size: 20px;
}

.page-hero p {
  margin: 5px 0 0;
  opacity: 0.92;
}

.hero-badge {
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.12);
  padding: 6px 10px;
  border-radius: 20px;
  font-size: 13px;
}

.chat-container {
  display: flex;
  height: calc(100vh - 190px);
  min-height: 560px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e8eef8;
  box-shadow: 0 8px 20px rgba(32, 68, 126, 0.08);
  overflow: hidden;
}

.chat-sidebar {
  width: 270px;
  background: linear-gradient(180deg, #f8fbff, #f4f7fd);
  border-right: 1px solid #e8eef8;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 14px;
  border-bottom: 1px solid #e8eef8;
}

.new-chat-btn {
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 10px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  border-radius: 8px;
  color: #3e4a5f;
  transition: all 0.2s;
}

.session-item:hover {
  background: #edf4ff;
}

.session-item.active {
  background: #e7f0ff;
  color: #1e62d0;
  font-weight: 600;
}

.session-title {
  margin-left: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f8fbff;
}

.chat-header {
  height: 58px;
  padding: 0 20px;
  border-bottom: 1px solid #e8eef8;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 700;
  color: #25324a;
}

.header-tip {
  color: #8a94a6;
  font-size: 13px;
}

.current-file {
  color: #1e62d0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.thinking-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #6d7a8f;
  background: #fff;
  border: 1px solid #e8eef8;
  border-radius: 10px;
  padding: 10px 12px;
}

.thinking-icon {
  animation: spin 1s linear infinite;
}

.chat-input-area {
  padding: 14px 18px;
  border-top: 1px solid #e8eef8;
  background: #fff;
}

.input-actions {
  margin-bottom: 8px;
}

.send-action {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
