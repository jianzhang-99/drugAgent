# drug-agent

AI 药品监管智能体后端服务。基于 Spring Boot + Spring AI Alibaba（通义千问），提供多场景路由、文档解析与合规风险识别能力。

---

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring AI Alibaba | 1.0.0-M6.1 |
| DashScope SDK | 2.19.1 |
| Apache POI (OOXML) | 5.2.5 |
| Knife4j (Swagger UI) | 4.4.0 |
| Hutool | 5.8.29 |
| Lombok | latest |

---

## 启动

### 前置条件

1. Java 21+
2. 阿里云百炼 DashScope API Key

### 配置 API Key

本地开发创建 `src/main/resources/application-local.yml`：

```yaml
aliyun:
  dashscope:
    api-key: sk-xxxxxx   # 替换为真实 Key
```

或通过环境变量：

```bash
export DASHscope_API_KEY=sk-xxxxxx
```

### 运行

```bash
mvn spring-boot:run
```

服务启动后监听 `http://localhost:8124`，所有接口统一前缀 `/api`。

### Swagger UI

```
http://localhost:8124/api/swagger-ui.html
```

---

## 项目结构

```
src/main/java/com/liang/drugagent/
├── DrugAgentApplication.java          # 启动类
│
├── controller/
│   └── DrugAgentController.java       # 统一对话入口（普通 + SSE 流式）
│
├── agent/
│   ├── SceneRouter.java               # 场景路由器（关键词 + hint 路由）
│   ├── WorkflowRegistry.java          # 工作流注册表
│   └── AgentContext.java              # 请求上下文
│
├── workflow/                          # 各场景工作流实现
│   ├── TenderReviewWorkflow.java      # 标书审查（MVP 占位）
│   ├── ContractPrecheckWorkflow.java  # 合同预审
│   ├── RiskAlertWorkflow.java         # 风险预警
│   └── FallbackWorkflow.java          # 兜底
│
├── service/
│   ├── DrugAgentService.java          # 顶层 Agent 编排
│   ├── AgentChatService.java          # 模型对话封装（含 Advisor 链）
│   └── QwenService.java               # 单轮对话工具
│
├── advisor/                           # Spring AI Advisor 链
│   ├── LoggingAdvisor.java
│   ├── PromptAdvisor.java
│   ├── ReReadingAdvisor.java
│   └── SafetyAdvisor.java
│
├── config/
│   ├── AiConfiguration.java           # ChatMemory 配置
│   ├── ChatClientConfig.java
│   └── VectorStoreConfig.java         # SimpleVectorStore（内存向量库）
│
├── domain/ enums/ prompt/             # 通用领域对象、枚举、Prompt 管理
│
└── tenderreview/                      # 标书审查独立模块（7.1 + 7.2）
    ├── controller/
    │   └── TenderReviewController.java
    ├── domain/
    │   ├── enums/  CaseStatus, BlockType
    │   ├── Anchor, SectionNode, Block
    │   ├── Case, CaseDocument
    │   ├── ExtractedField, ExtractionMeta
    │   └── CaseCreateRequest, CaseCreateResponse
    ├── storage/
    │   └── InMemoryCaseStore.java     # ConcurrentHashMap 内存存储
    ├── service/
    │   ├── CaseService.java           # 7.1 任务接收
    │   └── DocumentParseService.java  # 7.2 解析编排
    └── parser/
        ├── DocxParser.java            # Apache POI XWPF 解析核心
        └── DocumentParseResult.java   # 解析结果对象
```

---

## 场景路由

`POST /api/agent/drug/chat` 接收请求后，`SceneRouter` 按以下优先级路由：

| 优先级 | 规则 | 目标场景 |
|---|---|---|
| 1 | 请求携带 `sceneHint` 字段 | 直接命中对应场景 |
| 2 | 请求携带 `fileIds` | `CONTRACT_PRECHECK` |
| 3 | 关键词匹配（标书/投标/串标/围标…） | `TENDER_REVIEW` |
| 3 | 关键词匹配（合同/协议/条款/预审…） | `CONTRACT_PRECHECK` |
| 3 | 关键词匹配（药品/耗材/预警/异常…） | `RISK_ALERT` |
| 4 | 无匹配 | `UNKNOWN` → FallbackWorkflow |

