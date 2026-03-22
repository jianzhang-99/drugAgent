<template>
  <workspace-layout>
    <section class="knowledge-page">
      <header class="page-header">
        <div class="header-top">
          <div class="header-title">
            <h1>合规知识大脑</h1>
            <p>管理 Agent 的长期记忆与审查准则，包括 RAG 向量切片库和规则引擎字典</p>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="showAddDialog = true">
              <el-icon><Plus /></el-icon>
              新增知识
            </el-button>
          </div>
        </div>
      </header>

      <!-- 统计卡片 -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon bg-indigo">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.totalKnowledge }}</span>
            <span class="stat-label">知识条目</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon bg-emerald">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.totalChunks }}</span>
            <span class="stat-label">向量切片</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon bg-amber">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.ruleCount }}</span>
            <span class="stat-label">规则引擎</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon bg-rose">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stats.policyCount }}</span>
            <span class="stat-label">合规政策</span>
          </div>
        </div>
      </div>

      <!-- 知识分类Tab -->
      <div class="knowledge-tabs">
        <el-radio-group v-model="activeTab" @change="handleTabChange">
          <el-radio-button label="all">
            全部知识
            <el-badge :value="knowledgeList.length" type="primary" />
          </el-radio-button>
          <el-radio-button label="rule">
            规则引擎
            <el-badge :value="getCountByCategory('rule')" type="danger" />
          </el-radio-button>
          <el-radio-button label="rag">
            RAG向量库
            <el-badge :value="getCountByCategory('rag')" type="success" />
          </el-radio-button>
          <el-radio-button label="policy">
            合规政策
            <el-badge :value="getCountByCategory('policy')" type="warning" />
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 搜索和筛选 -->
      <div class="filter-bar">
        <el-input
          v-model="searchText"
          placeholder="搜索知识名称或描述..."
          clearable
          class="search-input"
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="sortBy" placeholder="排序方式" class="sort-select">
          <el-option label="最近更新" value="updatedAt" />
          <el-option label="名称排序" value="title" />
          <el-option label="切片数量" value="chunks" />
        </el-select>
      </div>

      <!-- 知识列表 -->
      <div class="knowledge-list">
        <div
          v-for="item in displayedKnowledge"
          :key="item.id"
          class="knowledge-card"
          @click="viewDetail(item)"
        >
          <div class="card-header">
            <div class="card-title-row">
              <h3 class="card-title">{{ item.title }}</h3>
              <el-tag :type="getTagType(item.category)" size="small">
                {{ getCategoryLabel(item.category) }}
              </el-tag>
            </div>
            <p class="card-desc">{{ item.description }}</p>
          </div>

          <div class="card-stats">
            <div class="stat-item">
              <el-icon><Document /></el-icon>
              <span>{{ item.chunks }} 个切片</span>
            </div>
            <div class="stat-item">
              <el-icon><Clock /></el-icon>
              <span>更新于 {{ item.updatedAt }}</span>
            </div>
          </div>

          <div class="card-tags" v-if="item.tags && item.tags.length">
            <el-tag
              v-for="tag in item.tags.slice(0, 3)"
              :key="tag"
              size="small"
              type="info"
            >
              {{ tag }}
            </el-tag>
            <el-tag v-if="item.tags.length > 3" size="small" type="info">
              +{{ item.tags.length - 3 }}
            </el-tag>
          </div>

          <div class="card-actions" @click.stop>
            <el-button size="small" @click="editKnowledge(item)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" type="danger" plain @click="deleteKnowledge(item)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>

        <el-empty v-if="displayedKnowledge.length === 0" description="暂无知识条目" />
      </div>

      <!-- 新增/编辑对话框 -->
      <el-dialog
        v-model="showAddDialog"
        :title="editingItem ? '编辑知识' : '新增知识'"
        width="600px"
        :close-on-click-modal="false"
      >
        <el-form :model="form" label-width="80px">
          <el-form-item label="名称">
            <el-input v-model="form.title" placeholder="请输入知识名称" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.category" placeholder="请选择分类">
              <el-option label="规则引擎" value="rule" />
              <el-option label="RAG向量库" value="rag" />
              <el-option label="合规政策" value="policy" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              placeholder="请输入知识描述"
            />
          </el-form-item>
          <el-form-item label="标签">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              placeholder="请输入标签"
              style="width: 100%"
            >
              <el-option
                v-for="tag in availableTags"
                :key="tag"
                :label="tag"
                :value="tag"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showAddDialog = false">取消</el-button>
          <el-button type="primary" @click="saveKnowledge">
            {{ editingItem ? '保存' : '创建' }}
          </el-button>
        </template>
      </el-dialog>

      <!-- 详情对话框 -->
      <el-dialog
        v-model="showDetailDialog"
        :title="selectedItem?.title"
        width="700px"
      >
        <div v-if="selectedItem" class="detail-content">
          <div class="detail-header">
            <el-tag :type="getTagType(selectedItem.category)" size="large">
              {{ getCategoryLabel(selectedItem.category) }}
            </el-tag>
            <span class="detail-date">更新于 {{ selectedItem.updatedAt }}</span>
          </div>

          <div class="detail-section">
            <h4>描述</h4>
            <p>{{ selectedItem.description }}</p>
          </div>

          <div class="detail-section">
            <h4>标签</h4>
            <div class="detail-tags">
              <el-tag
                v-for="tag in selectedItem.tags"
                :key="tag"
                type="info"
              >
                {{ tag }}
              </el-tag>
            </div>
          </div>

          <div class="detail-section">
            <h4>向量切片预览</h4>
            <div class="chunks-preview">
              <div
                v-for="(chunk, idx) in selectedItem.chunksPreview"
                :key="idx"
                class="chunk-item"
              >
                <span class="chunk-index">{{ idx + 1 }}</span>
                <span class="chunk-text">{{ chunk }}</span>
              </div>
            </div>
          </div>
        </div>
        <template #footer>
          <el-button @click="showDetailDialog = false">关闭</el-button>
          <el-button type="primary" @click="editKnowledge(selectedItem); showDetailDialog = false">
            编辑
          </el-button>
        </template>
      </el-dialog>
    </section>
  </workspace-layout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Collection,
  Document,
  Connection,
  Warning,
  Search,
  Edit,
  Delete,
  Clock
} from '@element-plus/icons-vue'
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'

