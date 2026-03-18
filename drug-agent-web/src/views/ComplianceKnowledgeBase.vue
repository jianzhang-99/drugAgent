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
              <p>管理法规文献、规则组和实体字典，让 Agent 的知识底座可维护、可追踪、可持续更新。</p>
            </div>
          </div>
        </div>
        <div class="hero-actions">
          <input ref="knowledgeInput" type="file" multiple style="display: none" @change="handleImportKnowledge" />
          <button class="import-trigger" @click="knowledgeInput?.click()">
            <el-icon><Plus /></el-icon>
            <span>导入新知识</span>
          </button>
        </div>
      </header>

      <section class="hero-panels">
        <article class="stat-card">
          <span class="stat-title">RAG 文档数</span>
          <div class="stat-row">
            <strong>{{ documents.length }}</strong>
            <span class="stat-status success">已接入</span>
          </div>
        </article>

        <article class="stat-card">
          <span class="stat-title">启用规则组</span>
          <div class="stat-row">
            <strong>{{ enabledRulesCount }}</strong>
            <span class="stat-sub">/ {{ rules.length }} 组</span>
          </div>
        </article>

        <article class="engine-card">
          <div class="engine-head">
            <el-icon><MagicStick /></el-icon>
            <span>知识引擎状态</span>
          </div>
          <p>当前前端已支持知识文档管理、规则组启停、实体字典维护和本地持久化，可直接用于演示和日常录入。</p>
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
            <input v-model="searchText" type="text" :placeholder="activeTabPlaceholder" />
          </label>

          <button v-if="activeTab === 'rag'" class="reindex-trigger" @click="reindexDocuments">
            <el-icon><Refresh /></el-icon>
            <span>手动触发全量向量化</span>
          </button>

          <button v-if="activeTab === 'rules'" class="reindex-trigger" @click="addRule">
            <el-icon><Plus /></el-icon>
            <span>新增规则组</span>
          </button>

          <button v-if="activeTab === 'dict'" class="reindex-trigger" @click="addDictionary">
            <el-icon><Plus /></el-icon>
            <span>新增词条</span>
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
              <tr v-if="filteredDocuments.length === 0">
                <td colspan="5" class="empty-cell">暂无知识文档，请先导入文件。</td>
              </tr>
              <tr v-for="item in filteredDocuments" :key="item.id">
                <td>
                  <div class="doc-cell">
                    <div class="doc-icon" :class="item.fileType">
                      <el-icon><Document /></el-icon>
                    </div>
                    <div>
                      <div class="doc-title">{{ item.name }}</div>
                      <div class="doc-meta">{{ item.size }}</div>
                    </div>
                  </div>
                </td>
                <td><span class="status-pill" :class="item.statusClass">{{ item.status }}</span></td>
                <td class="metric-cell">{{ item.chunks }}</td>
                <td class="time-cell">{{ item.importedAt }}</td>
                <td class="action-cell">
                  <button class="table-action" @click="markDocumentReady(item.id)">重建</button>
                  <button class="table-action danger" @click="removeDocument(item.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>

          <table v-else-if="activeTab === 'rules'" class="knowledge-table">
            <thead>
              <tr>
                <th>规则组名称</th>
                <th>适用场景</th>
                <th>优先级</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rule in filteredRules" :key="rule.id">
                <td>{{ rule.name }}</td>
                <td>{{ rule.scene }}</td>
                <td>{{ rule.priority }}</td>
                <td><span class="status-pill" :class="rule.enabled ? 'ready' : 'processing'">{{ rule.enabled ? '已启用' : '已停用' }}</span></td>
                <td class="action-cell">
                  <button class="table-action" @click="toggleRule(rule.id)">{{ rule.enabled ? '停用' : '启用' }}</button>
                  <button class="table-action danger" @click="removeRule(rule.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>

          <table v-else class="knowledge-table">
            <thead>
              <tr>
                <th>实体类型</th>
                <th>标准名称</th>
                <th>别名</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredDictionaries" :key="item.id">
                <td>{{ item.type }}</td>
                <td>{{ item.term }}</td>
                <td>{{ item.alias }}</td>
                <td class="action-cell">
                  <button class="table-action" @click="renameDictionary(item.id)">编辑</button>
                  <button class="table-action danger" @click="removeDictionary(item.id)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </workspace-layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Coin, Document, Files, MagicStick, Notebook, Plus, Refresh, Search, Checked } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
