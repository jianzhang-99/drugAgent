<template>
  <div class="app-container">
    <NavBar />
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import NavBar from './components/NavBar.vue'
</script>

<style>
/* 全局样式已在 variables.css 中引入 */
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg-color);
}
</style>
