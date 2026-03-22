<template>
  <div class="min-h-screen bg-gray-50 p-6">
    <div class="max-w-5xl mx-auto">
      <div class="mb-8">
        <h1 class="text-3xl font-bold text-gray-900">系统配置</h1>
        <p class="text-gray-500 mt-2">自定义 Agent 行为与系统设置</p>
      </div>

      <div class="flex gap-6">
        <!-- Left Tab Navigation -->
        <div class="w-64 flex-shrink-0">
          <nav class="bg-white rounded-xl shadow-sm p-2">
            <button
              v-for="tab in tabs" :key="tab.key" @click="activeTab = tab.key"
              :class="['w-full text-left px-4 py-3 rounded-lg transition-all duration-200 flex items-center gap-3',
                activeTab === tab.key ? 'bg-blue-50 text-blue-600 font-medium' : 'text-gray-600 hover:bg-gray-50']">
              <component :is="tab.icon" class="w-5 h-5" />
              {{ tab.label }}
            </button>
          </nav>
        </div>

        <!-- Right Configuration Content -->
        <div class="flex-1 bg-white rounded-xl shadow-sm p-6">
          <!-- PROFILE Tab -->
          <div v-if="activeTab === 'PROFILE'" class="space-y-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-6">基础资料</h2>
            <div class="flex items-center gap-6">
              <div class="w-20 h-20 bg-gray-200 rounded-full flex items-center justify-center">
                <User class="w-10 h-10 text-gray-400" />
              </div>
              <div>
                <h3 class="font-medium text-gray-800">管理员</h3>
                <p class="text-sm text-gray-500">系统管理员 · 超级权限</p>
              </div>
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">用户名称</label>
                <input type="text" v-model="profile.name" class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">电子邮箱</label>
                <input type="email" v-model="profile.email" class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            </div>
            <div class="bg-amber-50 border border-amber-200 rounded-xl p-5 mt-6">
              <div class="flex items-start gap-4">
                <div class="w-10 h-10 bg-amber-100 rounded-lg flex items-center justify-center flex-shrink-0">
                  <Lock class="w-5 h-5 text-amber-600" />
                </div>
                <div>
                  <h4 class="font-medium text-amber-800">权限提示</h4>
                  <p class="text-sm text-amber-600 mt-1">当前为系统管理员账户，拥有最高权限。请谨慎修改核心配置，以免影响系统正常运行。</p>
                </div>
              </div>
            </div>
          </div>

          <!-- ENGINE Tab -->
          <div v-if="activeTab === 'ENGINE'" class="space-y-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-6">Agent 引擎偏好</h2>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-3">基座大模型</label>
              <div class="space-y-2">
                <button
                  v-for="model in models" :key="model.id" @click="settings.model = model.id"
                  :class="['w-full p-4 rounded-lg border-2 transition-all duration-200 text-left flex items-center justify-between',
                    settings.model === model.id ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300']">
                  <div>
                    <div class="font-medium text-gray-800">{{ model.name }}</div>
                    <div class="text-sm text-gray-500">{{ model.description }}</div>
                  </div>
                  <div v-if="settings.model === model.id" class="w-3 h-3 bg-blue-500 rounded-full"></div>
                </button>
              </div>
            </div>
            <div class="mt-8">
              <div class="flex items-center justify-between mb-3">
                <label class="block text-sm font-medium text-gray-700">推理严谨度</label>
                <div class="flex items-center gap-2">
                  <span class="text-sm text-gray-500">幻觉容忍</span>
                  <span class="px-2 py-1 bg-blue-100 text-blue-600 text-sm font-medium rounded">{{ settings.rigor }}%</span>
                </div>
              </div>
              <input type="range" v-model="settings.rigor" min="0" max="100" class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-blue-600" />
              <div class="flex justify-between text-xs text-gray-400 mt-1">
                <span>快速响应</span>
                <span>严格推理</span>
              </div>
            </div>
          </div>

          <!-- COMPLIANCE Tab -->
          <div v-if="activeTab === 'COMPLIANCE'" class="space-y-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-6">数据与合规策略</h2>
            <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
              <div>
                <div class="font-medium text-gray-800">自动清理源文件缓存</div>
                <div class="text-sm text-gray-500">任务完成后自动删除上传的原始文件</div>
              </div>
              <button @click="settings.autoClean = !settings.autoClean"
                :class="['relative w-14 h-7 rounded-full transition-colors duration-200', settings.autoClean ? 'bg-blue-500' : 'bg-gray-300']">
                <span :class="['absolute top-1 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200', settings.autoClean ? 'translate-x-8' : 'translate-x-1']"></span>
              </button>
            </div>
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-3">审计追踪日志留存期限</label>
              <select v-model="settings.logRetention" class="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white">
                <option value="30">30 天</option>
                <option value="90">90 天</option>
                <option value="180">180 天</option>
                <option value="forever">永久</option>
              </select>
              <p class="text-xs text-gray-400 mt-2">超过留存期限的日志将自动清除以节省存储空间</p>
            </div>
          </div>

          <!-- DISPLAY Tab -->
          <div v-if="activeTab === 'DISPLAY'" class="space-y-6">
            <h2 class="text-xl font-semibold text-gray-800 mb-6">通知与展示</h2>
            <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
              <div>
                <div class="font-medium text-gray-800">默认展开 Agent 思考图谱</div>
                <div class="text-sm text-gray-500">新任务默认显示 AI 推理过程可视化</div>
              </div>
              <button @click="settings.showThinkGraph = !settings.showThinkGraph"
                :class="['relative w-14 h-7 rounded-full transition-colors duration-200', settings.showThinkGraph ? 'bg-blue-500' : 'bg-gray-300']">
                <span :class="['absolute top-1 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200', settings.showThinkGraph ? 'translate-x-8' : 'translate-x-1']"></span>
              </button>
            </div>
            <div class="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
              <div>
                <div class="font-medium text-gray-800">高风险命中外部告警</div>
                <div class="text-sm text-gray-500">检测到高风险问题时发送外部通知</div>
              </div>
              <button @click="settings.highRiskAlert = !settings.highRiskAlert"
                :class="['relative w-14 h-7 rounded-full transition-colors duration-200', settings.highRiskAlert ? 'bg-blue-500' : 'bg-gray-300']">
                <span :class="['absolute top-1 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200', settings.highRiskAlert ? 'translate-x-8' : 'translate-x-1']"></span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Save Button -->
      <div class="mt-6 flex justify-end">
        <button @click="saveSettings"
          class="px-6 py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition-colors duration-200 flex items-center gap-2">
          <Save class="w-5 h-5" />
          保存配置
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { User, Settings2, Shield, Bell, Lock, Save } from 'lucide-vue-next'

