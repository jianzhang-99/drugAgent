<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <h1>偏好与配置</h1>
        <p>在不接入完整账号体系之前，前端先提供本地偏好配置，提升联调与演示体验。</p>
      </header>

      <div class="settings-grid">
        <article class="settings-card">
          <label>提交人</label>
          <input v-model="form.submittedBy" type="text" placeholder="例如：监管员A" />
        </article>

        <article class="settings-card">
          <label>默认场景提示</label>
          <select v-model="form.defaultSceneHint">
            <option value="AUTO">自动识别</option>
            <option value="TENDER_REVIEW">标书审查</option>
            <option value="CONTRACT_PRECHECK">合同预审</option>
            <option value="RISK_ALERT">合规预警</option>
          </select>
        </article>

        <article class="settings-card switch-card">
          <div>
            <label>启用流式输出</label>
            <p>工作台默认使用 SSE 打字机效果输出回复。</p>
          </div>
          <input v-model="form.streamOutput" type="checkbox" />
        </article>

        <article class="settings-card switch-card">
          <div>
            <label>上传后自动解析</label>
            <p>后续任务详情页可根据该开关自动触发文档解析。</p>
          </div>
          <input v-model="form.autoParseAfterUpload" type="checkbox" />
        </article>
      </div>

      <button class="save-btn" @click="saveSettings">保存配置</button>
    </section>
  </workspace-layout>
</template>

<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import { appendAuditLog, getUserPreferences, setUserPreferences } from '../utils/local-state'

const form = reactive({ ...getUserPreferences() })

const saveSettings = () => {
  setUserPreferences({ ...form })
  appendAuditLog({
    id: `audit-${Date.now()}`,
    type: 'SETTINGS_UPDATED',
    title: '更新前端偏好配置',
    detail: `提交人设置为 ${form.submittedBy || 'anonymous'}`,
    createdAt: new Date().toISOString()
  })
  ElMessage.success('配置已保存')
}
</script>

<style scoped>
.page-shell {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 30px;
  font-weight: 850;
}

.page-header p,
.switch-card p {
  color: var(--text-sub);
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 24px;
}

.settings-card {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 22px;
  box-shadow: var(--shadow-sm);
}

.settings-card label {
  display: block;
  margin-bottom: 12px;
  font-weight: 700;
}

.settings-card input[type="text"],
.settings-card select {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  border: 1px solid var(--border-light);
  padding: 0 12px;
}

.switch-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.save-btn {
  margin-top: 24px;
  border: 0;
  border-radius: 14px;
  padding: 12px 18px;
  background: var(--primary-color);
  color: white;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
