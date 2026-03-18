<template>
  <workspace-layout>
    <section class="knowledge-page">
      <header class="knowledge-hero">
        <div class="hero-copy">
          <div class="hero-brand">
            <div class="hero-brand-icon">
              <el-icon><Coin /></el-icon>
            </div>
            <div>
              <h1>合规知识大脑</h1>
              <p>
                管理 Agent 的长期记忆与审查准则。非结构化文献将由 RAG 引擎进行向量化切片，
                结构化规则将注入场景工作流的路由策略中。
              </p>
            </div>
          </div>
        </div>
        <button class="import-trigger">
          <el-icon><Plus /></el-icon>
          <span>导入新知识</span>
        </button>
      </header>

      <section class="hero-panels">
        <article class="stat-card">
          <span class="stat-title">RAG 向量切片库</span>
          <div class="stat-row">
            <strong>24,592</strong>
            <span class="stat-status success">
              <el-icon><TrendCharts /></el-icon>
              活跃
            </span>
          </div>
        </article>

        <article class="stat-card">
          <span class="stat-title">生效审查规则</span>
          <div class="stat-row">
            <strong>18</strong>
            <span class="stat-sub">/ 24 组</span>
          </div>
        </article>

        <article class="engine-card">
          <div class="engine-head">
            <el-icon><MagicStick /></el-icon>
            <span>AGENT 知识引擎状态</span>
          </div>
          <p>
            基于 Gemini 2.5 Pro 驱动的 Embedding 模型。当前检索延时正常 (~120ms)，
            规则引擎已与 SceneRouter 保持热更新同步。
          </p>
          <div class="engine-watermark">?</div>
        </article>
      </section>

      <section class="knowledge-shell">
        <nav class="knowledge-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="tab-button"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            <span>{{ tab.label }}</span>
          </button>
        </nav>

        <div class="panel-toolbar">
          <label class="search-box">
            <el-icon><Search /></el-icon>
            <input
              v-model="searchText"
              type="text"
              placeholder="检索法规、指引或政策文献..."
            >
          </label>

          <button class="reindex-trigger" v-if="activeTab === 'rag'">
            <el-icon><Refresh /></el-icon>
            <span>手动触发全量向量化</span>
          </button>
        </div>

        <div class="table-shell">
          <table v-if="activeTab === 'rag'" class="knowledge-table">
            <thead>
              <tr>
                <th>文件名 / 文献名</th>
                <th>状态</th>
                <th>向量切片数</th>
                <th>导入时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredDocuments" :key="item.name">
                <td>
                  <div class="doc-cell">
                    <div class="doc-icon" :class="item.fileType">
                      <el-icon><component :is="item.icon" /></el-icon>
                    </div>
                    <div>
                      <div class="doc-title">{{ item.name }}</div>
                      <div class="doc-meta">{{ item.size }}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="status-pill" :class="item.statusClass">
                    <el-icon><component :is="item.statusIcon" /></el-icon>
                    {{ item.status }}
                  </span>
                </td>
                <td class="metric-cell">{{ item.chunks }}</td>
                <td class="time-cell">{{ item.importedAt }}</td>
                <td>
                  <button class="table-action" aria-label="删除文档">
                    <el-icon><Delete /></el-icon>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>

          <div v-else class="placeholder-panel">
            <div class="placeholder-badge">
              <el-icon><component :is="activeTabMeta.icon" /></el-icon>
            </div>
            <h3>{{ activeTabMeta.title }}</h3>
            <p>{{ activeTabMeta.description }}</p>
          </div>
        </div>
      </section>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  Check,
  Checked,
  Coin,
  Delete,
  Document,
  DocumentChecked,
  Files,
  Loading,
  MagicStick,
  Notebook,
  Plus,
  Refresh,
  Search,
  TrendCharts
} from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'

const activeTab = ref('rag')
const searchText = ref('')

const tabs = [
  { key: 'rag', label: '法规文献源 (RAG)', icon: Files },
  { key: 'rules', label: '审查规则组 (Rule Engine)', icon: Checked },
  { key: 'dict', label: '实体字典库 (Dictionaries)', icon: Notebook }
]

