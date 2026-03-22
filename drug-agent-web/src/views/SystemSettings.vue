<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <div class="header-top">
          <div class="header-title">
            <h1>偏好与系统配置</h1>
            <p>管理当前账户的基础信息、Agent 底层大模型参数以及文档处理的合规留痕策略</p>
          </div>
          <div class="header-actions">
            <TaskQuickView />
          </div>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="settings-tabs" tab-position="top">
        <!-- 基础资料 -->
        <el-tab-pane label="基础资料" name="profile">
          <div class="settings-content">
            <div class="user-profile">
              <div class="profile-header">
                <div class="avatar">
                  <el-avatar :size="64" class="bg-slate-200">
                    <el-icon><User /></el-icon>
                  </el-avatar>
                </div>
                <div class="profile-info">
                  <h3>管理员 (Admin)</h3>
                  <p class="user-id">ID: USR-882194-A</p>
                </div>
              </div>

              <div class="permissions-info">
                <el-alert
                  title="系统权限与角色限制"
                  type="warning"
                  :closable="false"
                  show-icon
                >
                  <template #icon>
                    <el-icon><Lock /></el-icon>
                  </template>
                  <p>当前账户拥有"全场景工作流调用"及"知识库读写"权限。如需修改权限配置，请联系 IT 部门通过 SSO 同步更新。</p>
                </el-alert>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- Agent引擎偏好 -->
        <el-tab-pane label="Agent引擎偏好" name="engine">
          <div class="settings-content">
            <div class="settings-section">
              <h3 class="section-title">
                <el-icon><Cpu /></el-icon>
                底层模型与路由规则
              </h3>
              <p class="section-desc">调整支撑 Agent 运行的核心大语言模型及推理参数</p>

              <div class="model-selection">
                <label class="block text-sm font-bold text-slate-800 mb-4">基座大模型选择 (Default Model)</label>
                <div class="model-grid">
                  <div
                    class="model-card"
                    :class="{ 'selected': form.selectedModel === 'gemini-flash' }"
                    @click="form.selectedModel = 'gemini-flash'"
                  >
                    <div class="selected-badge" v-if="form.selectedModel === 'gemini-flash'">
                      <el-icon><Check /></el-icon>
                    </div>
                    <h4>Gemini 2.5 Flash</h4>
                    <p>响应速度极快，适合日常标书查重与快速文本分发路由。（当前推荐）</p>
                  </div>
                  <div
                    class="model-card"
                    :class="{ 'selected': form.selectedModel === 'gemini-pro' }"
                    @click="form.selectedModel = 'gemini-pro'"
                  >
                    <div class="selected-badge" v-if="form.selectedModel === 'gemini-pro'">
                      <el-icon><Check /></el-icon>
                    </div>
                    <h4>Gemini 2.5 Pro</h4>
                    <p>具备强大的逻辑推理能力，适合处理复杂的法律合同交叉比对。</p>
                  </div>
                </div>
              </div>

              <div class="temperature-control">
                <div class="temp-header">
                  <label class="block text-sm font-bold text-slate-800">推理严谨度 (Temperature 控制)</label>
                  <span class="temp-value">{{ form.temperature }}% 幻觉容忍</span>
                </div>
                <p class="temp-desc">
                  在医疗合规场景中，建议将此值设定在较低水平（&lt; 30%），以确保模型严格遵循规则引擎的结论，减少开放式幻觉。
                </p>
                <div class="temp-slider">
                  <span class="slider-label">严密事实</span>
                  <el-slider
                    v-model="form.temperature"
                    :min="0"
                    :max="100"
                    :step="1"
                    show-stops
                  />
                  <span class="slider-label">发散创造</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 数据与合规策略 -->
        <el-tab-pane label="数据与合规策略" name="compliance">
          <div class="settings-content">
            <div class="settings-section">
              <h3 class="section-title">
                <el-icon><ShieldAlert /></el-icon>
                数据脱敏与留痕策略
              </h3>
              <p class="section-desc">控制上传至 Agent 的敏感文件如何被存储与销毁</p>

              <div class="settings-grid">
                <div class="settings-card switch-card" @click="form.autoCleanCache = !form.autoCleanCache">
                  <div>
                    <h4 class="mb-1">
                      <el-icon><Delete /></el-icon>
                      自动清理源文件缓存
                    </h4>
                    <p class="card-tip">
                      开启后，审查工作流执行完毕 24 小时内，将自动物理销毁云端存储的上传附件 (.pdf/.docx)，仅保留向量化切片与摘要。
                    </p>
                  </div>
                  <el-switch v-model="form.autoCleanCache" />
                </div>

                <div class="settings-card">
                  <h4 class="mb-1">
                    <el-icon><Clock /></el-icon>
                    审计追踪日志 (Trace Log) 留存期限
                  </h4>
                  <el-select v-model="form.logRetention" placeholder="选择留存期限" class="w-full">
                    <el-option label="保留 30 天" value="30" />
                    <el-option label="保留 90 天 (合规推荐)" value="90" />
                    <el-option label="保留 180 天" value="180" />
                    <el-option label="永久保留 (消耗较高存储)" value="forever" />
                  </el-select>
                  <p class="card-tip">设置审计日志的保留时间</p>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 通知与展示 -->
        <el-tab-pane label="通知与展示" name="display">
          <div class="settings-content">
            <div class="settings-section">
              <h3 class="section-title">
                <el-icon><View /></el-icon>
                视图展示与系统通知
              </h3>

              <div class="settings-grid">
                <div class="settings-card switch-card" @click="form.traceEnabled = !form.traceEnabled">
                  <div>
                    <h4 class="mb-1">
                      <el-icon><Terminal /></el-icon>
                      默认展开 Agent 思考图谱 (Trace)
                    </h4>
                    <p class="card-tip">
                      工作台中生成审查报告时，默认展示 Agent 的调用节点及执行日志，适合审计员及高级审核专家。
                    </p>
                  </div>
                  <el-switch v-model="form.traceEnabled" />
                </div>

                <div class="settings-card switch-card" @click="form.riskAlertEnabled = !form.riskAlertEnabled">
                  <div>
                    <h4 class="mb-1">
                      <el-icon><Bell /></el-icon>
                      高风险命中外部告警
                    </h4>
                    <p class="card-tip">
                      当任何一个后台任务发现"高风险 (High Risk)" 级别违规时，自动通过邮件或企业微信发送告警通知。
                    </p>
                  </div>
                  <el-switch v-model="form.riskAlertEnabled" />
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="page-footer">
        <el-button @click="resetSettings">重置</el-button>
        <el-button type="primary" @click="saveSettings">保存系统配置</el-button>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, Cpu, ShieldAlert, View, Terminal, Bell,
  Check, Lock, Delete, Clock
} from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import TaskQuickView from '../components/TaskQuickView.vue'
import {
  appendAuditLog,
  getUserPreferences,
  setUserPreferences
} from '../utils/local-state'

const activeTab = ref('profile')

const defaultForm = {
  // 基础资料
  selectedModel: 'gemini-flash',
  // Agent引擎偏好
  temperature: 20,
  // 数据与合规策略
  autoCleanCache: true,
  logRetention: '90',
  // 通知与展示
  traceEnabled: true,
  riskAlertEnabled: false,
  // 任务与输出
  streamOutput: true,
  autoParseAfterUpload: true,
  taskSortBy: 'createdAt',
  autoRefreshTasks: true,
  taskRefreshInterval: 30
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
    detail: `用户更新了系统设置`,
    createdAt: new Date().toISOString()
  })
  ElMessage.success('配置已保存')
  // 通知其他组件配置已更新
  window.dispatchEvent(new CustomEvent('settings-updated', { detail: form }))
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