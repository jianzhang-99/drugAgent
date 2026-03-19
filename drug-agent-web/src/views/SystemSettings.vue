<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <div class="header-top">
          <div class="header-title">
            <h1>系统设置</h1>
            <p>配置系统偏好、管理任务数据、自定义解析规则</p>
          </div>
          <div class="header-actions">
            <TaskQuickView />
          </div>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <!-- 用户偏好 -->
        <el-tab-pane label="用户偏好" name="preference">
          <div class="settings-section">
            <h3 class="section-title">基础信息</h3>
            <div class="settings-grid">
              <article class="settings-card">
                <label>提交人名称</label>
                <input v-model="form.submittedBy" type="text" placeholder="例如：监管员A" />
                <p class="card-tip">任务创建时显示的提交人名称</p>
              </article>

              <article class="settings-card">
                <label>默认场景提示</label>
                <select v-model="form.defaultSceneHint">
                  <option value="AUTO">自动识别</option>
                  <option value="TENDER_REVIEW">标书审查</option>
                  <option value="CONTRACT_PRECHECK">合同预审</option>
                  <option value="RISK_ALERT">合规预警</option>
                </select>
                <p class="card-tip">新建任务时的默认场景类型</p>
              </article>
            </div>
          </div>

          <div class="settings-section">
            <h3 class="section-title">交互设置</h3>
            <div class="settings-grid">
              <article class="settings-card switch-card">
                <div>
                  <label>启用流式输出</label>
                  <p>工作台默认使用 SSE 打字机效果输出回复</p>
                </div>
                <el-switch v-model="form.streamOutput" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>上传后自动解析</label>
                  <p>文档上传后自动触发解析，无需手动点击</p>
                </div>
                <el-switch v-model="form.autoParseAfterUpload" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>启用声音提示</label>
                  <p>任务完成时播放提示音</p>
                </div>
                <el-switch v-model="form.soundEnabled" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>显示任务详情弹窗</label>
                  <p>任务创建成功后弹出详情页面</p>
                </div>
                <el-switch v-model="form.showTaskDetailOnCreate" />
              </article>
            </div>
          </div>
        </el-tab-pane>

        <!-- 任务管理 -->
        <el-tab-pane label="任务管理" name="task">
          <div class="settings-section">
            <h3 class="section-title">任务展示</h3>
            <div class="settings-grid">
              <article class="settings-card">
                <label>每页显示任务数</label>
                <select v-model="form.tasksPerPage">
                  <option :value="10">10 条</option>
                  <option :value="20">20 条</option>
                  <option :value="50">50 条</option>
                </select>
                <p class="card-tip">任务看板中每页显示的任务数量</p>
              </article>

              <article class="settings-card">
                <label>任务排序方式</label>
                <select v-model="form.taskSortBy">
                  <option value="createdAt">创建时间</option>
                  <option value="updatedAt">更新时间</option>
                  <option value="status">状态</option>
                </select>
                <p class="card-tip">任务列表的默认排序规则</p>
              </article>
            </div>
          </div>

          <div class="settings-section">
            <h3 class="section-title">自动刷新</h3>
            <div class="settings-grid">
              <article class="settings-card switch-card">
                <div>
                  <label>自动刷新任务状态</label>
                  <p>定期自动获取最新任务状态</p>
                </div>
                <el-switch v-model="form.autoRefreshTasks" />
              </article>

              <article class="settings-card" v-if="form.autoRefreshTasks">
                <label>刷新间隔（秒）</label>
                <input v-model.number="form.taskRefreshInterval" type="number" min="5" max="300" />
                <p class="card-tip">任务状态自动刷新间隔，建议 30-60 秒</p>
              </article>
            </div>
          </div>
        </el-tab-pane>

        <!-- 解析配置 -->
        <el-tab-pane label="解析配置" name="parse">
          <div class="settings-section">
            <h3 class="section-title">文档解析</h3>
            <div class="settings-grid">
              <article class="settings-card switch-card">
                <div>
                  <label>并行解析文档</label>
                  <p>同时解析多个文档提升效率</p>
                </div>
                <el-switch v-model="form.parallelParse" />
              </article>

              <article class="settings-card">
                <label>解析超时时间（秒）</label>
                <input v-model.number="form.parseTimeout" type="number" min="30" max="600" />
                <p class="card-tip">单个文档解析的最大等待时间</p>
              </article>

              <article class="settings-card">
                <label>最大并发数</label>
                <input v-model.number="form.maxConcurrentParses" type="number" min="1" max="10" />
                <p class="card-tip">并行解析时的最大并发数量</p>
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>解析完成后自动执行规则</label>
                  <p>文档解析完成后自动触发围标规则检测</p>
                </div>
                <el-switch v-model="form.autoRunRules" />
              </article>
            </div>
          </div>

          <div class="settings-section">
            <h3 class="section-title">字段提取</h3>
            <div class="settings-grid">
              <article class="settings-card switch-card">
                <div>
                  <label>自动提取联系方式</label>
                  <p>从文档中自动提取联系人、电话、邮箱</p>
                </div>
                <el-switch v-model="form.autoExtractContact" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>自动提取报价信息</label>
                  <p>从文档中自动提取报价金额和明细</p>
                </div>
                <el-switch v-model="form.autoExtractQuote" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>自动提取团队成员</label>
                  <p>从文档中自动提取项目团队人员信息</p>
                </div>
                <el-switch v-model="form.autoExtractTeam" />
              </article>

              <article class="settings-card switch-card">
                <div>
                  <label>自动提取错别字</label>
                  <p>检测并记录文档中的罕见错别字</p>
                </div>
                <el-switch v-model="form.autoExtractTypo" />
              </article>
            </div>
          </div>
        </el-tab-pane>

        <!-- 数据管理 -->
        <el-tab-pane label="数据管理" name="data">
          <div class="settings-section">
            <h3 class="section-title">本地数据</h3>
            <div class="data-stats">
              <div class="stat-card">
                <el-icon class="stat-icon"><Document /></el-icon>
                <div class="stat-info">
                  <span class="stat-value">{{ localDataStats.taskCount }}</span>
                  <span class="stat-label">本地任务数</span>
                </div>
              </div>
              <div class="stat-card">
                <el-icon class="stat-icon"><Files /></el-icon>
                <div class="stat-info">
                  <span class="stat-value">{{ localDataStats.parseResultCount }}</span>
                  <span class="stat-label">解析结果数</span>
                </div>
              </div>
              <div class="stat-card">
                <el-icon class="stat-icon"><Clock /></el-icon>
                <div class="stat-info">
                  <span class="stat-value">{{ localDataStats.auditLogCount }}</span>
                  <span class="stat-label">审计日志数</span>
                </div>
              </div>
            </div>
          </div>

          <div class="settings-section">
            <h3 class="section-title">数据操作</h3>
            <div class="action-cards">
              <article class="action-card">
                <div class="action-icon blue">
                  <el-icon><Download /></el-icon>
                </div>
                <div class="action-content">
                  <label>导出任务数据</label>
                  <p>将本地任务记录导出为 JSON 文件</p>
                </div>
                <el-button type="primary" plain @click="exportTasks">导出</el-button>
              </article>

              <article class="action-card">
                <div class="action-icon green">
                  <el-icon><Upload /></el-icon>
                </div>
                <div class="action-content">
                  <label>导入任务数据</label>
                  <p>从 JSON 文件导入任务记录</p>
                </div>
                <el-button type="success" plain @click="importTasks">导入</el-button>
              </article>

              <article class="action-card">
                <div class="action-icon orange">
                  <el-icon><RefreshRight /></el-icon>
                </div>
                <div class="action-content">
                  <label>同步服务器数据</label>
                  <p>从服务器拉取最新任务数据</p>
                </div>
                <el-button type="warning" plain @click="syncFromServer">同步</el-button>
              </article>

              <article class="action-card danger">
                <div class="action-icon red">
                  <el-icon><Delete /></el-icon>
                </div>
                <div class="action-content">
                  <label>清理本地数据</label>
                  <p>清除所有本地缓存的任务和解析结果</p>
                </div>
                <el-button type="danger" plain @click="clearLocalData">清理</el-button>
              </article>
            </div>
          </div>
        </el-tab-pane>

        <!-- 关于 -->
        <el-tab-pane label="关于" name="about">
          <div class="settings-section">
            <h3 class="section-title">系统信息</h3>
            <div class="about-card">
              <div class="about-logo">
                <div class="logo-box">
                  <el-icon><MagicStick /></el-icon>
                </div>
                <div class="about-title">
                  <h2>横渡智能监管系统</h2>
                  <p>横渡智能监管平台</p>
                </div>
              </div>
              <div class="about-info">
                <div class="info-row">
                  <span class="info-label">版本号</span>
                  <span class="info-value">v1.0.0</span>
                </div>
                <div class="info-row">
                  <span class="info-label">前端框架</span>
                  <span class="info-value">Vue 3 + Element Plus</span>
                </div>
                <div class="info-row">
                  <span class="info-label">构建时间</span>
                  <span class="info-value">{{ buildTime }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="settings-section">
            <h3 class="section-title">技术栈</h3>
            <div class="tech-cards">
              <div class="tech-card">
                <span class="tech-name">Vue 3</span>
                <span class="tech-desc">渐进式前端框架</span>
              </div>
              <div class="tech-card">
                <span class="tech-name">Element Plus</span>
                <span class="tech-desc">UI 组件库</span>
              </div>
              <div class="tech-card">
                <span class="tech-name">Spring Boot</span>
                <span class="tech-desc">后端服务框架</span>
              </div>
              <div class="tech-card">
                <span class="tech-name">LLM Agent</span>
                <span class="tech-desc">大语言模型智能体</span>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="page-footer">
        <el-button @click="resetSettings">重置</el-button>
        <el-button type="primary" @click="saveSettings">保存配置</el-button>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Document, Files, Clock, Download, Upload, RefreshRight,
  Delete, MagicStick
} from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import TaskQuickView from '../components/TaskQuickView.vue'
import {
  appendAuditLog,
  getUserPreferences,
  setUserPreferences,
  getTenderTasks,
  getTenderParseResults,
  getAuditLogs
} from '../utils/local-state'

