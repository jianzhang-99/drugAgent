<template>
  <div class="formal-document-layout" v-if="data">
    <!-- 正文区域 -->
    <div class="doc-main" id="doc-scroll-container" @scroll="onScroll">
      <div class="doc-content">
        <!-- 头部信息 -->
        <div class="doc-header">
          <h1 class="doc-title">标书审查报告</h1>
          <div class="doc-meta">
            <div class="meta-row"><span>报告编号：</span><span>{{ data.metadata?.reportId || '-' }}</span></div>
            <div class="meta-row"><span>任务编号：</span><span>{{ data.metadata?.taskId || '-' }}</span></div>
            <div class="meta-row"><span>生成时间：</span><span>{{ data.metadata?.generatedAt || '-' }}</span></div>
            <div class="meta-row"><span>审查对象：</span><span>{{ data.metadata?.projectTarget || '-' }}</span></div>
            <div class="meta-row"><span>审查类型：</span><span>{{ data.metadata?.reviewType || '双文档比对' }}</span></div>
            <div class="meta-row"><span>审查范围：</span><span>{{ data.executiveSummary?.metrics?.documentCount || 0 }} 份文件，{{ data.executiveSummary?.metrics?.partyCount || 0 }} 家投标主体</span></div>
          </div>
        </div>

        <div class="doc-divider"></div>

        <!-- 一、审查结论 -->
        <section id="chapter-1" class="doc-section">
          <h2>一、审查结论</h2>
          <h3>1.1 总体结论</h3>
          <p class="strong-text" :class="metricColor(data.executiveSummary?.riskLevel)">
            [{{ levelLabel(data.executiveSummary?.riskLevel) }}]
          </p>
          
          <h3>1.2 风险评分</h3>
          <p class="strong-text">{{ data.executiveSummary?.riskScore }}/100</p>
          
          <h3>1.3 结论摘要</h3>
          <p>
            本次审查共识别出 {{ data.executiveSummary?.metrics?.deduplicatedRules }} 项风险信号，其中 {{ data.executiveSummary?.metrics?.coreEvidenceCount }} 项为核心证据。综合判断，本次投标文件存在 
            <strong :class="metricColor(data.executiveSummary?.riskLevel)">[{{ levelLabel(data.executiveSummary?.riskLevel) }}]</strong>，建议 
            <strong>[{{ data.executiveSummary?.recommendedAction }}]</strong>。
          </p>
          
          <h3>1.4 一句话结论</h3>
          <p class="strong-text">建议：[{{ data.executiveSummary?.recommendedAction }}]</p>
        </section>

        <!-- 二、关键指标概览 -->
        <section id="chapter-2" class="doc-section">
          <h2>二、关键指标概览</h2>
          <table class="doc-table">
            <thead>
              <tr><th>指标</th><th style="text-align: right">数值</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr><td>核心证据数</td><td align="right">{{ data.executiveSummary?.metrics?.coreEvidenceCount }}</td><td>可直接支撑审查结论的关键证据</td></tr>
              <tr><td>高置信命中数</td><td align="right">{{ data.executiveSummary?.metrics?.highConfidenceHits }}</td><td>高可信度风险命中条数</td></tr>
              <tr><td>命中规则数</td><td align="right">{{ data.executiveSummary?.metrics?.deduplicatedRules }}</td><td>去重后的规则类型数量</td></tr>
              <tr><td>涉及文档数</td><td align="right">{{ data.executiveSummary?.metrics?.documentCount }}</td><td>参与审查的文件数量</td></tr>
              <tr><td>涉及主体数</td><td align="right">{{ data.executiveSummary?.metrics?.partyCount }}</td><td>参与比对的投标主体数量</td></tr>
            </tbody>
          </table>
        </section>

        <!-- 三、风险分布总览 -->
        <section id="chapter-3" class="doc-section">
          <h2>三、风险分布总览</h2>
          <table class="doc-table">
            <thead>
              <tr><th>风险类型</th><th>风险等级</th><th style="text-align: right">命中数量</th><th>是否建议人工复核</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="dist in data.riskOverview?.distributions" :key="dist.riskType">
                <td>{{ dist.riskType }}</td>
                <td :class="metricColor(dist.level)">[{{ levelLabel(dist.level) }}]</td>
                <td align="right">{{ dist.hitCount }}</td>
                <td>{{ dist.needReview ? '是' : '否' }}</td>
                <td>{{ dist.explanation }}</td>
              </tr>
            </tbody>
          </table>
          <p class="doc-note">说明：本页仅展示真实识别到的风险类型；未识别到的风险可标注为“未发现”，不建议统一展示为“低”。</p>
        </section>

        <!-- 四、涉及文档信息 -->
        <section id="chapter-4" class="doc-section">
          <h2>四、涉及文档信息</h2>
          <table class="doc-table">
            <thead>
              <tr><th>序号</th><th>投标主体</th><th>文件名称</th><th>文档角色</th><th style="text-align: right">参与核心风险数</th><th>涉及风险类型</th></tr>
            </thead>
            <tbody>
              <tr v-for="(doc, index) in data.documents" :key="doc.id">
                <td>{{ index + 1 }}</td>
                <td>{{ doc.partyName }}</td>
                <td>{{ doc.fileName }}</td>
                <td>{{ doc.docRole }}</td>
                <td align="right">{{ doc.hitRiskCount }}</td>
                <td>{{ doc.involvedRisks?.join(' / ') || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <p class="doc-note">说明：本次审查基于以上文件开展，重点围绕文本内容、结构模板、报价信息、团队信息等维度进行比对分析。</p>
        </section>

        <!-- 五、核心风险 Top 3 -->
        <section id="chapter-5" class="doc-section">
          <h2>五、核心风险 Top 3</h2>
          <template v-if="data.riskOverview?.topRisks?.length">
            <div v-for="risk in data.riskOverview?.topRisks" :key="risk.rank" class="risk-block">
              <h3>风险 {{ risk.rank }}：[{{ risk.riskName }}]</h3>
              <p>风险等级：<strong :class="metricColor(risk.riskLevel)">[{{ levelLabel(risk.riskLevel) }}]</strong></p>
              <p>风险类型：<strong>[{{ typeLabel(risk.riskType) }}]</strong></p>
              <p class="field-title">风险说明：</p>
              <p>{{ risk.description }}</p>
              <p class="field-title">关键事实：</p>
              <p>{{ risk.keyFact }}</p>
              <p class="field-title">判定依据：</p>
              <p>{{ risk.basis }}</p>
              <p class="field-title">建议动作：</p>
              <p>{{ risk.action }}</p>
            </div>
          </template>
          <div v-else class="doc-note">未命中高危核心风险。</div>
        </section>

        <!-- 六、关键证据明细 -->
        <section id="chapter-6" class="doc-section">
          <h2>六、关键证据明细</h2>
          <template v-if="data.evidences?.length">
            <div v-for="ev in data.evidences" :key="ev.evidenceId" class="evidence-block">
              <h3>证据 {{ ev.evidenceId }}</h3>
              <p>证据标题：<strong>[{{ ev.title }}]</strong></p>
              <p>证据类型：<strong>[{{ ev.type === 'price_diff' ? '报价对比' : ev.type === 'team_diff' ? '人员对比' : '文本/结构比对' }}]</strong></p>
              <p>证据等级：<strong :class="metricColor(ev.level)">[{{ levelLabel(ev.level) }}]</strong></p>
              <p>涉及文档：<strong>[文档A] vs [文档B]</strong></p>
              
              <p class="field-title">证据摘要：</p>
              <p>{{ ev.summary }}</p>
              
              <p class="field-title">关键数据：</p>
              <ul class="doc-list">
                <li v-if="ev.diffPayload?.similarityScore">相似度：[{{ ev.diffPayload.similarityScore }}]</li>
                <li v-if="ev.diffPayload?.divergence">核心差异：[{{ ev.diffPayload.divergence }}]</li>
                <li>判定：[{{ ev.diffPayload?.diffVerdict }}]</li>
              </ul>
              
              <p class="field-title">证据说明：</p>
              <p>{{ ev.analysis }}</p>
              
              <p class="field-title">原文摘录：</p>
              <div class="diff-container">
                <div class="diff-side side-a">
                  <div class="diff-head">文档A</div>
                  <pre>{{ ev.diffPayload?.contentA || '-' }}</pre>
                </div>
                <div class="diff-side side-b">
                  <div class="diff-head">文档B</div>
                  <pre>{{ ev.diffPayload?.contentB || '-' }}</pre>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="doc-note">当前未提取到足够展示原文比对的关键证据。</div>
        </section>

        <!-- 七、详细比对结果 -->
        <section id="chapter-7" class="doc-section">
          <h2>七、详细比对结果</h2>
          
          <h3>7.1 文本与结构相似比对</h3>
          <table class="doc-table">
            <thead>
              <tr><th>序号</th><th>比对位置/标题</th><th>文档A内容摘要</th><th>文档B内容摘要</th><th style="text-align: right">相似度</th><th>判定</th></tr>
            </thead>
            <tbody>
              <tr v-for="(ev, idx) in textEvs" :key="ev.evidenceId">
                <td>{{ idx + 1 }}</td>
                <td>{{ ev.title }}</td>
                <td class="ellipsis-cell">{{ ev.diffPayload?.contentA }}</td>
                <td class="ellipsis-cell">{{ ev.diffPayload?.contentB }}</td>
                <td align="right">{{ ev.diffPayload?.similarityScore || '-' }}</td>
                <td :class="metricColor(ev.level)">{{ levelLabel(ev.level) }}</td>
              </tr>
              <tr v-if="!textEvs.length"><td colspan="6" align="center" style="color: #94a3b8">暂无文本雷同记录</td></tr>
            </tbody>
          </table>

          <h3 style="margin-top: 24px">7.2 报价/团队比对（如适用）</h3>
          <table class="doc-table">
            <thead>
              <tr><th>序号</th><th>比对项</th><th>文档A数据</th><th>文档B数据</th><th>判定</th></tr>
            </thead>
            <tbody>
              <tr v-for="(ev, idx) in nonTextEvs" :key="ev.evidenceId">
                <td>{{ idx + 1 }}</td>
                <td>{{ ev.title }}</td>
                <td class="ellipsis-cell">{{ ev.diffPayload?.contentA }}</td>
                <td class="ellipsis-cell">{{ ev.diffPayload?.contentB }}</td>
                <td :class="metricColor(ev.level)">{{ levelLabel(ev.level) }}</td>
              </tr>
              <tr v-if="!nonTextEvs.length"><td colspan="5" align="center" style="color: #94a3b8">暂无报价或团队雷同记录</td></tr>
            </tbody>
          </table>
        </section>

        <!-- 八、处置建议 -->
        <section id="chapter-8" class="doc-section">
          <h2>八、处置建议</h2>
          <h3>8.1 一级动作｜立即执行</h3>
          <ol class="doc-list">
            <li v-for="(act, idx) in data.actionPlan?.level1Actions" :key="idx">[{{ act }}]</li>
            <li v-if="!data.actionPlan?.level1Actions?.length">暂无</li>
          </ol>
          
          <h3>8.2 二级动作｜进一步核验</h3>
          <ol class="doc-list">
            <li v-for="(act, idx) in data.actionPlan?.level2Actions" :key="idx">[{{ act }}]</li>
             <li v-if="!data.actionPlan?.level2Actions?.length">暂无</li>
          </ol>
          
          <h3>8.3 三级动作｜必要时追溯</h3>
          <ol class="doc-list">
            <li v-for="(act, idx) in data.actionPlan?.level3Actions" :key="idx">[{{ act }}]</li>
             <li v-if="!data.actionPlan?.level3Actions?.length">暂无</li>
          </ol>
        </section>

        <!-- 九、建议责任分工 -->
        <section id="chapter-9" class="doc-section">
          <h2>九、建议责任分工</h2>
          <table class="doc-table">
            <thead>
              <tr><th>动作</th><th>建议责任角色</th><th>优先级</th><th>备注</th></tr>
            </thead>
            <tbody>
              <tr v-for="(roleAct, idx) in data.actionPlan?.responsibilityMatrix" :key="idx">
                <td>{{ roleAct.action }}</td>
                <td>[{{ roleAct.role }}]</td>
                <td>[{{ roleAct.priority }}]</td>
                <td>{{ roleAct.remark || '-' }}</td>
              </tr>
              <tr v-if="!data.actionPlan?.responsibilityMatrix?.length"><td colspan="4" align="center" style="color: #94a3b8">暂无分派记录</td></tr>
            </tbody>
          </table>
        </section>

        <!-- 十、审查结论归纳 -->
        <section id="chapter-10" class="doc-section">
          <h2>十、审查结论归纳</h2>
          <p>
            本次审查共识别出 {{ data.executiveSummary?.metrics?.deduplicatedRules || 0 }} 项风险信号，其中 {{ data.executiveSummary?.metrics?.coreEvidenceCount || 0 }} 项为高置信核心证据。综合判断，本项目标书存在 
            <strong :class="metricColor(data.executiveSummary?.riskLevel)">[{{ levelLabel(data.executiveSummary?.riskLevel) }}]</strong>，建议 
            <strong>[{{ data.executiveSummary?.recommendedAction }}]</strong>。
          </p>
          <p>
            本报告结论基于当前审查范围内的文本、结构及规则分析结果形成，仅作为风险识别与人工复核辅助依据，不直接替代最终评审、合规或法律判断。
          </p>
        </section>

        <!-- 十一、审查范围与局限说明 -->
        <section id="chapter-11" class="doc-section">
          <h2>十一、审查范围与局限说明</h2>
          <ol class="doc-list">
            <li>本次审查仅基于已上传文件进行分析。</li>
            <li>若存在未纳入比对的补充文件、历史文件或附件材料，可能影响最终判断。</li>
            <li>文本相似、模板同源等结果属于风险信号，不直接等同于法律或行政定性。</li>
            <li>最终结论仍需结合人工复核、业务背景及项目实际情况综合判断。</li>
          </ol>
        </section>

        <!-- 十二、附录 -->
        <section id="chapter-12" class="doc-section" style="margin-bottom: 60px">
          <h2>十二、附录</h2>
          <h3>12.1 命中规则清单</h3>
          <table class="doc-table">
            <thead>
              <tr><th>规则名称</th><th>规则编码</th><th style="text-align: right">命中次数</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="(rule, idx) in data.metadata?.hitRules" :key="idx">
                <td>{{ rule.ruleName }}</td>
                <td>[{{ rule.ruleCode }}]</td>
                <td align="right">{{ rule.hitCount }}</td>
                <td>{{ rule.remark || '-' }}</td>
              </tr>
              <tr v-if="!data.metadata?.hitRules?.length"><td colspan="4" align="center" style="color: #94a3b8">暂无规则明细</td></tr>
            </tbody>
          </table>
          
          <h3>12.2 审查元信息</h3>
          <ul class="doc-list">
            <li>审查任务编号：[{{ data.metadata?.taskId || '-' }}]</li>
            <li>报告生成时间：[{{ data.metadata?.generatedAt || '-' }}]</li>
            <li>模型/系统版本：[{{ data.metadata?.systemVersion || '-' }}]</li>
          </ul>
        </section>
      </div>
    </div>

    <!-- 右侧导航 Anchor -->
    <div class="doc-sidebar">
      <div class="sidebar-title">报告目录</div>
      <ul class="anchor-list">
        <li v-for="nav in navs" :key="nav.id" :class="{ active: activeAnchor === nav.id }" @click="scrollTo(nav.id)">
          {{ nav.label }}
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import type { ReportData } from '../../types/report.types';

const props = defineProps<{ data?: ReportData }>();

const textEvs = computed(() => props.data?.evidences?.filter(e => e.type === 'text_diff' || e.type === 'structure_diff') || []);
const nonTextEvs = computed(() => props.data?.evidences?.filter(e => e.type === 'price_diff' || e.type === 'team_diff') || []);

const navs = [
  { id: 'chapter-1', label: '一、审查结论' },
  { id: 'chapter-2', label: '二、关键指标' },
  { id: 'chapter-3', label: '三、风险分布' },
  { id: 'chapter-4', label: '四、涉及文档' },
  { id: 'chapter-5', label: '五、核心风险' },
  { id: 'chapter-6', label: '六、证据明细' },
  { id: 'chapter-7', label: '七、详情比对' },
  { id: 'chapter-8', label: '八、处置建议' },
  { id: 'chapter-9', label: '九、责任分工' },
  { id: 'chapter-10', label: '十、归纳总结' },
  { id: 'chapter-11', label: '十一、局限说明' },
  { id: 'chapter-12', label: '十二、附录明细' },
];

const activeAnchor = ref('chapter-1');

function scrollTo(id: string) {
  const el = document.getElementById(id);
  const container = document.getElementById('doc-scroll-container');
  if (el && container) {
    container.scrollTo({ top: el.offsetTop - 80, behavior: 'smooth' });
    activeAnchor.value = id;
  }
}

let scrollTimeout: any = null;
function onScroll(e: Event) {
  if (scrollTimeout) clearTimeout(scrollTimeout);
  scrollTimeout = setTimeout(() => {
    const container = e.target as HTMLElement;
    const scrollP = container.scrollTop + 100;
    for (let i = navs.length - 1; i >= 0; i--) {
      const el = document.getElementById(navs[i].id);
      if (el && el.offsetTop <= scrollP) {
        activeAnchor.value = navs[i].id;
        break;
      }
    }
  }, 50);
}

function levelLabel(value?: string) {
  if (value === 'high') return '高风险';
  if (value === 'medium') return '中风险';
  if (value === 'low') return '低风险';
  return '低风险/未发现';
}

function metricColor(value?: string) {
  if (value === 'high') return 'color-danger';
  if (value === 'medium') return 'color-warning';
  return 'color-safe';
}

function typeLabel(type?: string) {
  const dict: Record<string, string> = {
    pricing: '报价异常', team: '团队重复', text_similarity: '文本相似', template: '模板同源', other: '其他辅助'
  };
  return dict[type || ''] || '未知风险';
}
</script>

<style scoped>
.formal-document-layout {
  display: flex;
  height: calc(100vh - 140px);
  max-width: 1100px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.05);
}

.doc-main {
  flex: 1;
  overflow-y: auto;
  padding: 40px 60px;
  scroll-behavior: smooth;
  color: #0f172a;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

.doc-sidebar {
  width: 220px;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  padding: 24px 16px;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 14px;
  font-weight: bold;
  color: #475569;
  margin-bottom: 16px;
  padding-left: 12px;
}

.anchor-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.anchor-list li {
  padding: 8px 12px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  margin-bottom: 2px;
}

.anchor-list li:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.anchor-list li.active {
  background: #e0f2fe;
  color: #0369a1;
  font-weight: bold;
}

/* Document Typography */
.doc-header {
  text-align: center;
  margin-bottom: 30px;
}
.doc-title {
  font-size: 28px;
  font-weight: bold;
  letter-spacing: 2px;
  margin-bottom: 24px;
}
.doc-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px 32px;
  font-size: 13px;
  color: #475569;
}
.meta-row span:first-child { font-weight: bold; }

.doc-divider {
  border-top: 2px solid #0f172a;
  margin-bottom: 40px;
}

.doc-section {
  margin-bottom: 40px;
}

.doc-section h2 {
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 8px;
  margin-bottom: 16px;
}

.doc-section h3 {
  font-size: 16px;
  font-weight: bold;
  margin: 20px 0 10px;
  color: #1e293b;
}

.doc-section p {
  line-height: 1.8;
  margin-bottom: 10px;
  font-size: 14px;
}

.strong-text {
  font-weight: bold;
  font-size: 15px;
}

.color-danger { color: #dc2626 !important; }
.color-warning { color: #d97706 !important; }
.color-safe { color: #16a34a !important; }

/* Table Style */
.doc-table {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
  font-size: 14px;
}
.doc-table th, .doc-table td {
  border: 1px solid #cbd5e1;
  padding: 10px 12px;
  text-align: left;
}
.doc-table th {
  background: #f1f5f9;
  font-weight: bold;
}
.ellipsis-cell {
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-note {
  font-size: 13px;
  color: #64748b;
  margin-top: 8px;
  line-height: 1.6;
}

/* Lists */
.doc-list {
  padding-left: 20px;
  font-size: 14px;
  line-height: 1.8;
  margin: 10px 0;
}

/* Blocks */
.risk-block, .evidence-block {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
}
.risk-block h3, .evidence-block h3 {
  margin-top: 0;
  font-size: 15px;
}
.field-title {
  font-weight: bold;
  color: #334155;
  margin: 12px 0 4px !important;
}

/* Diff Style for text */
.diff-container {
  display: flex;
  gap: 1px;
  background: #cbd5e1;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  overflow: hidden;
}
.diff-side {
  flex: 1;
  background: #fff;
  min-width: 0;
}
.diff-head {
  background: #f1f5f9;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: bold;
  border-bottom: 1px solid #e2e8f0;
}
.diff-side pre {
  margin: 0;
  padding: 12px;
  font-size: 13px;
  font-family: inherit;
  white-space: pre-wrap;
  color: #334155;
  background: #fef2f2; /* diff red bg */
}
.side-b pre {
  background: #f0fdf4; /* diff green bg */
}

@media (max-width: 900px) {
  .formal-document-layout {
    flex-direction: column;
    height: auto;
  }
  .doc-sidebar {
    width: auto;
    border-left: none;
    border-top: 1px solid #e2e8f0;
    max-height: 200px;
  }
}
</style>