import {
  appendAuditLog,
  getKnowledgeDictionaries,
  getKnowledgeDocuments,
  getKnowledgeRules,
  setKnowledgeDictionaries,
  setKnowledgeDocuments,
  setKnowledgeRules
} from '../utils/local-state'

const activeTab = ref('rag')
const searchText = ref('')
const knowledgeInput = ref(null)
const documents = ref(getKnowledgeDocuments())
const rules = ref(getKnowledgeRules())
const dictionaries = ref(getKnowledgeDictionaries())

const tabs = [
  { key: 'rag', label: '法规文献源 (RAG)', icon: Files },
  { key: 'rules', label: '审查规则组 (Rule Engine)', icon: Checked },
  { key: 'dict', label: '实体字典库 (Dictionaries)', icon: Notebook }
]

const enabledRulesCount = computed(() => rules.value.filter((rule) => rule.enabled).length)

const activeTabPlaceholder = computed(() => {
  if (activeTab.value === 'rules') return '检索规则组名称或适用场景...'
  if (activeTab.value === 'dict') return '检索实体名称、别名或类型...'
  return '检索法规、指引或政策文献...'
})

const keywordMatches = (value) => value.toLowerCase().includes(searchText.value.trim().toLowerCase())

const filteredDocuments = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return documents.value
  return documents.value.filter((item) => item.name.toLowerCase().includes(keyword))
})

const filteredRules = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return rules.value
  return rules.value.filter((item) => keywordMatches(item.name) || keywordMatches(item.scene))
})

const filteredDictionaries = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return dictionaries.value
  return dictionaries.value.filter((item) => keywordMatches(item.type) || keywordMatches(item.term) || keywordMatches(item.alias))
})

const persistDocuments = () => setKnowledgeDocuments(documents.value)
const persistRules = () => setKnowledgeRules(rules.value)
const persistDictionaries = () => setKnowledgeDictionaries(dictionaries.value)

const handleImportKnowledge = (event) => {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  const imported = files.map((file) => ({
    id: `doc-${Date.now()}-${file.name}`,
    name: file.name,
    size: `${(file.size / 1024 / 1024).toFixed(1)} MB`,
    fileType: file.name.toLowerCase().endsWith('.pdf') ? 'pdf' : 'doc',
    status: '已向量化',
    statusClass: 'ready',
    chunks: `${Math.max(12, Math.round(file.size / 2048))} Chunks`,
    importedAt: new Date().toLocaleDateString('zh-CN')
  }))

  documents.value = [...imported, ...documents.value]
  persistDocuments()
  appendAuditLog({
    id: `audit-${Date.now()}`,
    type: 'KNOWLEDGE_IMPORTED',
    title: '导入知识文档',
    detail: `新增导入 ${imported.length} 份知识文件`,
    createdAt: new Date().toISOString()
  })
  ElMessage.success(`成功导入 ${imported.length} 份知识文件`)
  event.target.value = ''
}

const markDocumentReady = (id) => {
  documents.value = documents.value.map((item) => item.id === id ? { ...item, status: '已向量化', statusClass: 'ready' } : item)
  persistDocuments()
  ElMessage.success('已触发重建索引')
}

const removeDocument = async (id) => {
  await ElMessageBox.confirm('确认删除这份知识文档吗？删除后需要重新导入。', '删除确认', { type: 'warning' })
  documents.value = documents.value.filter((item) => item.id !== id)
  persistDocuments()
  ElMessage.success('知识文档已删除')
}

