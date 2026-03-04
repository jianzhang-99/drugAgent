<template>
  <div class="knowledge-container">
    <el-card class="upload-card">
      <template #header>
        <div class="card-header">
          <span>上传法规文件</span>
        </div>
      </template>
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleUpload"
        accept=".pdf,.doc,.docx,.xls,.xlsx"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或 <em>点击上传(PDF/Word)</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持格式: PDF / Word / Excel
          </div>
        </template>
      </el-upload>
    </el-card>

    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <span>已入库文件列表</span>
          <el-button type="primary" link @click="loadList">刷新</el-button>
        </div>
      </template>
      <el-table :data="tableData" style="width: 100%" v-loading="loading">
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="chunkCount" label="片段数">
          <template #default="{ row }">
            {{ row.chunkCount || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">
              {{ row.status === 'COMPLETED' ? '✅ 完成' : '⏳ 处理中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDelete(row.id)">🗑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="test-card">
      <template #header>
        <div class="card-header">
          <span>检索效果测试 (P2)</span>
        </div>
      </template>
      <div class="test-area">
        <el-input 
          v-model="testQuery" 
          placeholder="输入关键词测试检索" 
          class="test-input"
          @keyup.enter="handleTest"
        >
          <template #append>
            <el-button @click="handleTest">🔍测试</el-button>
          </template>
        </el-input>
      </div>
      <div class="test-results" v-if="testResults.length" v-loading="testing">
        <div v-for="(res, index) in testResults" :key="index" class="result-item">
          <div class="result-header">
            结果{{ index + 1 }} (相似度: {{ res.score.toFixed(2) }})
          </div>
          <div class="result-content">{{ res.content }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { uploadKnowledge, getKnowledgeList, deleteKnowledge, testSearch } from '@/api/knowledge'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const testQuery = ref('')
const testResults = ref([])
const testing = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeList()
    tableData.value = res || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleUpload = async (file) => {
  try {
    await uploadKnowledge(file.raw)
    ElMessage.success('上传成功，文件处理中')
    loadList()
  } catch (e) {
    console.error(e)
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该法规文件吗？', '提示', { type: 'warning' })
    await deleteKnowledge(id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleTest = async () => {
  if (!testQuery.value.trim()) return
  testing.value = true
  try {
    const res = await testSearch(testQuery.value)
    testResults.value = res || []
  } catch (e) {
    // 兼容 MOCK 返回
    testResults.value = [
      { score: 0.87, content: '《药品管理法》第五十三条 药品经营企业购进药品，必须建立并执行进货检查验收制度，验明药品合格证明和其他标识；不符合规定要求的，不得购进。' },
      { score: 0.72, content: '《药品管理法》第四十一条 药品生产企业必须经药监部门批准，取得《药品生产许可证》。' }
    ]
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadList().catch(() => {
      // 本地无后端时可填充测试数据
      tableData.value = [
          { id: 1, fileName: '药品管理法.pdf', chunkCount: 45, status: 'COMPLETED', createTime: '2026-03-01 10:00:00' },
          { id: 2, fileName: '处方管理办法.pdf', chunkCount: null, status: 'PROCESSING', createTime: '2026-03-02 12:00:00' }
      ]
  })
})
</script>

<style scoped>
.knowledge-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.card-header {
  font-weight: bold;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.test-input {
  max-width: 500px;
  margin-bottom: 20px;
}
.result-item {
  background: #f8f9fa;
  padding: 12px;
  margin-bottom: 10px;
  border-radius: 4px;
  border-left: 4px solid var(--primary-color);
}
.result-header {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.result-content {
  font-size: 14px;
  line-height: 1.6;
}
</style>
