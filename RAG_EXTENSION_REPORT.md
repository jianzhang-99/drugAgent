# drugAgent 项目 RAG 扩展报告（多场景统一 MVP 版）

## 1. 报告目标

在保留现有应用层架构（`SceneRouter -> DrugAgentService -> Workflow`）的前提下，建设一套**统一 RAG 能力**，同时服务：

- 合同预审（`CONTRACT_PRECHECK`）
- 耗材/药品监管预警（`RISK_ALERT`）
- 标书审查（`TENDER_REVIEW`）

MVP 阶段不拆多个 RAG 子系统，而是通过“统一底座 + 场景策略参数化”实现多场景复用。

---

## 2. 当前基础与问题

### 2.1 已有基础

项目已具备 RAG 接入关键条件：

- 场景路由：`SceneRouter`
- 工作流编排：`WorkflowRegistry` + 各 `SceneWorkflow`
- 模型调用层：`AgentChatService`、`QwenService`
- 向量入口基础：`VectorStoreConfig`

### 2.2 当前问题

- 回答主要依赖模型记忆，检索增强不足。
- 缺少稳定引用（citation），审计与复核成本高。
- 文档知识没有统一“切片-向量化-元数据-检索”闭环。
- 仅用 `scene + orgId` 过滤粒度不足，存在误杀和漏召回风险。

---

## 3. MVP 总体方案：一套 RAG，三类场景复用

### 3.1 设计原则

1. **统一底座**：一个 Ingest + Retrieve + Generate 链路。
2. **检索混合化**：默认采用 `BM25/关键词 + 向量` 的混合召回，不走纯向量单路。
3. **策略按场景分层**：按 `SceneEnum` 注入不同召回策略、过滤策略与 Prompt 模板。
4. **输出统一**：统一返回 `answer + citations + evidenceList + riskLevel + decision`。
5. **强拒答优先**：审核/监管场景宁可保守拒答，也不冒险误命中。

### 3.2 架构图（逻辑）

```
数据入库（统一）
  -> 文档清洗
  -> 切片 chunk
  -> embedding + keyword index（BM25）
  -> 写入 VectorStore / KeywordStore（带 metadata）

查询（统一）
  -> SceneRouter 命中场景
  -> Workflow 调用 KnowledgeRagService
  -> RagPolicyResolver 按场景给策略
  -> Hybrid Retrieve（BM25 + Vector）
  -> 融合排序（RRF/加权）+ 过滤 + 阈值判定
  -> LLM 生成答案（或拒答/降级）
  -> 返回统一结构
```

---

## 4. 模块设计（MVP 最小可用）

### 4.1 新增服务（统一服务，不按场景拆类）

1. `KnowledgeIngestService`
   - 文档清洗、切片、embedding、关键词索引写入

2. `KnowledgeRagService`
   - 接收 `scene + question + orgId + optional facets`
   - 执行混合检索、融合排序、阈值门控、生成/拒答

3. `RagPolicyResolver`
   - 根据 `SceneEnum` 返回策略：
     - 召回通道权重（BM25 vs Vector）
     - topK / threshold / minEvidence
     - facet 过滤规则
     - prompt 模板
     - 拒答阈值与降级动作

4. `RetrievalFusionService`
   - 负责混合检索结果融合（推荐 RRF 起步）

### 4.2 与现有流程衔接

- 不改 `DrugAgentService` 主流程结构。
- 在各场景 Workflow 内引入 `KnowledgeRagService`。
- 先接 `RISK_ALERT` 与 `CONTRACT_PRECHECK`，再接 `TENDER_REVIEW` 细分策略。

---

## 5. 统一数据模型与检索维度（重点）

> 结论：`scene + orgId` 仅是基础隔离维度，不足以支撑精细召回。

### 5.1 Chunk 元数据（必须）

- `scene`：`contract_precheck` / `risk_alert` / `tender_review` / `general`
- `orgId`
- `docType`：法规、制度、合同模板、耗材目录、处罚通报等
- `subScene`：场景子域（如 `tender_tech`、`tender_procurement`、`risk_drug`、`risk_consumable`）
- `topicTags`：主题标签（如“资格条款/技术参数/医保编码/异常阈值”）
- `sourceId` / `sourceTitle`
- `chunkIndex`
- `version` / `effectiveDate`
- `region`（可选，地方监管口径差异）

### 5.2 分层过滤策略