// 状态
const activeTab = ref('all')
const searchText = ref('')
const sortBy = ref('updatedAt')
const showAddDialog = ref(false)
const showDetailDialog = ref(false)
const editingItem = ref(null)
const selectedItem = ref(null)

// 表单
const form = ref({
  title: '',
  category: 'rule',
  description: '',
  tags: []
})

// 模拟知识库数据
const knowledgeList = ref([
  {
    id: 'kb-001',
    title: '围标行为认定规则',
    description: '用于识别投标人之间是否存在围标行为的判定规则，包括价格雷同、文件特征相似度等指标。',
    category: 'rule',
    chunks: 156,
    updatedAt: '2024-03-15',
    tags: ['围标', '串标', '价格异常'],
    chunksPreview: [
      '价格雷同判定：投标报价差异小于等于 5% 视为高度可疑',
      '文件特征相似度：技术方案文档相似度超过 80% 触发预警',
      '投标时间异常：多家投标人提交文件时间间隔小于 10 分钟'
    ]
  },
  {
    id: 'kb-002',
    title: '医疗设备采购合规标准',
    description: '医疗设备采购过程中需要遵守的法律法规、合规要求和标准流程。',
    category: 'policy',
    chunks: 89,
    updatedAt: '2024-02-20',
    tags: ['医疗器械', '采购合规', '法规'],
    chunksPreview: [
      '《医疗器械监督管理条例》相关要求',
      '采购流程必须经过招标、投标、评标、定标四个阶段',
      '进口设备需要提供医疗器械注册证'
    ]
  },
  {
    id: 'kb-003',
    title: '合同风险条款库',
    description: '常见合同风险条款的识别模板和应对建议，包括付款周期、违约责任、知识产权等。',
    category: 'rag',
    chunks: 234,
    updatedAt: '2024-03-01',
    tags: ['合同风险', '条款审查', '法律'],
    chunksPreview: [
      '付款周期异常：预付款超过 30% 或付款周期超过 90 天需重点关注',
      '违约责任不对等：乙方违约责任明显重于甲方时触发预警',
      '知识产权归属：未明确约定技术成果归属权的条款'
    ]
  },
  {
    id: 'kb-004',
    title: '投标人资质审查规则',
    description: '投标人资质审查的标准流程和关键检查点，包括营业执照、资质证书、经营范围等。',
    category: 'rule',
    chunks: 67,
    updatedAt: '2024-01-28',
    tags: ['资质审查', '投标人', '合规'],
    chunksPreview: [
      '营业执照有效期检查：距离到期不足 6 个月需提醒续期',
      '资质证书匹配度：投标产品必须在资质证书经营范围内',
      '业绩要求：近三年同类项目业绩数量不得少于 3 个'
    ]
  },
  {
    id: 'kb-005',
    title: '药品集中采购政策',
    description: '国家药品集中采购相关政策文件、实施细则和操作指南。',
    category: 'policy',
    chunks: 312,
    updatedAt: '2024-02-10',
    tags: ['药品集采', '政策', '医保'],
    chunksPreview: [
      '带量采购政策：原则上不低于年度采购量的 60%',
      '价格联动：同品种药品价格不得高于全国最低价',
      '质量分层：通过一致性评价的药品优先采购'
    ]
  },
  {
    id: 'kb-006',
    title: '标书相似度检测模型',
    description: '基于语义分析的标书相似度检测模型，用于识别技术方案雷同情况。',
    category: 'rag',
    chunks: 45,
    updatedAt: '2024-03-05',
    tags: ['相似度', '语义分析', '机器学习'],
    chunksPreview: [
      '文本向量化：使用 BERT 模型将标书文本转换为向量',
      '相似度计算：余弦相似度超过 0.85 视为高度相似',
      '段落匹配：连续 50 字以上相同视为重复段落'
    ]
  }
])

