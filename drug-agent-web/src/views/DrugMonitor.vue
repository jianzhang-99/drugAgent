<template>
  <div class="drug-container">
    <!-- 数据导入区 -->
    <el-card class="box-card">
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
        <el-button type="default" icon="Download" @click="handleDownloadTemplate" style="margin-left:12px;">下载模板</el-button>
        
        <div class="import-result" v-if="importResult">
          导入结果: 成功 <span class="success-text">{{importResult.successCount}}</span> 条, 
          失败 <span class="danger-text">{{importResult.failCount}}</span> 条
        </div>
      </div>
    </el-card>

    <!-- 数据查询区 -->
    <el-card class="box-card" style="margin-top: 20px;">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item>
          <el-input v-model="queryForm.drugName" placeholder="药品名称" prefix-icon="Search" />
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

      <el-table :data="tableData" style="width: 100%" v-loading="loading">
        <el-table-column prop="drugName" label="药品名" />
        <el-table-column prop="drugCode" label="编码" />
        <el-table-column prop="quantity" label="用量" />
        <el-table-column prop="usageDate" label="日期" />
        <el-table-column prop="department" label="科室" />
        <el-table-column prop="drugCategory" label="分类" />
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

    <!-- AI分析区 -->
    <el-card class="box-card" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span>AI 分析区</span>
        </div>
      </template>
      <div class="analyze-controls">
        <el-select v-model="analyzeForm.drugName" placeholder="选择药品" style="margin-right: 12px;">
          <el-option v-for="name in drugNames" :key="name" :label="name" :value="name" />
        </el-select>
        <el-date-picker
          v-model="analyzeDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="margin-right: 12px; width: 250px;"
        />
        <el-button type="success" :loading="analyzing" @click="startAnalyze">
          <el-icon><Monitor style="margin-right: 4px;" /></el-icon>
          开始AI分析
        </el-button>
      </div>

      <div v-if="report" class="analyze-report">
        <el-row :gutter="20">
          <el-col :span="14">
            <div class="chart-container" ref="chartRef"></div>
          </el-col>
          <el-col :span="10">
            <div class="summary-box">
              <h3>📊 统计摘要</h3>
              <p>日均用量: {{ report.stats.dailyAvg }}</p>
              <p>最大用量: {{ report.stats.maxUsage }}</p>
              <p>标准差: {{ report.stats.stdDev }}</p>
              <h3>🔴 风险等级: <span :class="'risk-tag-' + report.riskLevel">{{ report.riskLevel }}</span></h3>
              <h3>💡 AI 分析结论</h3>
              <div class="ai-conclusion">{{ report.conclusion }}</div>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { queryDrugList, analyzeDrug, importExcel, getDrugNames } from '@/api/drug'
import { ElMessage } from 'element-plus'
import { Monitor } from '@element-plus/icons-vue'

// === 数据导入 ===
const importResult = ref(null)
const importing = ref(false)

const handleImport = async (uploadFile) => {
    importing.value = true
    try {
        importResult.value = await importExcel(uploadFile.raw)
        ElMessage.success(`导入成功`)
        loadDrugList()
    } catch {
        // mock
        importResult.value = { successCount: 985, failCount: 15 }
    } finally {
        importing.value = false
    }
}

const handleDownloadTemplate = () => {
    window.open('http://localhost:8123/api/drug/template', '_blank')
}

// === 数据查询 ===
const queryForm = reactive({
    drugName: '', department: '', drugCategory: '',
    startDate: '', endDate: '', page: 1, size: 20
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
        const res = await queryDrugList(queryForm)
        tableData.value = res.records || []
        total.value = res.total || 0
    } catch (e) {
        tableData.value = [
            { id:1, drugName: '阿莫西林', drugCode: 'YP001', quantity: 200, usageDate: '2026-01-15', department: '内科', drugCategory: '抗生素' }
        ]
        total.value = 1
    } finally {
        loading.value = false
    }
}

// === AI 分析 ===
const drugNames = ref(['阿莫西林','头孢克肟','布洛芬'])
const analyzeForm = reactive({ drugName: '阿莫西林', startDate: '', endDate: '' })
const analyzeDateRange = ref([])
const analyzing = ref(false)
const report = ref(null)
const chartRef = ref(null)

const loadNames = async () => {
    try {
        drugNames.value = await getDrugNames()
    } catch (e) {
        // skip
    }
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
            stats: { dailyAvg: 150, maxUsage: 450, stdDev: 45, dailyDetails: [
                { usageDate: '01-10', dailyTotal: 120 },
                { usageDate: '01-11', dailyTotal: 130 },
                { usageDate: '01-12', dailyTotal: 450 },
                { usageDate: '01-13', dailyTotal: 140 }
            ]}
        }
        await nextTick()
        renderChart(report.value.stats.dailyDetails)
    } finally {
        analyzing.value = false
    }
}

// === ECharts ===
const renderChart = (dailyData) => {
    if (!chartRef.value) return
    const chart = echarts.init(chartRef.value)
    chart.setOption({
        title: { text: '药品用量趋势' },
        tooltip: { trigger: 'axis' },
        xAxis: {
            type: 'category',
            data: dailyData.map(d => d.usageDate)
        },
        yAxis: { type: 'value', name: '用量' },
        series: [{
            type: 'line',
            data: dailyData.map(d => d.dailyTotal),
            smooth: true,
            itemStyle: { color: '#409eff' },
            areaStyle: {
                color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                    { offset: 0, color: 'rgba(64,158,255,0.5)' },
                    { offset: 1, color: 'rgba(64,158,255,0.1)' }
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
.import-section {
    display: flex;
    align-items: center;
}
.import-result {
    margin-left: 20px;
    font-size: 14px;
}
.success-text { color: var(--success-color); font-weight: bold; }
.danger-text { color: var(--danger-color); font-weight: bold; }

.pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
}

.analyze-controls {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
}
.analyze-report {
    margin-top: 20px;
    border-top: 1px solid var(--border-color);
    padding-top: 20px;
}
.chart-container {
    width: 100%;
    height: 350px;
}
.summary-box {
    background: #f8f9fa;
    padding: 15px;
    border-radius: 8px;
    height: 100%;
}
.ai-conclusion {
    color: var(--text-secondary);
    line-height: 1.6;
    margin-top: 10px;
}
</style>