const activeTab = ref('preference')

const defaultForm = {
  // 用户偏好
  submittedBy: 'anonymous',
  defaultSceneHint: 'AUTO',
  streamOutput: true,
  autoParseAfterUpload: true,
  soundEnabled: false,
  showTaskDetailOnCreate: true,
  // 任务管理
  tasksPerPage: 20,
  taskSortBy: 'createdAt',
  autoRefreshTasks: true,
  taskRefreshInterval: 30,
  // 解析配置
  parallelParse: true,
  parseTimeout: 120,
  maxConcurrentParses: 3,
  autoRunRules: true,
  autoExtractContact: true,
  autoExtractQuote: true,
  autoExtractTeam: true,
  autoExtractTypo: true
}

const form = reactive({ ...defaultForm })

// 加载用户配置
const loadSettings = () => {
  const prefs = getUserPreferences()
  Object.assign(form, { ...defaultForm, ...prefs })
}

// 重置设置
const resetSettings = () => {
  Object.assign(form, defaultForm)
  ElMessage.info('已重置为默认配置')
}

// 保存设置
const saveSettings = () => {
  setUserPreferences({ ...form })
  appendAuditLog({
    id: `audit-${Date.now()}`,
    type: 'SETTINGS_UPDATED',
    title: '更新系统配置',
    detail: `用户 ${form.submittedBy} 更新了系统设置`,
    createdAt: new Date().toISOString()
  })
  ElMessage.success('配置已保存')
  // 通知其他组件配置已更新
  window.dispatchEvent(new CustomEvent('settings-updated', { detail: form }))
}

