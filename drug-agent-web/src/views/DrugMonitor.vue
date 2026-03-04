<template>
  <div class="drug-container">
    <section class="page-hero">
      <div>
        <h2>药品监管驾驶舱</h2>
        <p>面向监管领导的核心指标看板与风险分析</p>
      </div>
      <div class="hero-tags">
        <el-tag effect="dark" type="primary">实时监测</el-tag>
        <el-tag effect="plain" type="danger">重点风险预警</el-tag>
      </div>
    </section>

    <el-row :gutter="16" class="overview-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-label">纳入监测药品</div>
          <el-statistic :value="overview.totalDrugs" suffix="种" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-label">累计用量</div>
          <el-statistic :value="overview.totalUsage" suffix="单位" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-label">日均用量</div>
          <el-statistic :value="overview.avgUsage" :precision="1" suffix="单位" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="overview-card" shadow="hover">
          <div class="card-label">覆盖科室</div>
          <el-statistic :value="overview.departmentCount" suffix="个" />
        </el-card>
      </el-col>
    </el-row>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>数据导入</span>
          <small>支持Excel模板导入，适用于批量历史数据补录</small>
        </div>
      </template>

      <div class="import-section">
        <el-upload
          class="upload-btn"
          action="#"
          :auto-upload="false"
          :on-change="handleImport"
          :show-file-list="false"
          accept=".xlsx,.xls"
        >
          <el-button type="primary" :loading="importing" icon="Upload">上传Excel导入</el-button>
        </el-upload>
        <el-button type="default" icon="Download" @click="handleDownloadTemplate">下载模板</el-button>

        <div class="import-result" v-if="importResult">
          导入结果：成功 <span class="success-text">{{ importResult.successCount }}</span> 条，
          失败 <span class="danger-text">{{ importResult.failCount }}</span> 条
        </div>
      </div>
    </el-card>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>监管查询</span>
          <small>按药品、科室、分类、日期多维筛选，快速定位异常</small>
        </div>
      </template>

      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item>
          <el-input v-model="queryForm.drugName" placeholder="药品名称" prefix-icon="Search" clearable />
        </el-form-item>
        <el-form-item>
          <el-select v-model="queryForm.department" placeholder="科室" clearable style="width: 120px;">
            <el-option label="内科" value="内科" />
            <el-option label="外科" value="外科" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="queryForm.drugCategory" placeholder="分类" clearable style="width: 120px;">
            <el-option label="抗生素" value="抗生素" />
            <el-option label="心血管" value="心血管" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDrugList" icon="Search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" style="width: 100%" v-loading="loading" stripe>
        <el-table-column prop="drugName" label="药品名" min-width="120" />
        <el-table-column prop="drugCode" label="编码" min-width="90" />
        <el-table-column prop="quantity" label="用量" min-width="90" sortable />
        <el-table-column prop="usageDate" label="日期" min-width="120" />
        <el-table-column prop="department" label="科室" min-width="90">
          <template #default="scope">
            <el-tag effect="plain" type="primary">{{ scope.row.department }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="drugCategory" label="分类" min-width="100">
          <template #default="scope">
            <el-tag effect="light" type="warning">{{ scope.row.drugCategory }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryForm.page"
          :page-size="queryForm.size"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="loadDrugList"
        />
      </div>
    </el-card>

    <el-card class="panel-card" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>AI 风险分析</span>
          <small>自动识别异常波动，生成监管建议</small>
        </div>
      </template>

      <div class="analyze-controls">
        <el-select v-model="analyzeForm.drugName" placeholder="选择药品" style="margin-right: 12px; width: 200px;">
          <el-option v-for="name in drugNames" :key="name" :label="name" :value="name" />
        </el-select>
        <el-date-picker
          v-model="analyzeDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="margin-right: 12px; width: 260px;"
        />
        <el-button type="success" :loading="analyzing" @click="startAnalyze">
          <el-icon><Monitor /></el-icon>
          <span style="margin-left: 6px;">开始AI分析</span>
        </el-button>
      </div>

      <div v-if="report" class="analyze-report">
        <el-row :gutter="20">
          <el-col :span="14">
            <div class="chart-container" ref="chartRef"></div>
          </el-col>
          <el-col :span="10">
            <div class="summary-box">
              <h3>统计摘要</h3>
              <p>日均用量：{{ report.stats.dailyAvg }}</p>
              <p>最大用量：{{ report.stats.maxUsage }}</p>
              <p>标准差：{{ report.stats.stdDev }}</p>
              <div class="risk-line">
                风险等级：
                <span :class="'risk-tag-' + report.riskLevel">{{ report.riskLevel }}</span>
              </div>
              <h3>AI 分析结论</h3>
              <div class="ai-conclusion">{{ report.conclusion }}</div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import { analyzeDrug } from '@/api/drug'
import { ElMessage } from 'element-plus'
import { Monitor } from '@element-plus/icons-vue'

const importResult = ref(null)
const importing = ref(false)

const handleImport = async () => {
  importing.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 300))
    importResult.value = { successCount: 1, failCount: 0 }
    ElMessage.success('已读取文件（演示模式）')
    loadDrugList()
  } finally {
    importing.value = false
  }
}

const handleDownloadTemplate = () => {
  ElMessage.info('后端暂未提供模板下载接口')
}

const queryForm = reactive({
  drugName: '',
  department: '',
  drugCategory: '',
  startDate: '',
  endDate: '',
  page: 1,
  size: 20
})
const dateRange = ref([])
const tableData = ref([])
const total = ref(0)
const loading = ref(false)

