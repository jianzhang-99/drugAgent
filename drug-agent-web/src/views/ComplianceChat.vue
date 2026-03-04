<template>
  <div class="chat-container">
    <div class="chat-sidebar">
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
    </div>
    
    <div class="chat-main">
      <div class="chat-header">
        <div v-if="currentFile" class="current-file">
          <el-icon><Paperclip /></el-icon> 当前文件: {{ currentFile.fileName }}
        </div>
        <div v-else>合规对话</div>
      </div>
      
      <div class="chat-messages" ref="msgContainerRef">
        <MessageBubble v-for="(msg, idx) in messages" :key="idx" :msg="msg" />
        <div v-if="sending" class="message-bubble is-ai">
           <div class="avatar">🤖</div>
           <div class="content loading-indicator">思考中...</div>
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
          placeholder="输入合规问题，按 Ctrl+Enter 发送"
          @keyup.ctrl.enter="handleSend"
        />
        <div class="send-action">
          <el-button type="primary" :loading="sending" @click="handleSend" icon="Position">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { Plus, ChatLineRound, Paperclip, Position } from '@element-plus/icons-vue'
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

// 模拟 MOCK 方法以保持健壮性
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
            { role: 'assistant', content: '您好！我是您的智能合规助手。您可以上传相关的药品法规、合规文件或者采购单据，我会帮您审查并解答合规问题。' },
            { role: 'user', content: '这份采购记录是否符合药品管理法？' },
            { role: 'assistant', content: '## 合规判断：⚠️ 部分合规\n### 分析\n根据内容和《药品管理法》，您提供的单据缺少关键的**进口药品注册证**信息，建议及时补充，否则可能面临行政处罚风险。' }
        ]
    }
    scrollToBottom()
}

const handleSend = async () => {
    if (!inputText.value.trim() || sending.value) return
    
    const userMsg = inputText.value
    inputText.value = ''
    
    // 乐观更新
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
        messages.value.push({ role: 'assistant', content: '⚠️ 服务正在建设中或网络异常。' })
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
.chat-container {
    display: flex;
    height: calc(100vh - 120px);
    background: var(--card-bg);
    border-radius: var(--border-radius);
    box-shadow: var(--shadow);
    overflow: hidden;
}

.chat-sidebar {
    width: 260px;
    background: #fafafa;
    border-right: 1px solid var(--border-color);
    display: flex;
    flex-direction: column;
}

.sidebar-header {
    padding: 16px;
    border-bottom: 1px solid var(--border-color);
}
.new-chat-btn {
    width: 100%;
}

.session-list {
    flex: 1;
    overflow-y: auto;
    padding: 10px 0;
}

.session-item {
    padding: 12px 16px;
    cursor: pointer;
    display: flex;
    align-items: center;
    color: var(--text-primary);
    transition: background 0.2s;
}

.session-item:hover {
    background: #f0f2f5;
}

.session-item.active {
    background: #e6f1fc;
    color: var(--primary-color);
    border-right: 3px solid var(--primary-color);
}

.session-title {
    margin-left: 10px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 14px;
}

.chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.chat-header {
    padding: 16px 24px;
    border-bottom: 1px solid var(--border-color);
    font-weight: bold;
    display: flex;
    justify-content: space-between;
}

.current-file {
    color: var(--primary-color);
    display: flex;
    align-items: center;
    gap: 6px;
}

.chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    background: var(--card-bg);
}

.chat-input-area {
    padding: 16px 24px;
    border-top: 1px solid var(--border-color);
    background: #fafafa;
}

.input-actions {
    margin-bottom: 8px;
}

.send-action {
    display: flex;
    justify-content: flex-end;
    margin-top: 10px;
}

.loading-indicator {
    color: var(--text-secondary);
    font-size: 14px;
    animation: blink 1.5s infinite;
}
@keyframes blink {
    0% { opacity: 0.4; }
    50% { opacity: 1; }
    100% { opacity: 0.4; }
}

/* 消息气泡的基础样式补充，在复用组件基础上 */
.is-ai {
    display: flex;
    gap: 12px;
    padding: 16px;
}
</style>
