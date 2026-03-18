<template>
  <button
    class="sidebar-item"
    :class="{ active, collapsed }"
    type="button"
    @click="$emit('click')"
  >
    <div class="icon-box">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <span v-if="!collapsed" class="label-text">{{ label }}</span>
  </button>
</template>

<script setup>
defineProps({
  icon: [Object, String],
  label: String,
  active: {
    type: Boolean,
    default: false
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

defineEmits(['click'])
</script>

<style scoped>
.sidebar-item {
  height: 56px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  color: var(--text-sub);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.sidebar-item.collapsed {
  padding: 0;
  justify-content: center;
  gap: 0;
}

.sidebar-item:hover {
  background: var(--primary-bg);
  color: var(--primary-color);
  transform: translateX(4px);
}

.sidebar-item.collapsed:hover {
  transform: none;
}

.sidebar-item.active {
  background: var(--primary-color);
  color: #fff;
  box-shadow: var(--shadow-md);
}

.icon-box {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
}

.sidebar-item .el-icon {
  font-size: 20px;
}

.sidebar-item.active .el-icon {
  transform: scale(1.1);
}

.label-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
