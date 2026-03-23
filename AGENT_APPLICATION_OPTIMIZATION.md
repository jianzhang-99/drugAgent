# Agent 应用层优化建议（drugAgent）

> 评估范围：`DrugAgentController`、`DrugAgentService`、`AgentChatService`、`QwenService`、`SceneRouter`
>
> 目标：提升稳定性、可维护性、扩展性与工程化水平。

---

## 一、总体结论

当前应用层的分层方向是正确的，已经具备了“路由 → 编排 → 执行”的基本骨架，且有一定可观测性基础（如 traceId）。

建议优先处理 **异常一致性、流式生命周期管理、入参校验统一** 三个问题，能最快提升线上稳定性。

---

## 二、优先级优化清单

## P0（必须优先）——稳定性与一致性

### 1. 异常处理风格统一
- 现状：`QwenService.chat()` 吞异常并返回字符串，其他位置则直接抛异常。
- 风险：调用方无法可靠区分“正常回复”与“异常文本”；告警与重试策略难统一。
- 建议：统一为“抛业务异常（含错误码）+ 全局异常处理器统一响应”。

### 2. `streamHandle` 中流式订阅生命周期不完整
- 现状：Service 层直接 `subscribe` 推送 SSE。
- 风险：断连后仍可能推流；资源回收、取消订阅、线程行为不够可控。
- 建议：补齐 `onCompletion / onTimeout / onError` 的清理与取消机制，确保连接结束后停止上游流。

### 3. 入参校验规则统一
- 现状：`chat` 和 `streamChat` 校验策略不一致，`sessionId/userId` 缺省逻辑分散。
- 风险：边界输入行为不一致，线上问题难定位。
- 建议：DTO 层统一 `@Valid` + `@NotBlank`/`@Size`，Controller 只保留极少量流程判断。

---

## P1（高价值）——可维护性与扩展性

### 4. `DrugAgentService` 职责过重，建议拆分
- 现状：同时承担编排、路由、SSE、文件处理、元数据拼装等职责。
- 建议拆分为：
  - `AgentOrchestrationService`：路由与 workflow 调度
  - `UploadTaskService`：上传文件与元数据组装
  - `TenderIngestionService`：标书解析与聚合
  - `StreamResponseService`：SSE 事件与状态输出

### 5. `agentType` 字符串判断建议枚举化
- 现状：`default/compliance_review/data_analysis` 依赖字符串分支。
- 风险：易拼写错误，不利于重构。
- 建议：引入 `AgentType` 枚举，集中维护 `SceneEnum -> AgentType -> SystemPrompt` 映射。

### 6. 同步与流式能力边界需显式化
- 现状：同步 `handle` 走完整 workflow；流式 `streamHandle` 多为对话兜底。
- 风险：前端/用户误以为两者能力等价。
- 建议：在响应中增加能力标记（如 `capabilityMode: full|fallback`）。

---

## P2（质量提升）——路由与业务精度

### 7. `SceneRouter` 由纯关键词升级为“两段式路由”
- 现状：规则路由可解释，但复杂输入下准确率有限。
- 建议：
  1) 规则快速命中；
  2) 未命中时走轻量 LLM 分类兜底（输出置信度与理由）。
- 保留并扩展字段：`routeReason` + `routeConfidence`。

### 8. 路由时机优化：先做轻量文件特征抽取
- 现状：部分场景依赖 metadata，信息粒度不足。
- 建议：上传后先抽取 `文件名 + MIME + 首段文本特征` 再路由，提升分类准确度。

### 9. 多文件处理从串行改为可控并行
- 现状：多文件解析串行执行，吞吐受限。
- 建议：线程池并行 + 单文件失败隔离（部分成功策略）。

---

## P3（工程化）——长期收益

### 10. 配置外置化
- 建议把硬编码项移入 `application.yml` + `@ConfigurationProperties`：
  - chat memory depth（如 `10`）
  - SSE timeout
  - 风险默认阈值
  - done 模板文案

### 11. 可观测性深化
- 建议新增指标：
  - 场景路由命中率、误判率
  - 模型调用耗时、失败率、token 消耗
  - 文件解析成功率、平均耗时、失败类型分布

### 12. 统一模型调用边界
- 现状：`QwenService` 与 `AgentChatService` 定位部分重叠。
- 建议：
  - `ModelGateway`：纯模型调用能力
  - `ConversationalAgentService`：带 advisor/memory/scene prompt 的会话能力

---

## 三、建议实施顺序（1~2 周）

### 第 1 阶段（1~2 天）
1. 统一异常处理（P0-1）
2. 完善流式生命周期管理（P0-2）
3. 统一入参校验（P0-3）