---

## 接口说明

### 通用对话

```
POST /api/agent/drug/chat
Content-Type: application/json

{
  "query": "这份标书与上一份高度雷同，帮我分析一下",
  "sessionId": "session-001",
  "sceneHint": "TENDER_REVIEW"   // 可选
}
```

```
POST /api/agent/drug/chat/stream   // SSE 流式输出
```

### 标书审查专用接口（tenderreview 模块）

#### 上传标书文件，创建审查任务

```
POST /api/tender-review/cases
Content-Type: multipart/form-data

files:       [file_a.docx, file_b.docx]   // 至少 2 份，仅支持 .docx
submittedBy: anonymous                     // 可选，默认 anonymous
```

响应（201）：

```json
{
  "caseId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "PENDING",
  "documentIds": ["doc-id-a", "doc-id-b"],
  "message": "任务创建成功，待解析文档数：2"
}
```

错误（400）：少于 2 份文件 / 非 docx 格式

#### 解析指定文档

```
POST /api/tender-review/cases/{caseId}/parse/{docId}
```

响应（200）：

```json
{
  "docId": "doc-id-a",
  "sectionTree": [...],
  "paragraphBlocks": [...],
  "tableBlocks": [...],
  "fields": [...],
  "extractionMeta": {
    "schemaVersion": "tender-review-struct-v1",
    "parserVersion": "v1.0.0",
    "parseSuccess": true
  }
}
```

---

## 文档解析能力（DocxParser）

使用 Apache POI XWPF 遍历文档 body elements，按顺序处理：

- **段落块**：提取文本、规范化空白、检测章节标题（中文数字开头）、构建章节树
- **表格块**：提取整表文本、同时将单元格段落也纳入段落块列表
- **锚点**：每个块携带 `paragraphIndex` / `tableIndex` / `paragraphNo` / `tableNo`

### 字段提取规则（V1 正则驱动）

| 字段类型 | 规则 | normalizedKey 格式 |
|---|---|---|
| `contact_phone` | `1[3-9]\d{9}` | `phone:13812345678` |
| `contact_email` | 标准邮箱正则 | `email:xxx@example.com` |
| `bid_price` | 数字 + 元/万/¥ | `quote_total:8865000` |
| `team_member` | 角色：姓名 模式 | `person:张三` |

提取结果包含 `fieldId`、`blockId`（可回溯原文）、`normalizedKey`（用于跨文档比对）、`confidence`。

---

## 数据结构对接（下游模块）

解析结果遵循 `数据结构对接文档.md` 定义的 schema，输出以下核心字段：

| 字段 | 说明 |
|---|---|
| `blocks` | 原文证据块，含 blockId / chapterPath / anchor |
| `fields` | 结构化字段，含 normalizedKey / confidence，供规则引擎消费 |
| `extractionMeta` | schemaVersion + parserVersion + parseSuccess |

`case` / `documents` / `compareScopes` 由任务层（`CaseService`）在任务级别组装，供后续 7.4+ 阶段使用。

---

## 测试

```bash
# 全量测试
mvn test

# 仅 tenderreview 模块测试
mvn test -Dtest="InMemoryCaseStoreTest,CaseServiceTest,DocxParserTest,DocumentParseServiceTest,TenderReviewControllerTest"
```

当前测试覆盖：

| 测试类 | 用例数 | 说明 |
|---|---|---|
| `InMemoryCaseStoreTest` | 8 | 内存存储 CRUD |
| `CaseServiceTest` | 12 | 任务接收校验、存储 |
| `DocxParserTest` | 35 | 解析逻辑 + 真实样本文件集成测试 |
| `DocumentParseServiceTest` | 2 | 编排服务 |
| `TenderReviewControllerTest` | 4 | `@WebMvcTest` 接口测试 |

> 测试依赖 `src/test/resources/application.yml` 提供虚拟 API Key，`TestAiConfig` 提供 mock ChatModel/EmbeddingModel，无需真实 DashScope 连接。

---

## 阶段规划

