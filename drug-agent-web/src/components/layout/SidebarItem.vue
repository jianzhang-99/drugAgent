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
  width: 100%;
  height: 48px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  margin-bottom: 4px;
}

.sidebar-item.collapsed {
  padding: 0;
  justify-content: center;
  gap: 0;
}

.sidebar-item:hover {
  background: #f8fafc;
  color: #1e293b;
}

.sidebar-item.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
}

.icon-box {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.sidebar-item .el-icon {
  font-size: 18px;
}

.label-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