### 第 2 阶段（2~4 天）
1. 拆分 `DrugAgentService` 职责（P1-4）
2. 引入 `AgentType` 枚举与映射中心（P1-5）
3. 同步/流式能力标记对齐（P1-6）

### 第 3 阶段（3~5 天）
1. 路由升级为“规则 + LLM 兜底”（P2-7）
2. 文件路由前置轻量特征抽取（P2-8）
3. 多文件并行与失败隔离（P2-9）

---

## 四、当前应用层评估（阶段性）

- 架构清晰度：**8/10**
- 健壮性：**6.5/10**
- 可扩展性：**7/10**
- 工程化成熟度：**6.5/10**

> 结论：已具备可持续演进基础；优先完成 P0 后，线上稳定性和可维护性会明显提升。

---

## 五、结合当前项目增加 RAG 的方案报告

> 目标：在不推翻现有 `SceneRouter + WorkflowRegistry + DrugAgentService` 架构的前提下，新增“可落地、可演进”的本地 RAG 能力，用于法规问答、合同条款检索、标书知识追溯与解释增强。

### 5.1 现状评估（与 RAG 相关）

从当前代码看，你已经具备 RAG 最关键的基础条件：

1. **向量库配置已存在**：`VectorStoreConfig` 已启用 `SimpleVectorStore` 并支持本地文件加载。
2. **模型调用链可复用**：`AgentChatService` 已有 system prompt、memory、advisor 机制，可直接承接“检索增强后生成”。
3. **场景路由框架完备**：`SceneRouter` 与 `WorkflowRegistry` 可无缝新增 `KNOWLEDGE_QA` 或给现有场景注入 RAG 增强。
4. **文档处理能力已具备**：招投标文档解析链路可沉淀为通用“知识入库数据源”。

结论：你当前项目不是“从 0 到 1”，而是“从 0.6 到 1.0”，适合采用 **MVP 先落地 + 分阶段增强** 路线。

---

### 5.2 总体架构（建议）

建议采用旁路增强架构，不破坏现有主流程：

1. **Ingest（入库）层**
   - 输入：文本、md/docx、现有 tender parse 结果。
   - 处理：清洗 → 分块 → embedding → 写入 `VectorStore`。
   - 输出：可检索知识片段（含 metadata）。

2. **Retrieve（检索）层**
   - 输入：用户 query + 业务上下文（scene、tenant、docType）。
   - 处理：向量召回（TopK）+ 元数据过滤。
   - 输出：候选证据片段列表。

3. **Generate（生成）层**
   - 输入：query + 检索证据 + system prompt。
   - 处理：拼装 RAG Prompt，要求引用证据、禁止无依据扩写。
   - 输出：答案 + 引用来源 + 置信度提示。

4. **Observe（观测）层**
   - 记录：召回数量、命中来源、生成耗时、是否兜底、用户反馈。

---

### 5.3 与现有项目的集成方式

#### 方案 A（推荐先做）：新增独立知识问答场景 `KNOWLEDGE_QA`

- 在 `SceneEnum` 增加 `KNOWLEDGE_QA`。
- `SceneRouter` 增加关键词（如“知识库/依据/条款出处/参考文档”）与 metadata hint 识别。
- 新增 `KnowledgeQaWorkflow`，在工作流内部调用 `KnowledgeRagService`。

优点：边界清晰、风险低、便于灰度上线。

#### 方案 B（第二阶段）：对既有场景做“按需增强”

- `CONTRACT_PRECHECK`：先检索法规/合同模板，再给审查建议。
- `TENDER_REVIEW`：对规则命中点补充“历史案例/制度依据”。
- `RISK_ALERT`：对异常指标补充“知识解释”。

优点：业务价值高；挑战：上下文拼装复杂度更高。

---

### 5.4 模块设计（建议新增）

#### 1) `KnowledgeIngestService`

职责：统一知识入库。

核心方法建议：
- `ingestText(String text, Map<String,Object> metadata)`
- `ingestDocument(InputStream stream, String filename, Map<String,Object> metadata)`
- `ingestTenderCase(String caseId)`（复用你现有解析结果）

#### 2) `KnowledgeRagService`

职责：检索增强问答。

核心方法建议：
- `retrieve(String query, Map<String,Object> filter)`
- `askWithRag(String query, String sessionId, String scene, Map<String,Object> filter)`

返回建议包含：
- `answer`
- `citations`（文档名、片段ID、分数）
- `retrievalStats`（topK、耗时、是否命中）

#### 3) `KnowledgeController`

建议提供两个 MVP 接口：

- `POST /api/knowledge/ingest/text`
  - 入参：`text + source + bizType + tags`
  - 出参：`chunkCount + knowledgeIds`

- `POST /api/knowledge/ask`
  - 入参：`query + scene + sessionId + filters`
  - 出参：`answer + citations + traceId`

