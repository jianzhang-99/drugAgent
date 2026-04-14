<template>
  <div class="comparison-table">
    <el-table :data="data" border stripe style="width: 100%">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :min-width="col.width || 100"
      >
        <template #default="{ row }">
          <slot :name="col.prop" :row="row" :value="row[col.prop]">
            {{ row[col.prop] }}
          </slot>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
interface Column {
  prop: string;
  label: string;
  width?: number;
}

interface Props {
  data: any[];
  columns: Column[];
}

defineProps<Props>();
</script>

<style scoped>
.comparison-table {
  width: 100%;
}

:deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f7f8fa !important;
  color: #4e5969;
  font-weight: 600;
  font-size: 13px;
}

:deep(.el-table td) {
  font-size: 13px;
  color: #1d2129;
}
</style>
