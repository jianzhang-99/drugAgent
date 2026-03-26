<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
:root {
  color-scheme: light;
  font-family: "PingFang SC", "Microsoft YaHei", "Helvetica Neue", sans-serif;
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.16), transparent 28%),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.14), transparent 30%),
    linear-gradient(180deg, #f7fbfa 0%, #eef4f7 52%, #e8eef2 100%);
}

* {
  box-sizing: border-box;
}

html,
body,
#app {
  margin: 0;
  min-height: 100%;
}

body {
  color: #13313b;
}
</style>