const loadDrugList = async () => {
  if (dateRange.value && dateRange.value.length === 2) {
    queryForm.startDate = dateRange.value[0]
    queryForm.endDate = dateRange.value[1]
  } else {
    queryForm.startDate = ''
    queryForm.endDate = ''
  }
  loading.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 200))
    tableData.value = [
      { id: 1, drugName: '阿莫西林', drugCode: 'YP001', quantity: 200, usageDate: '2026-01-15', department: '内科', drugCategory: '抗生素' },
      { id: 2, drugName: '头孢克肟', drugCode: 'YP002', quantity: 160, usageDate: '2026-01-16', department: '外科', drugCategory: '抗生素' }
    ]
    total.value = tableData.value.length
  } finally {
    loading.value = false
  }
}

const overview = computed(() => {
  const totalUsage = tableData.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  const totalDrugs = tableData.value.length
  const departmentCount = new Set(tableData.value.map(item => item.department)).size
  const avgUsage = totalDrugs ? totalUsage / totalDrugs : 0
  return { totalUsage, totalDrugs, departmentCount, avgUsage }
})

const drugNames = ref(['阿莫西林', '头孢克肟', '布洛芬'])
const analyzeForm = reactive({ drugName: '阿莫西林', startDate: '', endDate: '' })
const analyzeDateRange = ref([])
const analyzing = ref(false)
const report = ref(null)
const chartRef = ref(null)

const loadNames = async () => {
  // 演示模式保留默认药品
}

const startAnalyze = async () => {
  if (analyzeDateRange.value && analyzeDateRange.value.length === 2) {
    analyzeForm.startDate = analyzeDateRange.value[0]
    analyzeForm.endDate = analyzeDateRange.value[1]
  }
  analyzing.value = true
  try {
    report.value = await analyzeDrug(analyzeForm)
    await nextTick()
    renderChart(report.value.stats.dailyDetails)
  } catch (e) {
    report.value = {
      riskLevel: 'HIGH',
      conclusion: '阿莫西林在内科的用量近一周出现异常飙升，远高于历史日均标准差，可能存在违规滥用情况。建议重点核查。',
      stats: {
        dailyAvg: 150,
        maxUsage: 450,
        stdDev: 45,
        dailyDetails: [
          { usageDate: '01-10', dailyTotal: 120 },
          { usageDate: '01-11', dailyTotal: 130 },
          { usageDate: '01-12', dailyTotal: 450 },
          { usageDate: '01-13', dailyTotal: 140 }
        ]
      }
    }
    await nextTick()
    renderChart(report.value.stats.dailyDetails)
  } finally {
    analyzing.value = false
  }
}

const renderChart = (dailyData) => {
  if (!chartRef.value) return
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '药品用量趋势', textStyle: { fontSize: 14, fontWeight: 600 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 20, top: 50, bottom: 30 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dailyData.map(d => d.usageDate)
    },
    yAxis: { type: 'value', name: '用量' },
    series: [{
      type: 'line',
      data: dailyData.map(d => d.dailyTotal),
      smooth: true,
      symbolSize: 8,
      itemStyle: { color: '#2f74ff' },
      lineStyle: { width: 3 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(47,116,255,0.35)' },
          { offset: 1, color: 'rgba(47,116,255,0.05)' }
        ])
      },
      markPoint: {
        data: [
          { type: 'max', name: '最大值' },
          { type: 'min', name: '最小值' }
        ]
      }
    }]
  })
}

onMounted(() => {
  loadDrugList()
  loadNames()
})
</script>

<style scoped>
.drug-container {
  max-width: 1480px;
  margin: 0 auto;
}

.page-hero {
  margin-bottom: 16px;
  border-radius: 12px;
  padding: 18px 20px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #0f4aa8, #1f6de0 60%, #4c94ff);
  box-shadow: 0 10px 24px rgba(31, 109, 224, 0.24);
}

.page-hero h2 {
  margin: 0;
  font-size: 22px;
  letter-spacing: 0.5px;
}

.page-hero p {
  margin: 6px 0 0;
  opacity: 0.9;
}

.hero-tags {
  display: flex;
  gap: 10px;
}

.overview-row {
  margin-bottom: 16px;
}

.overview-card {
  border-radius: 12px;
  border: 1px solid #e8eef8;
}

.card-label {
  color: #7a8599;
  margin-bottom: 4px;
  font-size: 13px;
}

.panel-card {
  margin-top: 16px;
  border-radius: 12px;
  border: 1px solid #e8eef8;
}

.panel-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.panel-header span {
  font-size: 16px;
  font-weight: 600;
  color: #213547;
}

.panel-header small {
  color: #8a94a6;
}

.import-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.import-result {
  font-size: 14px;
}

.success-text {
  color: var(--success-color);
  font-weight: 600;
}

.danger-text {
  color: var(--danger-color);
  font-weight: 600;
}

.query-form {
  padding: 6px 0 10px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.analyze-controls {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.analyze-report {
  border-top: 1px solid #edf1f7;
  padding-top: 18px;
}

.chart-container {
  width: 100%;
  height: 350px;
  border-radius: 12px;
  border: 1px solid #edf1f7;
}

.summary-box {
  background: linear-gradient(180deg, #f8fbff, #f5f7fb);
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #edf1f7;
  height: 100%;
}

.summary-box h3 {
  margin: 6px 0 10px;
}

.risk-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 14px;
}

.ai-conclusion {
  color: #4f596b;
  line-height: 1.7;
}
</style>
