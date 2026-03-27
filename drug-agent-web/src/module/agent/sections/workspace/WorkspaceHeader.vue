<template>
  <header class="workspace-header">
    <div class="header-title">智能体审查工作台</div>

    <div class="header-actions">
      <button class="task-pill" type="button">
        <span class="task-wave">∿</span>
        <span>0 活跃任务</span>
      </button>
      <div class="profile-badge">HD</div>
    </div>
  </header>
</template>

<style scoped>
.workspace-header {
  display: flex;
  height: 74px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6edf5;
  padding: 0 32px;
  background: rgba(255, 255, 255, 0.9);
}

.header-title {
  color: #7d90aa;
  font-size: 14px;
  font-weight: 700;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.task-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: none;
  border-radius: 999px;
  background: #f6f8fc;
  color: #344863;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 700;
}

.task-wave {
  color: #4f6df5;
}

.profile-badge {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: linear-gradient(135deg, #506bff, #4550dd);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

@media (max-width: 900px) {
  .workspace-header {
    padding: 0 20px;
  }
}
</style>
