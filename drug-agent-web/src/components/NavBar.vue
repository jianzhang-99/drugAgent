<template>
  <header class="gov-navbar">
    <div class="navbar-left">
      <div class="logo-mark">HD</div>
      <div class="title-group">
        <h1>横渡智能监管系统</h1>
      </div>
    </div>

    <el-menu
      :default-active="activeIndex"
      class="top-navbar"
      mode="horizontal"
      router
      background-color="transparent"
      text-color="#dbe6ff"
      active-text-color="#ffffff"
    >
      <el-menu-item index="/agent">
        <el-icon><Connection /></el-icon>
        <span>Drug Agent</span>
      </el-menu-item>
    </el-menu>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Connection } from '@element-plus/icons-vue'

const route = useRoute()
const activeIndex = computed(() => {
  if (route.path.startsWith('/agent')) return '/agent'
  return route.path
})
</script>

<style scoped>
.gov-navbar {
  height: 72px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #113870, #1a4f99 55%, #2c69bf);
  color: #fff;
  box-shadow: 0 6px 20px rgba(17, 56, 112, 0.3);
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.title-group h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.title-group p {
  margin: 2px 0 0;
  font-size: 12px;
  color: rgba(236, 242, 255, 0.9);
}

.top-navbar {
  border-bottom: none;
  min-width: 180px;
  justify-content: flex-end;
}

.top-navbar :deep(.el-menu-item) {
  border-bottom: none !important;
  margin-left: 4px;
  border-radius: 8px;
  height: 42px;
}

.top-navbar :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.18) !important;
  font-weight: 600;
}
</style>
