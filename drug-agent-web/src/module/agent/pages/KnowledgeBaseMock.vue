<template>
  <div class="mock-page">
    <div class="page-header">
      <h2>合规知识库</h2>
      <p>内置医药行业招标、合同合规规则与法务档案，支持自定义规则扩展。</p>
    </div>
    <div class="empty-state">
      <div class="empty-icon">📚</div>
      <h3>合规规则库正在建设中</h3>
      <p>前端占位页面，后续支持上传 RAG 语料，管理豁免清单及敏感词库。</p>
    </div>
  </div>
</template>

<style scoped>
.mock-page {
  padding: 40px 48px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header h2 {
  font-size: 28px;
  color: #1e293b;
  margin: 0 0 8px;
}

.page-header p {
  color: #64748b;
  margin: 0;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  margin-top: -100px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 24px;
}

.empty-state h3 {
  font-size: 20px;
  color: #475569;
  margin: 0 0 12px;
}

.empty-state p {
  font-size: 14px;
}
</style>