const documents = [
  {
    name: '《国家医疗器械采购管理规范(2025版)》.pdf',
    size: '2.4 MB',
    fileType: 'pdf',
    icon: Document,
    status: '已向量化',
    statusClass: 'ready',
    statusIcon: Check,
    chunks: '1452 Chunks',
    importedAt: '2026-03-01'
  },
  {
    name: '骨科耗材历年最高限价指导文件.docx',
    size: '1.1 MB',
    fileType: 'doc',
    icon: DocumentChecked,
    status: '已向量化',
    statusClass: 'ready',
    statusIcon: Check,
    chunks: '840 Chunks',
    importedAt: '2026-03-10'
  },
  {
    name: '内部采购合同标准条款模板_V3.pdf',
    size: '0.8 MB',
    fileType: 'pdf',
    icon: Document,
    status: '切片解析中',
    statusClass: 'processing',
    statusIcon: Loading,
    chunks: '–',
    importedAt: '刚刚'
  }
]

const placeholders = {
  rules: {
    icon: Checked,
    title: '审查规则组',
    description: '这里将展示可启停的规则分组、命中优先级和适用场景，用于注入审查工作流。'
  },
  dict: {
    icon: Notebook,
    title: '实体字典库',
    description: '这里将维护药械名称、规格别名、科室实体和供应商词典，支撑 NER 与规则归一化。'
  }
}

const filteredDocuments = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return documents
  return documents.filter((item) => item.name.toLowerCase().includes(keyword))
})

const activeTabMeta = computed(() => placeholders[activeTab.value] ?? placeholders.rules)
</script>

<style scoped>
.knowledge-page {
  padding: 44px 38px 56px;
  max-width: 1480px;
}

.knowledge-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 34px;
}