| 阶段 | 状态 | 内容 |
|---|---|---|
| 7.1 任务接收 | ✅ **已完成** | 文件上传校验、caseId 生成、任务初始化 |
| 7.2 文档解析 | ✅ **已完成** | DOCX 解析、blocks 提取、fields 字段提取、extractionMeta |
| 7.3 结构化提取 | 📋 待开发 | LLM 辅助深度字段提取 |
| 7.4 围标规则命中 | 📋 待开发 | 跨文档 normalizedKey 比对 |
| 7.5 误报豁免 | 📋 待开发 | 章节权重、豁免规则 |
| 7.6 风险融合 | 📋 待开发 | 多维度风险评分 |
| 7.7 证据编排 | 📋 待开发 | blockId 回溯、证据链生成 |
| 7.8 报告生成 | 📋 待开发 | 结构化风险报告输出 |

---

## 当前状态（2026-03-18）

### ✅ 已完成

**7.1 任务接收**
- 接口：`POST /api/tender-review/cases`
- 功能：上传 2+ 份 .docx 文件，生成 caseId 和 documentIds
- 校验：文件数量、格式、文件名非空
- 存储：内存存储（`InMemoryCaseStore`）
- 测试：12 个单元测试 + 4 个接口测试，全部通过

**7.2 文档解析**
- 接口：`POST /api/tender-review/cases/{caseId}/parse/{docId}`
- 功能：
  - 段落块提取（100+ 块/文档）
  - 表格块提取（5 块/文档）
  - 章节树构建（7 个一级章节）
  - 字段提取（手机号、邮箱、报价、人员，正则驱动）
  - extractionMeta（schema v1, parser v1.0.0）
- 测试：35 个解析测试 + 2 个编排测试，全部通过

**Swagger UI 集成**
- 所有接口已注册到 Swagger UI（`http://localhost:8124/api/swagger-ui.html`）
- 标书审查分组独立展示
- 7.1 接口支持文件上传表单（多文件选择）
- 完整的 API 文档（summary、description、responses）

### 📋 待办事项

**短期（7.3 结构化提取）**
- [ ] 设计 LLM prompt 模板，提取深度字段（项目名称、投标单位、联系人、技术方案摘要等）
- [ ] 实现 `StructuredExtractionService`，调用 QwenService
- [ ] 将提取结果合并到 `DocumentParseResult.fields`
- [ ] 编写单元测试，验证提取准确率

**中期（7.4 围标规则命中）**
- [ ] 实现跨文档 `normalizedKey` 比对逻辑
- [ ] 定义围标规则（手机号重复、邮箱重复、报价接近、人员重叠等）
- [ ] 输出规则命中结果（ruleId、matchedDocIds、evidence）

**长期（7.5-7.8）**
- [ ] 误报豁免机制（章节权重、白名单）
- [ ] 风险评分融合（多维度加权）
- [ ] 证据链生成（blockId 回溯）
- [ ] 结构化报告输出（JSON/PDF）

### ⚠️ 已知问题

1. **字段提取边界情况**：章节标题"六、投标报价汇总"被误命中为 `bid_price`，confidence=0.6（可通过阈值过滤）
2. **内存存储限制**：当前使用 `ConcurrentHashMap`，重启后数据丢失，生产环境需替换为持久化存储
3. **文件大小限制**：未设置上传文件大小限制，需在 `application.yml` 配置 `spring.servlet.multipart.max-file-size`

---

## 最近更新

**2026-03-18**
- 修复 Swagger UI 文件上传显示问题（`@ArraySchema` + `items.format=binary`）
- 扩展 `packages-to-scan` 使标书审查接口出现在 Swagger UI
- Java 版本对齐为 21
- 手动测试 7.1/7.2 全部功能点通过（7 个测试用例）

---

## 开发约束

- **现有 Java 源文件不可修改**（`DrugAgentApplication` 等冻结文件）
- 所有新功能在独立包（`tenderreview.*`）中实现
- 新功能与现有代码通过 Spring 自动注入集成
- 每个功能点遵循 设计 → 开发 → 测试 → 测试通过 四关卡制度
- `pom.xml` 和 `application.yml` 允许追加，不做其他改动
