<template>
  <article class="suggest-card">
    <div class="suggest-icon" :class="theme">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
  </article>
</template>

<script setup>
defineProps({
  title: String,
  description: String,
  icon: [Object, String],
  theme: String
})
</script>

<style scoped>
.suggest-card {
  min-height: 210px;
  border-radius: var(--radius-lg);
  padding: 24px;
  background: var(--card-glass);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  transition: var(--transition-smooth);
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.suggest-card:hover {
  transform: translateY(-8px) scale(1.02);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-light);
}

.suggest-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  font-size: 24px;
  margin-bottom: 20px;
  transition: var(--transition-smooth);
}

.suggest-card:hover .suggest-icon {
  transform: rotate(-5deg) scale(1.1);
}

.theme-indigo {
  color: #4b75ff;
  background: #f0f3ff;
}

.theme-green {
  color: #10b981;
  background: #f0fdf4;
}

.theme-amber {
  color: #f59e0b;
  background: #fffbeb;
}

h3 {
  margin: 0;
  font-size: 19px;
  color: var(--text-main);
  font-weight: 700;
}

p {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-sub);
}
</style>
