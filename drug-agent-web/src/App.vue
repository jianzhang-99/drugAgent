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
.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: radial-gradient(circle at 20% 0%, #f4f8ff, #edf3ff 35%, #f5f7fa 100%);
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 18px 22px 24px;
}
</style>