---

### 5.5 数据分块与 metadata 设计

#### 分块策略（MVP）

- chunk 大小：600~1000 中文字符
- overlap：100~150
- 对标题/条款编号保留结构前缀（便于引用）

#### metadata 建议字段

- `sourceId`：来源文档或业务对象ID
- `sourceName`：文档名
- `scene`：`tender_review/contract_precheck/risk_alert/knowledge_qa`
- `bizType`：`regulation/template/case/faq`
- `tenantId`（如有多租户）
- `version` 与 `ingestedAt`

这样后续可做按场景、按租户、按文档类型过滤检索。

---

### 5.6 Prompt 与回答约束（关键）

建议给 RAG 模型增加硬约束：

1. 仅基于提供证据回答；证据不足要明确说“不足”。
2. 结论后必须列出引用来源（sourceName + 段落标识）。
3. 禁止编造法规条文号。
4. 对于审查类结论，输出“风险等级 + 依据 + 建议动作”。

可将该约束封装为 `SystemPromptManager` 中新的 RAG 模板，避免散落在业务代码。

---

### 5.7 实施计划（2~3 周）

#### 第 1 阶段（2~3 天）：打通 MVP 链路

- 新增 `KnowledgeController` 两个接口
- 实现 `KnowledgeIngestService`（仅 text 入库）
- 实现 `KnowledgeRagService`（检索 + 生成）
- 跑通本地向量存储读写

**验收**：能入库文本并问答，答案附引用。

#### 第 2 阶段（3~5 天）：接入文档与业务元数据

- 接入 `docx/md` 文档入库
- 将 `tender parse` 结果转为知识片段
- 增加 metadata 过滤与 trace 日志

**验收**：可按场景过滤召回，命中文档可追踪。

#### 第 3 阶段（4~7 天）：融合到现有三大场景

- `CONTRACT_PRECHECK` 接入条款依据检索
- `TENDER_REVIEW` 接入规则解释与历史案例依据
- `RISK_ALERT` 接入预警知识解释

**验收**：场景回答质量稳定提升，可解释性增强。

---

### 5.8 风险与应对

1. **向量库能力上限（SimpleVectorStore）**
   - 风险：数据量变大后召回性能下降。
   - 应对：MVP 后迁移 Milvus/PGVector/Elasticsearch 向量检索。

2. **召回噪声高**
   - 风险：答案引用不相关片段。
   - 应对：加 metadata filter + rerank（第二阶段可加）。

3. **回答“看似合理但证据不足”**
   - 风险：业务误判。
   - 应对：强制“无证据不下结论”模板 + 置信度提示。

4. **入库质量不稳定**
   - 风险：chunk 断句不合理影响召回。
   - 应对：按文档结构（标题、条款、表格）做结构化分块。

---

### 5.9 预期收益

引入 RAG 后，你的 Agent 会从“泛化对话”升级为“可追溯的业务助手”：

- 回答更贴近企业私有知识
- 输出可引用、可审计，降低幻觉风险
- 对合同/标书/风险场景的解释力显著提升
- 为后续多 Agent 协作（检索 Agent、审查 Agent、报告 Agent）打基础

---

### 5.10 本项目推荐落地顺序（最小阻力）

1. 先上线独立 `knowledge/ask` 与 `knowledge/ingest`（不改主链路）
2. 验证检索质量与引用可用性
3. 按场景逐步接入：先 `CONTRACT_PRECHECK`，再 `TENDER_REVIEW`，最后 `RISK_ALERT`
4. 数据量上来后再替换向量引擎

> 这一顺序能确保你在低风险下快速看到业务价值，同时不阻断现有功能迭代。

---

## 五、扩展功能方案：先落地一个“本地 RAG 知识库”

> 目标：先做可用 MVP，不追求极致性能；后续再做索引与检索优化。

### 5.1 业务目标（第一版）

先支持两件事：

1. **知识入库（文本）**：把业务文本（政策条文、合同模板、审查口径）写入本地知识库。
2. **知识问答（检索增强）**：用户提问时先检索相关知识片段，再交给大模型生成回答。

第一版能力边界：
- 仅支持本地单机部署；
- 数据源先从“纯文本输入”开始（文件导入第二阶段再补）；
- 先做同步问答，可选补流式输出；
- 先不做复杂权限隔离（只做最基础 namespace）。

---

### 5.2 推荐技术路线（尽量贴合你当前项目）

结合你现在是 Spring AI + ChatClient 架构，建议直接使用：

- **Embedding 模型**：与当前大模型供应商一致（便于接入和运维）
- **Vector Store（本地）**：
  - MVP 推荐：`SimpleVectorStore`（开发快、门槛低）
  - 稍进阶：本地 `PGVector` / `Milvus Lite`（后续再换）
