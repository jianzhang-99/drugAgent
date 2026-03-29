<template>
  <t-dialog
    v-model:visible="visible"
    header="偏好与系统配置"
    width="500px"
    :footer="false"
  >
    <div class="settings-body">
      <div class="settings-section">
        <h4 class="section-title">智能体调度模型</h4>
        <p class="section-desc">选择在核心审查链条中负责决策推理的大语言模型引擎。</p>
        
        <div class="model-cards">
          <div 
            v-for="model in store.availableModels" 
            :key="model.model"
            class="model-card"
            :class="{ active: store.currentModel === model.model }"
            @click="store.setCurrentModel(model.model)"
          >
            <div class="model-info">
              <span class="model-name">{{ model.name }}</span>
              <span class="model-badge" v-if="model.isDefault">默认建议</span>
            </div>
            <div class="radio-cir">
              <div class="radio-dot" v-show="store.currentModel === model.model"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="settings-section">
        <h4 class="section-title">底层调度参数</h4>
        <p class="section-desc">当前系统已开启自动兜底重试机制与降级策略，为保证结果稳定性，不建议直接干预或调节底层大模型推理参数 (如 Temperature, Top-p 等)。</p>
      </div>
    </div>
  </t-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAgentStore } from '../store/agentStore';

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', val: boolean): void }>();

const store = useAgentStore();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
});
</script>

<style scoped>
.settings-body {
  padding: 12px 0;
}

.settings-section {
  margin-bottom: 32px;
}

.settings-section:last-child {
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a293b;
  margin: 0 0 6px;
}

.section-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
  margin: 0 0 16px;
}

.model-cards {
  display: grid;
  gap: 12px;
}

.model-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s;
}

.model-card:hover {
  background: #f1f5f9;
}

.model-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.12);
}

.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.model-name {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}

.model-badge {
  font-size: 12px;
  padding: 2px 8px;
  background: #dcfce7;
  color: #166534;
  border-radius: 999px;
  font-weight: 500;
}

.radio-cir {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.model-card.active .radio-cir {
  border-color: #3b82f6;
}

.radio-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #3b82f6;
}
</style>
