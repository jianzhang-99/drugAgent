<template>
  <header class="workspace-header">
    <div class="header-title">智能体审查工作台</div>

    <div class="header-actions">
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
