<template>
  <div class="task-board-page">
    <div class="page-header">
      <h2>全局任务看板</h2>
      <p>统一管理您提交的所有异步审查任务、后台分析进度及历史记录。</p>
    </div>

    <div class="board-content">
      <div class="table-toolbar">
        <t-input
          v-model="searchKeyword"
          placeholder="搜索任务名称或编号..."
          style="width: 280px"
          clearable
        >
          <template #prefix-icon>
            <t-icon name="search" />
          </template>
        </t-input>
        <t-button theme="primary" variant="outline">
          <template #icon><t-icon name="refresh" /></template>
          刷新状态
        </t-button>
      </div>

      <t-table
        :data="filteredTasks"
        :columns="columns"
        row-key="id"
        class="task-table"
        hover
        :pagination="pagination"
      >
        <template #status="{ row }">
          <t-tag v-if="row.status === 'success'" theme="success" variant="light-outline">
            <template #icon><t-icon name="check-circle" /></template>
            已完成
          </t-tag>
          <t-tag v-else-if="row.status === 'running'" theme="primary" variant="light-outline">
            <template #icon><t-loading size="14px" style="margin-right: 4px;" /></template>
            执行中 ({{ row.progress }}%)
          </t-tag>
          <t-tag v-else-if="row.status === 'failed'" theme="danger" variant="light-outline">
            <template #icon><t-icon name="close-circle" /></template>
            异常中断
          </t-tag>
          <t-tag v-else theme="default" variant="light-outline">
            等待中
          </t-tag>
        </template>

        <template #action="{ row }">
          <t-button
            variant="text"
            theme="primary"
            :disabled="row.status !== 'success'"
          >
            查看报告
          </t-button>
          <t-button
            variant="text"
            theme="danger"
            v-if="row.status === 'failed' || row.status === 'running'"
          >
            {{ row.status === 'running' ? '终止' : '重试' }}
          </t-button>
        </template>
      </t-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const searchKeyword = ref('');

const columns = [
  { colKey: 'id', title: '任务编号', width: 120 },
  { colKey: 'name', title: '任务名称', ellipsis: true },
  { colKey: 'type', title: '分析类型', width: 120 },
  { colKey: 'submitter', title: '提交人', width: 120 },
  { colKey: 'createdAt', title: '提交时间', width: 180 },
  { colKey: 'status', title: '执行状态', width: 160 },
  { colKey: 'action', title: '操作', width: 140, fixed: 'right' },
];

const mockData = ref([
  {
    id: 'T-20231015-01',
    name: '骨科耗材集采第32批次标书合规性交叉对比审查',
    type: '标书审查',
    submitter: '张三 (法务部)',
    createdAt: '2023-10-15 09:30:12',
    status: 'success',
    progress: 100,
  },
  {
    id: 'T-20231015-02',
    name: '年度医疗器械采购框架协议v3.docx 预审',
    type: '合同预审',
    submitter: '李四 (合规组)',
    createdAt: '2023-10-15 10:15:44',
    status: 'running',
    progress: 65,
  },
  {
    id: 'T-20231015-03',
    name: 'Q3高频异常波动指标扫描',
    type: '合规预警',
    submitter: '系统自动调度',
    createdAt: '2023-10-15 11:00:00',
    status: 'failed',
    progress: 14,
  },
  {
    id: 'T-20231014-08',
    name: '血液净化设备标书(4家连标)对比',
    type: '标书审查',
    submitter: '王五 (审计部)',
    createdAt: '2023-10-14 16:45:22',
    status: 'success',
    progress: 100,
  },
]);

const pagination = {
  defaultCurrent: 1,
  defaultPageSize: 10,
  total: mockData.value.length,
};

const filteredTasks = computed(() => {
  if (!searchKeyword.value) return mockData.value;
  const lowerKeyword = searchKeyword.value.toLowerCase();
  return mockData.value.filter(
    (item) =>
      item.name.toLowerCase().includes(lowerKeyword) ||
      item.id.toLowerCase().includes(lowerKeyword)
  );
});
</script>

<style scoped>
.task-board-page {
  padding: 32px 42px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px;
}

.page-header p {
  color: #64748b;
  font-size: 14px;
  margin: 0;
}

.board-content {
  flex: 1;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.task-table {
  flex: 1;
}

:deep(.t-table th) {
  background: #f8fafc;
  font-weight: 600;
  color: #475569;
}

:deep(.t-tag) {
  border-radius: 6px;
}
</style>
