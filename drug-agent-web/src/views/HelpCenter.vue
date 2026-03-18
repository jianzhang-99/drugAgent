<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <h1>使用帮助</h1>
        <p>快速了解系统支持的操作方式、推荐提问方式和典型工作流。</p>
      </header>

      <div class="help-grid">
        <article class="help-card">
          <h2>如何发起工作台问答</h2>
          <p>直接输入监管问题，例如“请从付款条款、违约责任、排他条款三个维度预审这份合同”。</p>
        </article>

        <article class="help-card">
          <h2>如何创建标书审查任务</h2>
          <p>上传至少 2 份 `.docx` 标书文件，系统会创建任务并跳转到任务看板，随后可进入详情页做解析。</p>
        </article>

        <article class="help-card">
          <h2>知识库支持哪些能力</h2>
          <p>支持知识文档导入、规则组启停、实体字典维护。当前为前端本地持久化版本，适合演示与录入。</p>
        </article>

        <article class="help-card">
          <h2>推荐提问模板</h2>
          <p>“请总结本次风险点并列出需要人工复核的证据。”、“请按高、中、低风险输出结论和处理建议。”</p>
        </article>
      </div>
    </section>
  </workspace-layout>
</template>

<script setup>
import WorkspaceLayout from '../components/layout/WorkspaceLayout.vue'
</script>

<style scoped>
.page-shell {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px;
}

.page-header h1 {
  margin: 0 0 8px;
  font-size: 30px;
  font-weight: 850;
}

.page-header p,
.help-card p {
  color: var(--text-sub);
  line-height: 1.7;
}

.help-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 24px;
}

.help-card {
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.help-card h2 {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 800;
}

@media (max-width: 900px) {
  .help-grid {
    grid-template-columns: 1fr;
  }
}
</style>