const reindexDocuments = () => {
  documents.value = documents.value.map((item) => ({ ...item, status: '已向量化', statusClass: 'ready' }))
  persistDocuments()
  ElMessage.success('已触发全量向量化')
}

const addRule = () => {
  rules.value = [
    {
      id: `rule-${Date.now()}`,
      name: `新规则组 ${rules.value.length + 1}`,
      scene: '标书审查',
      enabled: true,
      priority: '中'
    },
    ...rules.value
  ]
  persistRules()
}

const toggleRule = (id) => {
  rules.value = rules.value.map((item) => item.id === id ? { ...item, enabled: !item.enabled } : item)
  persistRules()
}

const removeRule = (id) => {
  rules.value = rules.value.filter((item) => item.id !== id)
  persistRules()
}

const addDictionary = () => {
  dictionaries.value = [
    {
      id: `dict-${Date.now()}`,
      type: '药品',
      term: `新词条 ${dictionaries.value.length + 1}`,
      alias: '待补充'
    },
    ...dictionaries.value
  ]
  persistDictionaries()
}

const renameDictionary = (id) => {
  dictionaries.value = dictionaries.value.map((item) => item.id === id ? { ...item, alias: `${item.alias}（已编辑）` } : item)
  persistDictionaries()
}

const removeDictionary = (id) => {
  dictionaries.value = dictionaries.value.filter((item) => item.id !== id)
  persistDictionaries()
}
</script>

<style scoped>
.knowledge-page {
  padding: 44px 38px 56px;
  max-width: 1480px;
}

.knowledge-hero,
.hero-brand,
.panel-toolbar,
.action-cell {
  display: flex;
}

.knowledge-hero {
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 34px;
}

.hero-brand {
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
}

h1 {
  margin: 0 0 10px;
  font-size: 34px;
  font-weight: 850;
}

.hero-copy p,
.engine-card p {
  color: var(--text-sub);
  line-height: 1.7;
  margin: 0;
}

.import-trigger,
.reindex-trigger,
.table-action {
  border: 0;
  border-radius: 14px;
  cursor: pointer;
  font-weight: 700;
}

.import-trigger,
.reindex-trigger {
  height: 48px;
  padding: 0 18px;
  background: white;
  border: 1px solid var(--border-light);
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.hero-panels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin-bottom: 28px;
}

.stat-card,
.engine-card,
.knowledge-shell {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.stat-card,
.engine-card {
  padding: 24px;
}

.stat-title,
.stat-sub {
  color: var(--text-sub);
}

.stat-row,
.engine-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.stat-row strong {
  font-size: 36px;
}

.knowledge-shell {
  padding: 24px;
}

.knowledge-tabs {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.tab-button {
  border: 1px solid var(--border-light);
  background: #f8fafc;
  border-radius: 14px;
  padding: 12px 18px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  font-weight: 700;
}

.tab-button.active {
  background: var(--primary-color);
  color: white;
}

.panel-toolbar {
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px;
  border: 1px solid var(--border-light);
  border-radius: 14px;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: none;
  background: transparent;
}

.knowledge-table {
  width: 100%;
  border-collapse: collapse;
}

.knowledge-table th,
.knowledge-table td {
  padding: 16px 12px;
  border-bottom: 1px solid var(--border-light);
  text-align: left;
}

.doc-cell {
  display: flex;
  align-items: center;
  gap: 14px;
}

.doc-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #eef2ff;
  color: #4f46e5;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.ready {
  background: #ecfdf3;
  color: #15803d;
}

.processing {
  background: #fff7ed;
  color: #ea580c;
}

.action-cell {
  gap: 10px;
}

.table-action {
  background: #eef2ff;
  color: #334155;
  padding: 8px 12px;
}

.table-action.danger {
  background: #fef2f2;
  color: #dc2626;
}

.empty-cell {
  text-align: center;
  color: var(--text-sub);
}

@media (max-width: 900px) {
  .hero-panels {
    grid-template-columns: 1fr;
  }

  .knowledge-tabs,
  .panel-toolbar,
  .knowledge-hero {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
