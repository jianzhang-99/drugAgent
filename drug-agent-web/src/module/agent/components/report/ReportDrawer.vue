<template>
  <teleport to="body">
    <transition name="modal-fade">
      <div v-if="visible" class="modal-mask" @click.self="handleClose">
        <div class="modal-container">
          <div class="modal-header">
            <div class="header-left">
              <div class="header-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                </svg>
              </div>
              <div class="header-titles">
                <h3>标书审查决策报告</h3>
                <span class="trace-badge">TRACE: {{ store.currentResult?.traceId || '—' }}</span>
              </div>
            </div>

            <div class="header-actions">
              <button class="export-btn" @click="handleExportPdf">导出 PDF</button>
              <button class="close-btn" @click="handleClose" aria-label="关闭">×</button>
            </div>
          </div>

          <div class="modal-body doc-wrapper">
            <FormalReportDocument ref="reportDocRef" :data="reportData || undefined" />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useAgentStore } from '../../store/agentStore';
import type { DrugAgentResp } from '../../types/agent';
import type { ReportData } from '../../types/report.types';
import FormalReportDocument from './FormalReportDocument.vue';
import { exportReportToPdf } from '../../utils/pdfExporter';
import { normalizeReportData } from './reportDataAdapter';

const store = useAgentStore();

/** FormalReportDocument 组件实例引用，用于获取内容区域 DOM */
const reportDocRef = ref<InstanceType<typeof FormalReportDocument> | null>(null);

const visible = computed({
  get: () => !!store.currentResult,
  set: (value) => {
    if (!value) store.setCurrentResult(null);
  },
});

const reportData = computed<ReportData | null>(() => {
  const result = store.currentResult as DrugAgentResp | null;
  return normalizeReportData(result);
});

function handleClose() {
  store.setCurrentResult(null);
}

async function handleExportPdf() {
  const result = store.currentResult as DrugAgentResp | null;
  if (!result) return;

  // 直接取已渲染的报告 DOM，不再手写 HTML 模板
  const contentEl = reportDocRef.value?.docMainRef;
  if (!contentEl) return;

  await exportReportToPdf(contentEl, `标书审查报告_${result.traceId || Date.now()}`);
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(8px);
}

.modal-container {
  width: min(1180px, 100%);
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  box-shadow: 0 30px 100px rgba(15, 23, 42, 0.28);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 22px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #e2e8f0;
}

.header-left,
.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  color: #1d4ed8;
}

.header-titles h3 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #0f172a;
}

.trace-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-family: 'SF Mono', 'Fira Code', monospace;
}

.tab-group {
  max-width: 100%;
}

.export-btn,
.close-btn {
  border: none;
  cursor: pointer;
}

.export-btn {
  padding: 10px 14px;
  border-radius: 12px;
  background: #0f172a;
  color: #ffffff;
  font-weight: 700;
}

.close-btn {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: #f1f5f9;
  color: #475569;
  font-size: 24px;
  line-height: 1;
}

.modal-body {
  overflow: hidden;
  padding: 0;
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
}

.doc-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-active .modal-container,
.modal-fade-leave-active .modal-container {
  transition: transform 0.24s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-fade-enter-from .modal-container,
.modal-fade-leave-to .modal-container {
  transform: translateY(12px) scale(0.98);
}

@media (max-width: 960px) {
  .modal-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .modal-mask {
    padding: 0;
  }

  .modal-container {
    max-height: 100vh;
    border-radius: 0;
  }

  .modal-body {
    padding: 16px;
  }
}
</style>
