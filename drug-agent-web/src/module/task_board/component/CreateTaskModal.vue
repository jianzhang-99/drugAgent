<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay fixed inset-0 z-50 flex items-center justify-center">
        <!-- 遮罩 -->
        <div
          class="absolute inset-0 bg-black/40 backdrop-blur-sm"
          @click="handleClose"
        />

        <!-- 弹窗内容 -->
        <div class="modal-content relative bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
          <!-- Header -->
          <div class="flex items-center justify-between p-6 border-b border-slate-200">
            <div>
              <h2 class="text-lg font-semibold text-slate-800">创建新任务</h2>
              <p class="text-sm text-slate-500 mt-1">填写任务基本信息</p>
            </div>
            <button
              class="p-2 hover:bg-slate-100 rounded-lg transition-colors"
              @click="handleClose"
            >
              <X class="w-5 h-5 text-slate-500" />
            </button>
          </div>

          <!-- Form -->
          <div class="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
            <!-- 任务名称 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                任务名称 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="form.taskName"
                type="text"
                placeholder="请输入任务名称"
                class="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
              <p v-if="errors.taskName" class="mt-1 text-xs text-red-500">{{ errors.taskName }}</p>
            </div>

            <!-- 任务类型 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                任务类型 <span class="text-red-500">*</span>
              </label>
              <select
                v-model="form.taskType"
                class="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              >
                <option value="">请选择任务类型</option>
                <option value="TENDER_REVIEW">标书审查</option>
                <option value="CONTRACT_CHECK">合同检查</option>
                <option value="COMPLIANCE_ALERT">合规预警</option>
              </select>
              <p v-if="errors.taskType" class="mt-1 text-xs text-red-500">{{ errors.taskType }}</p>
            </div>

            <!-- 场景 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                场景
              </label>
              <select
                v-model="form.scene"
                class="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              >
                <option value="">请选择场景（可选）</option>
                <option value="TENDER">标书场景</option>
                <option value="CONTRACT">合同场景</option>
                <option value="COMPLIANCE">合规场景</option>
              </select>
            </div>

            <!-- 提交人 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                提交人
              </label>
              <input
                v-model="form.submittedBy"
                type="text"
                placeholder="请输入提交人姓名"
                class="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
            </div>

            <!-- 优先级 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                优先级
              </label>
              <div class="flex items-center gap-2">
                <button
                  v-for="level in priorityLevels"
                  :key="level.value"
                  :class="[
                    'flex-1 py-2 px-3 text-sm font-medium rounded-lg border transition-all',
                    form.priority === level.value
                      ? level.activeClass
                      : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300'
                  ]"
                  @click="form.priority = level.value"
                >
                  {{ level.label }}
                </button>
              </div>
            </div>

            <!-- 截止时间 -->
            <div>
              <label class="block text-sm font-medium text-slate-700 mb-1.5">
                截止时间
              </label>
              <input
                v-model="form.deadline"
                type="datetime-local"
                class="w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-3 p-6 border-t border-slate-200 bg-slate-50">
            <button
              class="px-5 py-2.5 text-sm font-medium text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 transition-colors"
              @click="handleClose"
            >
              取消
            </button>
            <button
              :disabled="isSubmitting"
              class="px-5 py-2.5 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
              @click="handleSubmit"
            >
              <Loader v-if="isSubmitting" class="w-4 h-4 animate-spin" />
              {{ isSubmitting ? '创建中...' : '创建任务' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { X, Loader } from 'lucide-vue-next'
import { useTaskboardStore } from '@/module/task_board/store/taskboard'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'created'])

const store = useTaskboardStore()

// ==================== 表单数据 ====================

const form = reactive({
  taskName: '',
  taskType: '',
  scene: '',
  submittedBy: '',
  priority: 5,
  deadline: ''
})

const errors = reactive({
  taskName: '',
  taskType: ''
})

const isSubmitting = ref(false)

// ==================== 优先级选项 ====================

const priorityLevels = [
  { value: 1, label: '紧急', activeClass: 'bg-red-100 border-red-200 text-red-700' },
  { value: 3, label: '高', activeClass: 'bg-amber-100 border-amber-200 text-amber-700' },
  { value: 5, label: '普通', activeClass: 'bg-blue-100 border-blue-200 text-blue-700' },
  { value: 7, label: '低', activeClass: 'bg-slate-100 border-slate-200 text-slate-600' }
]

// ==================== 方法 ====================

const validate = () => {
  let valid = true
  errors.taskName = ''
  errors.taskType = ''

  if (!form.taskName.trim()) {
    errors.taskName = '请输入任务名称'
    valid = false
  }

  if (!form.taskType) {
    errors.taskType = '请选择任务类型'
    valid = false
  }

  return valid
}

const handleSubmit = async () => {
  if (!validate()) return

  isSubmitting.value = true

  try {
    const taskData = {
      taskName: form.taskName.trim(),
      taskType: form.taskType,
      scene: form.scene || undefined,
      submittedBy: form.submittedBy.trim() || 'anonymous',
      priority: form.priority,
      deadline: form.deadline ? new Date(form.deadline).toISOString() : undefined
    }

    const created = await store.createTask(taskData)
    emit('created', created)

    // 重置表单
    resetForm()
  } catch (e) {
    console.error('Failed to create task:', e)
    alert('创建任务失败: ' + (e.message || '未知错误'))
  } finally {
    isSubmitting.value = false
  }
}

const handleClose = () => {
  if (!isSubmitting.value) {
    resetForm()
    emit('close')
  }
}

const resetForm = () => {
  form.taskName = ''
  form.taskType = ''
  form.scene = ''
  form.submittedBy = ''
  form.priority = 5
  form.deadline = ''
  errors.taskName = ''
  errors.taskType = ''
}

// 监听visible变化，重置表单
watch(() => props.visible, (newVal) => {
  if (!newVal) {
    resetForm()
  }
})
</script>

<style scoped>
.modal-overlay {
  animation: fadeIn 0.2s ease;
}

.modal-content {
  animation: slideUp 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
