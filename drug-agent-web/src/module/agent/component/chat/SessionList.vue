<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useChatStore } from '@/stores/chatStore'
import { ElInput, ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem, ElEmpty, ElSpin } from 'element-plus'
import { Search, Plus, MoreFilled, ChatDotRound, Delete, Edit } from '@element-plus/icons-vue'
import { formatTime } from '@/utils/timeFormat'

const chatStore = useChatStore()
const searchQuery = ref('')
const editingId = ref(null)
const editingTitle = ref('')

const groupedSessions = computed(() => chatStore.groupedSessions)

onMounted(() => {
  chatStore.fetchSessions()
})

watch(searchQuery, (val) => {
  chatStore.searchSessions(val)
})

function handleSelectSession(session) {
  chatStore.setCurrentSession(session)
  chatStore.fetchSession(session.id)
}

function handleCreateSession() {
  chatStore.createSession()
}

function handleEditTitle(session) {
  editingId.value = session.id
  editingTitle.value = session.title
}

function handleSaveTitle(session) {
  if (editingTitle.value.trim()) {
    chatStore.updateSessionTitle(session.id, editingTitle.value.trim())
  }
  editingId.value = null
}

function handleDeleteSession(session) {
  chatStore.deleteSession(session.id)
}
</script>

<template>
  <div class="session-list">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索会话..."
        :prefix-icon="Search"
        clearable
        size="small"
      />
      <el-button :icon="Plus" circle @click="handleCreateSession" />
    </div>

    <!-- 会话列表 -->
    <div class="sessions-container">
      <el-spin v-if="chatStore.loading" />

      <template v-else>
        <!-- 有搜索结果 -->
        <template v-if="searchQuery && groupedSessions['今天']?.length + groupedSessions['昨天']?.length + groupedSessions['过去7天']?.length + groupedSessions['更早']?.length > 0">
          <div
            v-for="session in chatStore.filteredSessions"
            :key="session.id"
            class="session-item"
            :class="{ active: chatStore.currentSession?.id === session.id }"
            @click="handleSelectSession(session)"
          >
            <div class="session-info">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-meta">
                <span class="scene-tag">{{ session.scene }}</span>
                <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
              </div>
            </div>
            <el-dropdown trigger="click" @command="(cmd) => cmd === 'edit' ? handleEditTitle(session) : handleDeleteSession(session)">
              <el-button link @click.stop>
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">重命名</el-dropdown-item>
                  <el-dropdown-item command="delete">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>

        <!-- 无搜索结果 -->
        <el-empty v-else-if="searchQuery" description="未找到匹配的会话" />

        <!-- 分组列表 -->
        <template v-else>
          <div v-for="(items, group) in groupedSessions" :key="group">
            <div v-if="items.length > 0" class="session-group">
              <div class="group-title">{{ group }}</div>
              <div
                v-for="session in items"
                :key="session.id"
                class="session-item"
                :class="{ active: chatStore.currentSession?.id === session.id }"
                @click="handleSelectSession(session)"
              >
                <div class="session-info">
                  <!-- 编辑模式 -->
                  <template v-if="editingId === session.id">
                    <el-input
                      v-model="editingTitle"
                      size="small"
                      @keyup.enter="handleSaveTitle(session)"
                      @blur="handleSaveTitle(session)"
                      @click.stop
                    />
                  </template>
                  <!-- 非编辑模式 -->
                  <template v-else>
                    <div class="session-title">{{ session.title }}</div>
                    <div class="session-meta">
                      <span class="scene-tag">{{ session.scene }}</span>
                      <span class="session-time">{{ formatTime(session.updatedAt) }}</span>
                    </div>
                  </template>
                </div>
                <el-dropdown trigger="click" @command="(cmd) => cmd === 'edit' ? handleEditTitle(session) : handleDeleteSession(session)">
                  <el-button link @click.stop>
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">重命名</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <el-empty v-if="chatStore.sessions.length === 0" description="暂无会话记录">
            <el-button type="primary" @click="handleCreateSession">创建第一个会话</el-button>
          </el-empty>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.search-bar {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sessions-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-group {
  margin-bottom: 16px;
}

.group-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 4px 8px;
  margin-bottom: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.session-item:hover {
  background-color: var(--el-fill-color-light);
}

.session-item.active {
  background-color: var(--el-color-primary-light-9);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.scene-tag {
  font-size: 10px;
  padding: 1px 6px;
  background-color: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  border-radius: 4px;
}

.session-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
