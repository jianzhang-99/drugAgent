<template>
  <el-menu
    :default-active="activeIndex"
    class="top-navbar"
    mode="horizontal"
    router
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409EFF"
  >
    <div class="logo-container">
      <span class="logo-text">Drug-Agent 药品智能监管系统</span>
    </div>
    
    <div class="nav-menus">
      <el-menu-item index="/drug">
        <el-icon><DataLine /></el-icon>
        <span>药品监管</span>
      </el-menu-item>
      
      <el-menu-item index="/compliance">
        <el-icon><ChatDotRound /></el-icon>
        <span>合规对话</span>
      </el-menu-item>
      
      <el-menu-item index="/knowledge">
        <el-icon><Reading /></el-icon>
        <span>知识库管理</span>
      </el-menu-item>
    </div>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataLine, ChatDotRound, Reading } from '@element-plus/icons-vue'

const route = useRoute()
const activeIndex = computed(() => {
  // 处理子路由高亮
  if (route.path.startsWith('/drug')) return '/drug'
  if (route.path.startsWith('/compliance')) return '/compliance'
  if (route.path.startsWith('/knowledge')) return '/knowledge'
  return route.path
})
</script>

<style scoped>
.top-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: none;
  padding: 0 20px;
}

.logo-container {
  display: flex;
  align-items: center;
  height: 100%;
}

.logo-text {
  color: white;
  font-size: 18px;
  font-weight: bold;
}

.nav-menus {
  display: flex;
}
</style>