const availableTags = ref([
  '围标', '串标', '价格异常', '医疗器械', '采购合规', '法规',
  '合同风险', '条款审查', '法律', '资质审查', '投标人', '合规',
  '药品集采', '政策', '医保', '相似度', '语义分析', '机器学习'
])

// 计算属性
const stats = computed(() => ({
  totalKnowledge: knowledgeList.value.length,
  totalChunks: knowledgeList.value.reduce((sum, item) => sum + item.chunks, 0),
  ruleCount: knowledgeList.value.filter(item => item.category === 'rule').length,
  policyCount: knowledgeList.value.filter(item => item.category === 'policy').length
}))

const displayedKnowledge = computed(() => {
  let result = [...knowledgeList.value]

  // 分类筛选
  if (activeTab.value !== 'all') {
    result = result.filter(item => item.category === activeTab.value)
  }

  // 搜索筛选
  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = result.filter(item =>
      item.title.toLowerCase().includes(search) ||
      item.description.toLowerCase().includes(search) ||
      item.tags.some(tag => tag.toLowerCase().includes(search))
    )
  }

  // 排序
  result.sort((a, b) => {
    if (sortBy.value === 'title') {
      return a.title.localeCompare(b.title, 'zh-CN')
    } else if (sortBy.value === 'chunks') {
      return b.chunks - a.chunks
    } else {
      return new Date(b.updatedAt) - new Date(a.updatedAt)
    }
  })

  return result
})

// 方法
const getCountByCategory = (category) => {
  return knowledgeList.value.filter(item => item.category === category).length
}

const getTagType = (category) => {
  const types = {
    rule: 'danger',
    rag: 'success',
    policy: 'warning'
  }
  return types[category] || ''
}

const getCategoryLabel = (category) => {
  const labels = {
    rule: '规则引擎',
    rag: 'RAG向量库',
    policy: '合规政策'
  }
  return labels[category] || category
}

const handleTabChange = () => {
  searchText.value = ''
}

const handleSearch = () => {
  // 搜索是响应式的，这里不需要额外处理
}

const viewDetail = (item) => {
  selectedItem.value = item
  showDetailDialog.value = true
}

const editKnowledge = (item) => {
  editingItem.value = item
  form.value = {
    title: item.title,
    category: item.category,
    description: item.description,
    tags: [...item.tags]
  }
  showAddDialog.value = true
}

const deleteKnowledge = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识「${item.title}」吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const index = knowledgeList.value.findIndex(k => k.id === item.id)
    if (index !== -1) {
      knowledgeList.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  } catch {
    // 用户取消
  }
}

const saveKnowledge = () => {
  if (!form.value.title || !form.value.description) {
    ElMessage.warning('请填写完整信息')
    return
  }

  if (editingItem.value) {
    // 编辑
    const index = knowledgeList.value.findIndex(k => k.id === editingItem.value.id)
    if (index !== -1) {
      knowledgeList.value[index] = {
        ...knowledgeList.value[index],
        ...form.value,
        updatedAt: new Date().toISOString().split('T')[0]
      }
      ElMessage.success('保存成功')
    }
  } else {
    // 新增
    const newItem = {
      id: `kb-${Date.now()}`,
      ...form.value,
      chunks: 0,
      updatedAt: new Date().toISOString().split('T')[0],
      tags: form.value.tags || [],
      chunksPreview: []
    }
    knowledgeList.value.unshift(newItem)
    ElMessage.success('创建成功')
  }

  showAddDialog.value = false
  editingItem.value = null
  form.value = {
    title: '',
    category: 'rule',
    description: '',
    tags: []
  }
}
</script>

<style scoped>
.knowledge-page {
  padding: 32px;
  max-width: 1440px;
  margin: 0 auto;
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

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.bg-indigo {
  background: #eef2ff;
  color: #4f46e5;
}

.stat-icon.bg-emerald {
  background: #ecfdf5;
  color: #059669;
}

.stat-icon.bg-amber {
  background: #fffbeb;
  color: #d97706;
}

.stat-icon.bg-rose {
  background: #fff1f2;
  color: #e11d48;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}

/* 知识分类Tab */
.knowledge-tabs {
  margin-bottom: 20px;
}

.knowledge-tabs :deep(.el-radio-button__inner) {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 搜索和筛选 */
.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.sort-select {
  width: 150px;
}

/* 知识列表 */
.knowledge-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.knowledge-card {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.knowledge-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.card-header {
  margin-bottom: 16px;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.card-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}

.card-desc {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-stats {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

/* 详情对话框 */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-date {
  font-size: 13px;
  color: #94a3b8;
}

.detail-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.detail-section p {
  margin: 0;
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chunks-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.chunk-index {
  width: 24px;
  height: 24px;
  background: #e2e8f0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  flex-shrink: 0;
}

.chunk-text {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .knowledge-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    flex-direction: column;
  }

  .search-input {
    max-width: none;
  }
}
</style>