.hero-brand {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.hero-brand-icon {
  width: 78px;
  height: 78px;
  border-radius: 22px;
  background: linear-gradient(180deg, #d8f7e7 0%, #b8efd3 100%);
  display: grid;
  place-items: center;
  color: #0b8a63;
  font-size: 40px;
  box-shadow: 0 10px 30px rgba(23, 162, 108, 0.15);
  flex-shrink: 0;
}

h1 {
  margin: 0 0 12px;
  font-size: 54px;
  line-height: 1.02;
  letter-spacing: -2px;
  color: #0d1b3d;
  font-weight: 900;
}

.hero-copy p {
  margin: 0;
  max-width: 980px;
  font-size: 22px;
  line-height: 1.55;
  color: #6f83a4;
  font-weight: 600;
}

.import-trigger {
  height: 84px;
  border: 0;
  border-radius: 26px;
  background: linear-gradient(180deg, #121c35 0%, #0d1730 100%);
  color: white;
  padding: 0 34px;
  display: inline-flex;
  align-items: center;
  gap: 14px;
  font-size: 24px;
  font-weight: 800;
  box-shadow: 0 16px 28px rgba(13, 23, 48, 0.18);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.import-trigger:hover {
  transform: translateY(-2px);
  box-shadow: 0 22px 36px rgba(13, 23, 48, 0.24);
}

.hero-panels {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(360px, 2fr);
  gap: 24px;
  margin-bottom: 34px;
}

.stat-card,
.engine-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid #dbe6f3;
  border-radius: 30px;
  box-shadow: 0 12px 32px rgba(28, 53, 91, 0.08);
}

.stat-card {
  padding: 34px 38px;
}

.stat-title {
  display: inline-block;
  margin-bottom: 30px;
  color: #8c9db7;
  font-size: 18px;
  font-weight: 800;
}

.stat-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.stat-row strong {
  font-size: 52px;
  line-height: 1;
  color: #152241;
  font-weight: 900;
  letter-spacing: -1px;
}

.stat-status,
.stat-sub {
  font-size: 18px;
  font-weight: 700;
}

.stat-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.stat-status.success {
  color: #14b86f;
}

.stat-sub {
  color: #91a2be;
}

.engine-card {
  position: relative;
  overflow: hidden;
  padding: 34px 38px;
  background: linear-gradient(135deg, #1a2439 0%, #18233a 100%);
  border-color: rgba(70, 90, 128, 0.36);
  color: #edf3ff;
}

.engine-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 0.5px;
  color: #b9d4ff;
}

.engine-card p {
  position: relative;
  margin: 0;
  max-width: 88%;
  font-size: 22px;
  line-height: 1.55;
  font-weight: 700;
  color: rgba(244, 247, 255, 0.96);
  z-index: 1;
}

.engine-watermark {
  position: absolute;
  right: 18px;
  bottom: -6px;
  font-size: 200px;
  font-weight: 900;
  line-height: 0.8;
  color: rgba(167, 185, 217, 0.08);
  pointer-events: none;
}

.knowledge-shell {
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #dbe6f3;
  border-radius: 38px;
  box-shadow: 0 20px 42px rgba(25, 47, 84, 0.08);
  overflow: hidden;
}

.knowledge-tabs {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 18px 18px 0;
  border-bottom: 1px solid #e5edf7;
  overflow-x: auto;
}

.tab-button {
  position: relative;
  border: 0;
  background: transparent;
  padding: 20px 26px 24px;
  border-radius: 24px 24px 0 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #7183a0;
  font-size: 20px;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.tab-button.active {
  background: #ffffff;
  color: #2b66ff;
  box-shadow: inset 0 -4px 0 #2b66ff;
}

.panel-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 30px 30px 24px;
}

.search-box {
  height: 58px;
  min-width: 320px;
  width: 480px;
  border-radius: 18px;
  border: 1px solid #dbe6f3;
  background: #f8fbff;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  color: #93a3bc;
}

.search-box input {
  flex: 1;
  border: 0;
  outline: none;
  background: transparent;
  color: #223252;
  font-size: 18px;
  font-weight: 600;
}

.search-box input::placeholder {
  color: #a5b4ca;
}

.reindex-trigger {
  border: 0;
  background: transparent;
  color: #2b66ff;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 800;
  cursor: pointer;
}

.table-shell {
  min-height: 720px;
}

.knowledge-table {
  width: 100%;
  border-collapse: collapse;
}

.knowledge-table thead th {
  text-align: left;
  font-size: 16px;
  font-weight: 800;
  color: #98a8c0;
  background: #fbfdff;
  padding: 22px 30px;
  border-top: 1px solid #edf3fa;
  border-bottom: 1px solid #edf3fa;
}

.knowledge-table tbody td {
  padding: 32px 30px;
  border-bottom: 1px solid #edf3fa;
  vertical-align: middle;
}

.doc-cell {
  display: flex;
  align-items: center;
  gap: 18px;
}

.doc-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  font-size: 28px;
}

.doc-icon.pdf {
  color: #ff4966;
}

.doc-icon.doc {
  color: #3e7fff;
}

.doc-title {
  font-size: 24px;
  font-weight: 800;
  color: #172645;
  margin-bottom: 6px;
}

.doc-meta,
.metric-cell,
.time-cell {
  font-size: 16px;
  font-weight: 600;
  color: #8da0bc;
}

.metric-cell,
.time-cell {
  color: #5d7092;
  font-size: 18px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 800;
}

.status-pill.ready {
  color: #13a96b;
  background: #ecfdf3;
  border: 1px solid #b9efd2;
}

.status-pill.processing {
  color: #2b66ff;
  background: #eef4ff;
  border: 1px solid #cadcff;
}

.table-action {
  width: 40px;
  height: 40px;
  border: 0;
  background: transparent;
  color: #91a4c0;
  font-size: 22px;
  cursor: pointer;
}

.placeholder-panel {
  min-height: 720px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 24px;
  color: #6e80a0;
}

.placeholder-badge {
  width: 82px;
  height: 82px;
  border-radius: 26px;
  display: grid;
  place-items: center;
  background: #eef4ff;
  color: #2b66ff;
  font-size: 36px;
  margin-bottom: 18px;
}

.placeholder-panel h3 {
  margin: 0 0 10px;
  font-size: 28px;
  color: #182748;
}

.placeholder-panel p {
  margin: 0;
  max-width: 560px;
  font-size: 18px;
  line-height: 1.7;
  font-weight: 600;
}

@media (max-width: 1320px) {
  .hero-panels {
    grid-template-columns: 1fr 1fr;
  }

  .engine-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 980px) {
  .knowledge-page {
    padding: 26px 18px 36px;
  }

  .knowledge-hero,
  .panel-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-brand {
    flex-direction: column;
    gap: 18px;
  }

  .hero-panels {
    grid-template-columns: 1fr;
  }

  h1 {
    font-size: 38px;
    letter-spacing: -1px;
  }

  .hero-copy p,
  .engine-card p {
    font-size: 18px;
  }

  .import-trigger,
  .search-box {
    width: 100%;
  }

  .knowledge-tabs {
    padding-top: 8px;
  }

  .tab-button {
    font-size: 16px;
    padding: 16px 16px 18px;
  }

  .knowledge-table {
    display: block;
    overflow-x: auto;
    white-space: nowrap;
  }

  .doc-title {
    font-size: 20px;
  }
}
</style>