// 本地数据统计
const localDataStats = computed(() => ({
  taskCount: getTenderTasks().length,
  parseResultCount: Object.keys(getTenderParseResults()).length,
  auditLogCount: getAuditLogs().length
}))

// 构建时间
const buildTime = computed(() => {
  return new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

// 导出任务数据
const exportTasks = async () => {
  try {
    const tasks = getTenderTasks()
    const parseResults = getTenderParseResults()
    const data = { tasks, parseResults, exportedAt: new Date().toISOString() }

    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `hengdu-backup-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
    appendAuditLog({
      id: `audit-${Date.now()}`,
      type: 'DATA_EXPORT',
      title: '导出任务数据',
      detail: `导出了 ${tasks.length} 条任务记录`,
      createdAt: new Date().toISOString()
    })
  } catch (error) {
    ElMessage.error('导出失败: ' + error.message)
  }
}

// 导入任务数据
const importTasks = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json'
  input.onchange = async (e) => {
    const file = e.target.files[0]
    if (!file) return

    try {
      const text = await file.text()
      const data = JSON.parse(text)

      if (data.tasks && Array.isArray(data.tasks)) {
        localStorage.setItem('tenderTasks', JSON.stringify(data.tasks))
      }
      if (data.parseResults) {
        localStorage.setItem('tenderParseResults', JSON.stringify(data.parseResults))
      }

      ElMessage.success('导入成功，请刷新页面')
      appendAuditLog({
        id: `audit-${Date.now()}`,
        type: 'DATA_IMPORT',
        title: '导入任务数据',
        detail: `导入了 ${data.tasks?.length || 0} 条任务记录`,
        createdAt: new Date().toISOString()
      })
    } catch (error) {
      ElMessage.error('导入失败: ' + error.message)
    }
  }
  input.click()
}

// 同步服务器数据
const syncFromServer = async () => {
  try {
    const response = await fetch('/api/tender/tasks')
    if (response.ok) {
      const serverTasks = await response.json()
      const localTasks = getTenderTasks()

      // 合并数据
      const merged = [...serverTasks]
      localTasks.forEach(task => {
        if (!merged.some(item => item.caseId === task.caseId)) {
          merged.push(task)
        }
      })

      localStorage.setItem('tenderTasks', JSON.stringify(merged))
      ElMessage.success('同步成功')
      appendAuditLog({
        id: `audit-${Date.now()}`,
        type: 'DATA_SYNC',
        title: '同步服务器数据',
        detail: `同步了 ${serverTasks.length} 条服务器任务`,
        createdAt: new Date().toISOString()
      })
    } else {
      ElMessage.warning('服务器暂无数据')
    }
  } catch (error) {
    ElMessage.error('同步失败: ' + error.message)
  }
}

// 清理本地数据
const clearLocalData = async () => {
  try {
    await ElMessageBox.confirm(
      '此操作将清除所有本地缓存的任务和解析结果，是否继续？',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    localStorage.removeItem('tenderTasks')
    localStorage.removeItem('tenderParseResults')
    localStorage.removeItem('recentTenderTask')

    ElMessage.success('本地数据已清理')
    appendAuditLog({
      id: `audit-${Date.now()}`,
      type: 'DATA_CLEAR',
      title: '清理本地数据',
      detail: '用户清理了所有本地缓存数据',
      createdAt: new Date().toISOString()
    })
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<style scoped>
.page-shell {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 40px;
}

.page-header {
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-title h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 850;
  color: #1a202c;
}

.header-title p {
  margin: 0;
  color: #718096;
  font-size: 14px;
}

.header-actions {
  flex-shrink: 0;
}

.settings-tabs {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.settings-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
}

.settings-section {
  margin-bottom: 32px;
}

.settings-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
  margin: 0 0 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.settings-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 18px;
}

.settings-card label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 8px;
}

.settings-card p {
  font-size: 12px;
  color: #718096;
  margin: 6px 0 0;
}

.card-tip {
  font-size: 12px;
  color: #a0aec0;
  margin-top: 6px;
}

.settings-card input[type="text"],
.settings-card input[type="number"],
.settings-card select {
  width: 100%;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #cbd5e0;
  padding: 0 12px;
  font-size: 14px;
  background: #fff;
}

.settings-card input:focus,
.settings-card select:focus {
  outline: none;
  border-color: #4299e1;
  box-shadow: 0 0 0 3px rgba(66, 153, 225, 0.15);
}

.switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.switch-card div {
  flex: 1;
  margin-right: 16px;
}

.switch-card p {
  margin-top: 4px;
}

/* 数据统计 */
.data-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: #fff;
}

.stat-card:nth-child(2) {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-card:nth-child(3) {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon {
  font-size: 32px;
  opacity: 0.9;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  opacity: 0.85;
}

/* 操作卡片 */
.action-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.action-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.action-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}

.action-icon.blue { background: linear-gradient(135deg, #667eea, #764ba2); }
.action-icon.green { background: linear-gradient(135deg, #11998e, #38ef7d); }
.action-icon.orange { background: linear-gradient(135deg, #f093fb, #f5576c); }
.action-icon.red { background: linear-gradient(135deg, #eb3349, #f45c43); }

.action-content {
  flex: 1;
}

.action-content label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #2d3748;
}

.action-content p {
  font-size: 12px;
  color: #718096;
  margin: 4px 0 0;
}

/* 关于页面 */
.about-card {
  padding: 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: #fff;
}

.about-logo {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.about-logo .logo-box {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.about-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.about-title p {
  margin: 4px 0 0;
  opacity: 0.85;
  font-size: 14px;
}

.about-info {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.info-row {
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 12px;
  opacity: 0.75;
  margin-bottom: 4px;
}

.info-value {
  font-size: 15px;
  font-weight: 600;
}

.tech-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.tech-card {
  padding: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  text-align: center;
}

.tech-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #2d3748;
  margin-bottom: 6px;
}

.tech-desc {
  font-size: 12px;
  color: #718096;
}

/* 页脚 */
.page-footer {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 900px) {
  .settings-grid,
  .action-cards,
  .tech-cards {
    grid-template-columns: 1fr;
  }

  .data-stats {
    grid-template-columns: 1fr;
  }

  .about-info {
    grid-template-columns: 1fr;
  }
}
</style>