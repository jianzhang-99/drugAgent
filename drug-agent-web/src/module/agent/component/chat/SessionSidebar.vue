<script setup>
import { ref } from 'vue'
import SessionList from './SessionList.vue'
import { ElButton, ElDivider } from 'element-plus'
import { Plus, Setting, Question } from '@element-plus/icons-vue'

const emit = defineEmits(['create-session'])

function handleNewChat() {
  emit('create-session')
}
</script>

<template>
  <div class="session-sidebar">
    <!-- 顶部操作 -->
    <div class="sidebar-header">
      <el-button type="primary" class="new-chat-btn" @click="handleNewChat">
        <el-icon><Plus /></el-icon>
        新建会话
      </el-button>
    </div>

    <!-- 会话列表 -->
    <div class="session-list-container">
      <SessionList />
    </div>

    <!-- 底部导航 -->
    <div class="sidebar-footer">
      <el-divider style="margin: 8px 0" />
      <div class="footer-nav">
        <el-button link>
          <el-icon><Setting /></el-icon>
          设置
        </el-button>
        <el-button link>
          <el-icon><Question /></el-icon>
          帮助
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.session-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 280px;
  background-color: var(--el-bg-color-page);
  border-right: 1px solid var(--el-border-color-light);
}

.sidebar-header {
  padding: 12px;
}

.new-chat-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.session-list-container {
  flex: 1;
  overflow: hidden;
}

.sidebar-footer {
  padding: 8px 12px;
}

.footer-nav {
  display: flex;
  justify-content: space-around;
}
</style>
