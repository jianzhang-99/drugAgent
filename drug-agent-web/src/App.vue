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
html,
body,
#app {
  min-height: 100%;
}

.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f7fb;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
</style>