推荐采用“硬过滤 + 软重排”两层：

1. **硬过滤（必须）**：`orgId`、文档状态、版本有效性
2. **软过滤/加权（可回退）**：`scene`、`subScene`、`docType`、`topicTags`

这样可减少误杀，同时抑制跨域噪声。

---

## 6. 检索策略：混合检索 + 场景化召回

### 6.1 为什么必须混合检索

- 监管文本中大量术语、编号、条款号、药品名对**关键词检索**更敏感。
- 语义近似和改写表达对**向量检索**更有效。
- 单一路径容易偏科，MVP 阶段最稳妥是 Hybrid。

### 6.2 MVP 推荐流程

1. Query 改写（可选，先轻量）
2. BM25 召回 topN
3. Vector 召回 topN
4. 融合排序（RRF 或加权归一）
5. 按策略截断为 topK
6. 证据充足性判定（`minEvidence` + `minScore`）

### 6.3 场景化召回策略示例

- `CONTRACT_PRECHECK`
  - 权重：BM25 0.6 / Vector 0.4
  - 原因：条款编号、固定法务术语较多

- `RISK_ALERT`（耗材/药品监管）
  - 权重：BM25 0.5 / Vector 0.5
  - 原因：规则条文 + 语义归因都重要

- `TENDER_REVIEW`
  - 权重：BM25 0.4 / Vector 0.6
  - 原因：相似描述、语义雷同判断更依赖语义

> 注：以上为起始值，需用离线评估持续调参。

---

## 7. 防幻觉：不止 Prompt，还要“拒答/降级”

### 7.1 核心原则

审核场景优先“**不误判**”。命中不足时必须拒答，不输出推测性结论。

### 7.2 决策门控（Decision Gate）

在生成前增加门控：

- `retrievedCount < minEvidence` -> 拒答
- `top1Score < minScore` -> 拒答
- 证据冲突明显 -> 降级为“需人工复核”

### 7.3 标准降级输出

- `decision = NO_HIT`：未命中充分证据
- `decision = NEED_HUMAN_REVIEW`：证据不足或冲突
- `answer`：返回“未命中/需人工补充”说明
- `citations`：可为空或仅给低置信候选

### 7.4 Prompt 约束（仍需要）

- 仅依据检索证据回答
- 禁止补全未提供事实
- 必须返回引用

> Prompt 只是最后一道约束，不可替代检索门控。

---

## 8. 实施计划（2 周）

### 第 1 周：能力闭环

1. `KnowledgeIngestService`：入库 + metadata 标准化
2. `KnowledgeRagService`：混合检索 + 融合排序
3. `RagPolicyResolver`：场景策略（权重/阈值/过滤）
4. 接入 `RISK_ALERT` 与 `CONTRACT_PRECHECK` workflow

### 第 2 周：安全与质量

1. 上线 `Decision Gate`（拒答/降级）
2. 增加 `subScene/topicTags/docType` 过滤策略
3. 补充指标：空召回率、误命中率、拒答率、P95 耗时
4. 小流量接入 `TENDER_REVIEW` 子域（技术/采购）

---

## 9. 验收标准（MVP DoD）

1. 同一套 RAG 服务稳定支持 2+ 场景。
2. 默认走混合检索（BM25 + Vector），非纯向量。
3. 回答中 80%+ 附带有效引用。
4. 审核场景在低命中时稳定拒答/降级，不输出主观推断。
5. 检索过滤可支持 `scene + orgId + subScene/docType/topicTags`。

---

## 10. 风险与应对

1. **误杀/漏召回**
   - 应对：从“硬 scene 过滤”改为“分层过滤 + 融合排序”

2. **误命中导致错误审核结论**
   - 应对：Decision Gate + 强拒答策略 + 人工复核出口

3. **性能压力上升（双通道检索）**
   - 应对：控制 topN、缓存热 query、并行检索

4. **策略漂移**
   - 应对：按场景维护评估集，定期回归测试

---

## 11. 结论

你提出的四点建议应作为本项目 RAG MVP 的默认要求：

- 不走纯向量，采用混合检索
- 场景化召回策略参数化
- 过滤维度从 `scene + orgId` 扩展到 `subScene/docType/topicTags`
- 防幻觉落到“检索门控 + 拒答/降级”，而非仅靠 Prompt

这套方案与当前代码结构兼容，且更符合审核类业务“宁缺毋滥”的上线要求。