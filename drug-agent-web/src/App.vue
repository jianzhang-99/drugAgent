<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
:root {
  color-scheme: light;
  font-family: "PingFang SC", "Microsoft YaHei", "Helvetica Neue", sans-serif;
  background: #f5f8fc;
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
  background: #f5f8fc;
}

button,
textarea,
input {
  font: inherit;
}

/* 简约定制化滚动条 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background-color: rgba(148, 163, 184, 0.3);
  border-radius: 10px;
}

::-webkit-scrollbar-thumb:hover {
  background-color: rgba(148, 163, 184, 0.6);
}
</style>