- **切片策略**：按字符切片（例如 chunk=500~800, overlap=100）
- **检索策略**：Top-K（先 K=4~6）+ 最低相似度阈值

这样能最快把链路跑通：`ingest -> embed -> store -> retrieve -> prompt -> answer`。

---

### 5.3 建议新增模块（应用层结构）

建议在现有 service 旁边新增以下组件，保持“可演进”：

1. `KnowledgeIngestService`
   - 负责文本清洗、切片、向量化、入库。
2. `KnowledgeRetrieveService`
   - 负责按 query 检索 Top-K 片段，返回证据列表。
3. `KnowledgeRagService`
   - 负责拼装 RAG Prompt（问题 + 检索证据）并调用 `AgentChatService/QwenService`。
4. `KnowledgeController`
   - 暴露两个接口：`/knowledge/ingest/text`、`/knowledge/ask`。

配套 DTO（你已预留空文件）：
- `KnowledgeIngestTextReq`
- `KnowledgeAskReq`

---

### 5.4 API 设计（MVP）

#### 1) 文本入库
- `POST /api/knowledge/ingest/text`
- 请求建议字段：
  - `namespace`：知识空间（如 `drug-regulation`）
  - `title`：文档标题
  - `text`：原始文本
  - `source`：来源（可选）
  - `tags`：标签（可选）
- 返回：
  - `documentId`
  - `chunkCount`
  - `status`

#### 2) 检索增强问答
- `POST /api/knowledge/ask`
- 请求建议字段：
  - `namespace`
  - `question`
  - `topK`（可选，默认 4）
  - `minScore`（可选）
  - `sessionId`（可选）
- 返回：
  - `answer`
  - `references`（命中的片段、来源、分数）
  - `traceId`

---

### 5.5 Prompt 组装建议（RAG 场景）

建议增加一个专用 System Prompt，例如 `RAG_COMPLIANCE_ASSISTANT_PROMPT`：

- 明确回答必须“优先依据检索证据”；
- 如果证据不足，要明确说“依据不足”；
- 输出时引用来源编号（如 `[REF-1]`）。

用户问题进入模型前，可拼接结构：

- 用户问题
- 检索证据列表（编号+来源+片段）
- 回答约束（禁止编造、要求引用）

---

### 5.6 数据模型建议（本地）

最小元数据建议保留：

- `docId`
- `namespace`
- `title`
- `chunkIndex`
- `source`
- `tags`
- `createdAt`

价值：后续做“按来源过滤”“按时间过滤”“召回评估”时不用返工。

---

### 5.7 实施计划（先扩展功能，不做重优化）

#### Phase A（0.5~1 天）：跑通链路
1. 建 `KnowledgeController` 两个接口
2. 实现文本切片 + 向量入库你
3. 实现 query 检索 + 拼装 prompt + 调模型
4. 返回引用片段（references）

#### Phase B（1~2 天）：可用性增强
1. 参数校验（`@Valid`）
2. 错误码统一（入库失败/检索为空/模型失败）
3. 增加基础日志与 traceId
4. 增加 namespace 隔离

#### Phase C（后续优化，不急）
1. 文件入库（PDF/DOCX/TXT）
2. 重排（rerank）
3. 缓存热点 query
4. 向量库替换为 PGVector/Milvus

---

### 5.8 与现有 Agent 场景的结合方式

建议先做“旁路集成”，避免影响你现在主链路：

- 新增 `SceneEnum.KNOWLEDGE_QA`（可选）；
- 或在 `DrugAgentService` 中按 `sceneHint=knowledge_qa` 进入 RAG 分支；
- 未命中 RAG 场景时保持原流程不变。

这样可以做到：**先扩展功能，再渐进合并架构**。

---

### 5.9 验收标准（MVP Done Definition）

满足以下 5 条即可认为第一版完成：

1. 能通过接口把一段文本成功入库；
2. 询问与文本相关问题时，答案明显引用了入库内容；
3. 返回中包含 references（至少来源+片段）；
4. 检索为空时有明确兜底文案（而非编造）；
5. 日志可定位一次完整链路（ingest / retrieve / generate）。

---

### 5.10 你当前项目的最小落地建议

结合你仓库现状，下一步可直接做：

1. 先把 `KnowledgeIngestTextReq`、`KnowledgeAskReq` 补全字段与校验；
2. 新增 `KnowledgeController`；
3. 新增 `KnowledgeIngestService`、`KnowledgeRagService`（先不要过度抽象）；
4. 先用本地可跑的向量存储实现 MVP；
5. 跑通后再考虑和 `SceneRouter` 深度融合。

> 结论：你现在非常适合先上一个“本地 RAG MVP”。这条线和性能优化不冲突，且能最快提升产品可用性与差异化。