const tabs = [
  { key: 'PROFILE', label: '基础资料', icon: User },
  { key: 'ENGINE', label: 'Agent 引擎偏好', icon: Settings2 },
  { key: 'COMPLIANCE', label: '数据与合规策略', icon: Shield },
  { key: 'DISPLAY', label: '通知与展示', icon: Bell }
]

const activeTab = ref('PROFILE')
const models = [
  { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash', description: '快速响应，适合日常审查任务' },
  { id: 'gemini-2.5-pro', name: 'Gemini 2.5 Pro', description: '深度推理，适合复杂合规分析' }
]
const profile = ref({ name: '管理员', email: 'admin@example.com' })
const settings = ref({
  model: 'gemini-2.5-flash', rigor: 75, autoClean: true, logRetention: '90',
  showThinkGraph: true, highRiskAlert: false
})

function saveSettings() {
  localStorage.setItem('drug-agent-settings', JSON.stringify({ profile: profile.value, settings: settings.value }))
  alert('配置已保存')
}
</script>

<style scoped>
input[type="range"]::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 18px; height: 18px; border-radius: 50%; background: #2563eb; cursor: pointer; }
input[type="range"]::-moz-range-thumb { width: 18px; height: 18px; border-radius: 50%; background: #2563eb; cursor: pointer; border: none; }
</style>
