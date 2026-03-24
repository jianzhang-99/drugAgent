<template>
  <workspace-layout>
    <section class="page-shell">
      <header class="page-header">
        <h1>使用帮助</h1>
        <p>系统默认按对话方式工作。你可以像使用 ChatGPT 一样连续追问，任务看板只用于回看历史任务。</p>
      </header>

      <div class="help-grid">
        <article class="help-card">
          <h2>如何发起对话</h2>
          <p>直接输入监管问题，例如“请从付款条款、违约责任、排他约定三个维度预审这份合同”。</p>
        </article>

        <article class="help-card">
          <h2>如何上传标书材料</h2>
          <p>上传至少 2 份 `.docx`、`.doc` 或 `.md` 文件后发送消息。系统会分析并把结果保留到历史任务，但当前页面不会跳转。</p>
        </article>

        <article class="help-card">
          <h2>任务看板的作用</h2>
          <p>任务看板只用于查看历史任务、结果留痕和后续复盘，不是主交互入口。</p>
        </article>

        <article class="help-card">
          <h2>推荐提问模板</h2>
          <p>“请按高、中、低风险输出结论和处理建议。” 或 “请列出需要人工复核的证据项和原因。”</p>
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
