<template>
  <article class="suggest-card" @click="$emit('click')">
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

defineEmits(['click'])
</script>

<style scoped>
.suggest-card {
  min-height: 180px;
  border-radius: 16px;
  padding: 24px;
  background: #fff;
  border: 1px solid #edf2f7;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  text-align: left;
}

.suggest-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.06);
  border-color: #3b82f633;
}

.suggest-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 22px;
  margin-bottom: 20px;
  transition: all 0.3s;
}

.suggest-card:hover .suggest-icon {
  transform: scale(1.1);
}

.theme-indigo {
  color: #4f46e5;
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
  font-size: 18px;
  color: #1a202c;
  font-weight: 700;
  margin-bottom: 12px;
}

p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #718096;
}
</style>
