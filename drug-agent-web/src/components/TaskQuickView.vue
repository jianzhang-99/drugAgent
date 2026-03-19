<template>
  <div class="task-quick-view">
    <el-popover
      placement="bottom-end"
      :width="360"
      trigger="click"
      popper-class="task-popover"
    >
      <template #reference>
        <div class="task-trigger">
          <el-badge :value="taskCount" :hidden="taskCount === 0" :max="99" class="task-badge">
            <el-button class="task-btn" :icon="List" circle />
          </el-badge>
          <span class="task-label">任务</span>
        </div>
      </template>

      <div class="task-dropdown">
        <div class="dropdown-header">
          <span class="header-title">快速任务</span>
          <el-button type="primary" link @click="goToTaskBoard">
            <el-icon><ArrowRight /></el-icon>
            看板
          </el-button>
        </div>

        <div v-if="recentTasks.length === 0" class="empty-state">
          <el-icon class="empty-icon"><Document /></el-icon>
          <p>暂无任务记录</p>
        </div>

        <div v-else class="task-list">
          <div
            v-for="task in recentTasks"
            :key="task.caseId"
            class="task-item"
            @click="goToTaskDetail(task.caseId)"
          >
            <div class="task-item-left">
              <el-tag
                :type="getStatusType(task.status)"
                size="small"
                class="status-tag"
              >
                {{ getStatusLabel(task.status) }}
              </el-tag>
              <span class="task-name">{{ getTaskTitle(task) }}</span>
            </div>
            <div class="task-item-right">
              <span class="task-time">{{ formatTime(task.createdAt) }}</span>
              <el-icon class="arrow-icon"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { List, ArrowRight, Document } from '@element-plus/icons-vue'
import { getTenderTasks, getRecentTenderTask } from '../utils/local-state'

const router = useRouter()

const recentTasks = ref([])

const taskCount = computed(() => recentTasks.value.length)

const loadTasks = () => {
  const tasks = getTenderTasks()
  const recent = getRecentTenderTask()

  let allTasks = [...tasks]
  if (recent && !allTasks.some(t => t.caseId === recent.caseId)) {
    allTasks.unshift(recent)
  }

  // 按创建时间倒序排列，取最近5个
  recentTasks.value = allTasks
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, 5)
}

const getTaskTitle = (task) => {
  const fileCount = task.documentIds?.length || 0
  return `标书审查任务（${fileCount} 份文件）`
}

const getStatusType = (status) => {
  const statusMap = {
    'PENDING': 'info',
    'PROCESSING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return statusMap[status] || 'info'
}

const getStatusLabel = (status) => {
  const labelMap = {
    'PENDING': '待处理',
    'PROCESSING': '处理中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return labelMap[status] || status
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN')
}

const goToTaskBoard = () => {
  router.push('/agent/tasks')
}

const goToTaskDetail = (caseId) => {
  router.push(`/agent/tasks/${caseId}`)
}

onMounted(() => {
  loadTasks()
  // 监听 localStorage 变化
  window.addEventListener('storage', loadTasks)
  // 监听自定义事件
  window.addEventListener('task-updated', loadTasks)
})
</script>

<style scoped>
.task-quick-view {
  display: flex;
  align-items: center;
}

.task-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.task-trigger:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.task-btn {
  width: 36px;
  height: 36px;
  border: none;
  background-color: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.task-btn:hover {
  background-color: rgba(255, 255, 255, 0.25);
}

.task-label {
  font-size: 14px;
  color: #fff;
}

.task-badge :deep(.el-badge__content) {
  background-color: #f56c6c;
}

:deep(.task-popover) {
  padding: 0;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.task-dropdown {
  margin: -12px;
}

.dropdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.empty-state {
  padding: 40px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 40px;
  color: #c0c4cc;
  margin-bottom: 8px;
}

.empty-state p {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.task-list {
  max-height: 320px;
  overflow-y: auto;
}

.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f5f7fa;
}

.task-item:last-child {
  border-bottom: none;
}

.task-item:hover {
  background-color: #f5f7fa;
}

.task-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.status-tag {
  flex-shrink: 0;
}

.task-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-item-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.task-time {
  font-size: 12px;
  color: #909399;
}

.arrow-icon {
  color: #c0c4cc;
  font-size: 14px;
}
</style>
