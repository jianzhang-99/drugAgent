# RAG 测试策略

## 1. 测试金字塔设计

### 1.1 测试分层占比

```
        ┌─────────────────┐
        │   E2E 测试      │ 占比: 10%
        │  (端到端验证)    │
        ├─────────────────┤
        │   集成测试       │ 占比: 30%
        │  (链路验证)      │
        ├─────────────────┤
        │   接口测试       │ 占比: 20%
        │  (HTTP API)     │
        ├─────────────────┤
        │   单元测试       │ 占比: 40%
        │  (模块级验证)    │
        └─────────────────┘
```

### 1.2 各层测试定义

| 层级 | 占比 | 测试范围 | 技术选型 |
|------|------|----------|----------|
| 单元测试 | 40% | TextExtractor、Chunker、EmbeddingService、HybridSearchService、RerankService | JUnit5 + Mockito |
| 接口测试 | 20% | KnowledgeController（RagController）文件上传/列表/删除/检索 | SpringBootTest + MockMvc |
| 集成测试 | 30% | IngestService、RagService 完整链路；VectorStore 持久化 | SpringBootTest + TestContainers |
| E2E 测试 | 10% | 从上传到检索完整链路；标书审查 + RAG 场景集成 | SpringBootTest |

---

## 2. 测试覆盖矩阵

### 2.1 单元测试（Unit Tests）

#### 2.1.1 TextExtractor

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| UT-TE-001 | TXT文件提取 | 提取纯文本文件 | 返回清洁后的文本内容 |
| UT-TE-002 | MD文件提取 | 提取Markdown文件 | 返回去除多余空格的文本 |
| UT-TE-003 | PDF文件提取 | 提取PDF文件 | 返回PDF文本内容 |
| UT-TE-004 | DOCX文件提取 | 提取Word文件 | 返回段落拼接文本 |
| UT-TE-005 | DOC文件提取 | 提取旧版Word文件 | 返回段落拼接文本 |
| UT-TE-006 | XLSX文件提取 | 提取Excel文件 | 返回表格文本内容 |
| UT-TE-007 | 空文件处理 | 传入空文件 | 抛出 TextExtractionException |
| UT-TE-008 | 异常格式处理 | 传入损坏文件 | 抛出 TextExtractionException |
| UT-TE-009 | 文件名空处理 | 文件名为null | 抛出 IllegalArgumentException |
| UT-TE-010 | 文本清洗验证 | 多余空白字符 | 合并为空格、去除多余换行 |

#### 2.1.2 Chunker

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| UT-CH-001 | 基础切分 | 短文档按段落切分 | 生成单个chunk |
| UT-CH-002 | 多段落切分 | 长文档多段落 | 生成多个chunk |
| UT-CH-003 | Markdown标题识别 | # 一级标题 | 标题保留在chunk中 |
| UT-CH-004 | 数字编号识别 | 1. 2.1 3、编号 | 识别为章节标题 |
| UT-CH-005 | 中文编号识别 | 一、二、（一）编号 | 识别为章节标题 |
| UT-CH-006 | 条款编号识别 | REGULATION类型第X条 | 识别为条款标题 |
| UT-CH-007 | Overlap验证 | 相邻chunk重叠 | overlap在80-150字符范围 |
| UT-CH-008 | 超长段落拆分 | 单段落超chunk大小 | 按句子边界拆分 |
| UT-CH-009 | 句子边界保留 | 拆分点检查 | 不截断句子中间语义 |
| UT-CH-010 | 空文档处理 | 空文本 | 返回空列表 |
| UT-CH-011 | Metadata完整率 | 检查各字段 | 必须字段100%完整 |
| UT-CH-012 | 不同文档类型 | REGULATION/CONTRACT/TENDER | 按类型策略切分 |

#### 2.1.3 EmbeddingService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| UT-ES-001 | 单条embedding | 正常文本 | 返回float[]向量 |
| UT-ES-002 | 空文本处理 | 空白字符串 | 抛出 IllegalArgumentException |
| UT-ES-003 | 批量embedding | 多条文本 | 返回List<float[]>向量列表 |
| UT-ES-004 | 向量维度一致性 | 不同文本 | 向量维度相同 |
| UT-ES-005 | null处理 | 传入null | 抛出 IllegalArgumentException |

#### 2.1.4 HybridSearchService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| UT-HS-001 | 空文档列表 | 传入空列表 | 返回空列表 |
| UT-HS-002 | BM25计算 | 关键词匹配 | 计算正确BM25分数 |
| UT-HS-003 | 向量分数计算 | 向量相似度 | 计算正确余弦相似度 |
| UT-HS-004 | 分数合并 | BM25+向量 | 按权重合并分数 |
| UT-HS-005 | topK限制 | 返回数量 | 最多返回topK条 |
| UT-HS-006 | 归一化验证 | 分数范围 | 归一化到0-1 |

#### 2.1.5 RerankService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| UT-RS-001 | 空列表处理 | 传入空列表 | 返回空列表 |
| UT-RS-002 | 数量不足topK | chunk数量小于topK | 返回全部chunks |
| UT-RS-003 | 相关性计算 | 查询词覆盖率 | 计算覆盖率分数 |
| UT-RS-004 | 多样性计算 | 同一sourceId去重 | 多样性分数正确 |
| UT-RS-005 | 综合评分 | 相关性+多样性 | 0.7*相关性+0.3*多样性 |
| UT-RS-006 | topK排序 | 取前K条 | 返回排序后topK |

---

### 2.2 集成测试（Integration Tests）

#### 2.2.1 IngestService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| IT-IS-001 | 完整入库链路 | 文件→提取→切分→embedding→存储 | 成功入库，chunk数量正确 |
| IT-IS-002 | 批量入库 | 多个文档 | 全部入库成功 |
| IT-IS-003 | 按sourceId删除 | 删除指定文档 | 向量库中该文档chunks被删除 |
| IT-IS-004 | 空文档处理 | 文本为空 | 返回chunk数量0 |
| IT-IS-005 | 幂等删除 | 删除不存在sourceId | 不报错 |

#### 2.2.2 RagService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| IT-RS-001 | 基础检索 | orgId+question | 返回相关chunks |
| IT-RS-002 | scene过滤 | 指定scene检索 | 仅返回该scene chunks |
| IT-RS-003 | 相似度阈值 | 设置threshold | 过滤低相似度结果 |
| IT-RS-004 | topK限制 | 设置topK=3 | 最多返回3条 |
| IT-RS-005 | 混合检索模式 | enableHybridSearch=true | BM25+向量合并结果 |
| IT-RS-006 | 重排模式 | enableRerank=true | Relevance+Diversity重排 |
| IT-RS-007 | 无结果处理 | 检索无命中 | 返回NO_HIT |
| IT-RS-008 | LLM生成回答 | needGenerateAnswer=true | 调用LLM并返回回答 |
| IT-RS-009 | 仅检索模式 | needGenerateAnswer=false | 仅返回chunks |
| IT-RS-010 | orgId必填校验 | orgId为空 | 返回CONTEXT_MISSING |
| IT-RS-011 | question必填校验 | question为空 | 返回NO_CANDIDATE |

#### 2.2.3 VectorStore（PGVector）

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| IT-VS-001 | 连接验证 | PGVector连接 | 连接成功 |
| IT-VS-002 | 插入持久化 | 插入向量 | 重启后仍可查询 |
| IT-VS-003 | 按过滤条件删除 | Filter表达式删除 | 仅删除匹配向量 |
| IT-VS-004 | 向量数量统计 | 统计向量数 | 数量正确 |

---

### 2.3 接口测试（Interface Tests）

#### 2.3.1 RagController / KnowledgeController

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| ITF-001 | 文件上传入库 | POST /knowledge/ingest/file | 返回sourceId，入库成功 |
| ITF-002 | COS文件入库 | POST /knowledge/ingest/oss | 幂等检查，已入库则跳过 |
| ITF-003 | 文本入库 | POST /knowledge/ingest/text | 返回sourceId |
| ITF-004 | 知识问答 | POST /knowledge/ask | 返回回答和引用 |
| ITF-005 | 仅检索 | POST /knowledge/search | 仅返回chunks，无LLM回答 |
| ITF-006 | 文件列表 | GET /knowledge/files | 返回文件列表 |
| ITF-007 | 文件删除 | DELETE /knowledge/files/{ossId} | 软删除成功 |
| ITF-008 | 持久化 | POST /knowledge/persist | 调用成功 |
| ITF-009 | orgId为空 | 缺少orgId参数 | 返回错误信息 |
| ITF-010 | 文件格式校验 | 上传不支持格式 | 返回格式错误 |

---

### 2.4 E2E 测试（End-to-End Tests）

| 测试编号 | 测试场景 | 测试内容 | 预期结果 |
|----------|----------|----------|----------|
| E2E-001 | 完整入库检索链路 | 上传文件→入库→检索→获取回答 | 全链路成功 |
| E2E-002 | 标书审查+RAG集成 | 标书审查命中→RAG检索法规证据 | EvidenceGroup正确组装 |
| E2E-003 | 多文档批量入库 | 批量上传→批量检索 | 检索命中正确文档 |
| E2E-004 | 删除后检索 | 删除文档→检索该文档内容 | 不再被检索到 |
| E2E-005 | 混合检索+Rerank | 启用混合检索和重排 | 结果质量优于单一检索 |

---

### 2.5 场景集成测试（Scene Integration Tests）

#### 2.5.1 TenderReviewRagService

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| SI-TR-001 | orgId为空跳过 | 无orgId参数 | 返回SKIPPED |
| SI-TR-002 | 无风险命中跳过 | effectiveHits为空 | 返回SKIPPED |
| SI-TR-003 | W-M1规则检索 | 报价梯度异常 | 构造定价问题检索 |
| SI-TR-004 | W-M2规则检索 | 联系方式近邻 | 构造联系人问题检索 |
| SI-TR-005 | W-M3规则检索 | 团队成员重叠 | 构造团队问题检索 |
| SI-TR-006 | 证据组合 | 多个风险类型 | 合并为EvidenceGroup |
| SI-TR-007 | 去重验证 | 相同riskType多次命中 | 仅执行一次检索 |
| SI-TR-008 | 异常降级 | RAG检索异常 | 降级返回，不阻塞主流程 |

#### 2.5.2 KnowledgeRetrievalTool

| 测试编号 | 测试点 | 测试内容 | 预期结果 |
|----------|--------|----------|----------|
| SI-KT-001 | 基础检索 | retrieve(question, orgId, scene) | 返回RagOutcome |
| SI-KT-002 | orgId为空 | orgId为null | 返回NO_HIT |
| SI-KT-003 | question为空 | question为blank | 返回NO_CANDIDATE |
| SI-KT-004 | 仅检索模式 | search(...) | 不调用LLM生成 |
| SI-KT-005 | 异常捕获 | RAG服务异常 | 返回NEED_HUMAN_REVIEW |

---

## 3. 测试数据准备方案

### 3.1 测试文档库

#### 3.1.1 法规类文档

| 文档类型 | 文件名 | 字符数 | 章节结构 | 用途 |
|----------|--------|--------|----------|------|
| REGULATION | 招标投标法.md | ~5000 | 一级标题+条款 | 基础检索测试 |
| REGULATION | 药品管理法.md | ~8000 | 多级标题 | 混合检索测试 |
| REGULATION | 政府采购法.md | ~6000 | 条款式 | 重排测试 |

#### 3.1.2 标书类文档

| 文档类型 | 文件名 | 特征 | 用途 |
|----------|--------|------|------|
| TENDER | 投标书A.docx | 报价部分 | 标书审查RAG测试 |
| TENDER | 投标书B.docx | 报价部分 | 围标风险测试 |
| TENDER | 技术方案.md | 完整标书结构 | E2E测试 |

#### 3.1.3 合同类文档

| 文档类型 | 文件名 | 条款数 | 用途 |
|----------|--------|--------|------|
| CONTRACT | 服务合同.md | 12条 | 条款切分测试 |
| CONTRACT | 采购合同.doc | 8条 | DOC格式测试 |

#### 3.1.4 边界测试文档

| 文档类型 | 文件名 | 特征 | 用途 |
|----------|--------|------|------|
| EMPTY | 空文档.txt | 0字节 | 空处理测试 |
| CORRUPT | 损坏文件.pdf | PDF结构损坏 | 异常处理测试 |
| LARGE | 大文件.md | >10MB | 大文档处理测试 |
| CHINESE | 中文文档.txt | 全中文 | 中文分词测试 |

### 3.2 测试查询用例

#### 3.2.1 基础检索查询

| 查询ID | 查询文本 | 预期命中文档 | 预期结果数 |
|--------|----------|--------------|------------|
| Q-001 | 什么是串通投标 | 招标投标法 | >=1 |
| Q-002 | 药品注册需要什么条件 | 药品管理法 | >=1 |
| Q-003 | 围标的法律后果 | 招标投标法 | >=1 |

#### 3.2.2 场景检索查询

| 查询ID | 查询文本 | 场景 | 预期结果 |
|--------|----------|------|----------|
| Q-S-001 | 报价异常接近如何认定串通投标 | tender_review | 命中定价相关法规 |
| Q-S-002 | 联系方式关联与串通投标 | tender_review | 命中联系人相关法规 |
| Q-S-003 | 项目团队重叠是否构成围标 | tender_review | 命中团队相关法规 |

#### 3.2.3 边界查询

| 查询ID | 查询文本 | 特征 | 预期结果 |
|--------|----------|------|----------|
| Q-B-001 | "" | 空字符串 | 返回错误 |
| Q-B-002 | 仅空格 | 空白字符串 | 返回错误 |
| Q-B-003 | 未知主题xyz | 无相关文档 | NO_HIT |

### 3.3 Mock 数据策略

| 数据类型 | Mock方案 | 使用场景 |
|----------|----------|----------|
| EmbeddingModel | Mockito mock | 单元测试 |
| VectorStore | Mockito mock | 单元测试、接口测试 |
| LlmService | Mockito mock + 固定回答 | 集成测试 |
| CosStorageService | 本地临时文件 | 集成测试 |
| 百炼API | Testcontainers + MockServer | E2E测试（可选） |

---

## 4. 测试执行计划

### 4.1 Phase 1：单元测试（Week 1-2）

**目标**：完成所有模块的单元测试

**执行顺序**：
1. TextExtractor 单元测试（10个用例）
2. Chunker 单元测试（12个用例）
3. EmbeddingService 单元测试（5个用例）
4. HybridSearchService 单元测试（6个用例）
5. RerankService 单元测试（6个用例）

**入口标准**：
- 所有单元测试通过
- 覆盖率达成：核心模块 > 80%

**技术要求**：
```java
// TextExtractor 测试示例结构
@ExtendWith(MockitoExtension.class)
class TextExtractorTest {
    @Mock
    private PdfTextExtractor pdfTextExtractor;

    private TextExtractor textExtractor;

    @BeforeEach
    void setUp() {
        textExtractor = new TextExtractor(pdfTextExtractor);
    }

    @Test
    void shouldExtractTextFromTxtFile() { ... }
}
```

### 4.2 Phase 2：接口测试（Week 2-3）

**目标**：完成 RagController 所有接口的测试

**执行顺序**：
1. 文件上传入库接口（ITF-001, ITF-010）
2. 知识问答接口（ITF-004, ITF-009）
3. 文件列表接口（ITF-006）
4. 文件删除接口（ITF-007）
5. COS文件入库接口（ITF-002）
6. 文本入库接口（ITF-003）

**入口标准**：
- 所有接口测试通过
- HTTP状态码正确
- 响应结构符合 Result<T> 规范

### 4.3 Phase 3：集成测试（Week 3-4）

**目标**：完成完整链路的集成测试

**执行顺序**：
1. IngestService 链路测试（IT-IS-001 ~ IT-IS-005）
2. RagService 检索链路测试（IT-RS-001 ~ IT-RS-011）
3. VectorStore 持久化测试（IT-VS-001 ~ IT-VS-004）
4. 混合检索与重排集成测试

**入口标准**：
- 集成测试使用 TestContainers 启动真实 PGVector
- 完整链路可运行
- 持久化验证通过

**技术要求**：
```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IngestServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    void shouldIngestDocumentAndRetrieve() { ... }
}
```

### 4.4 Phase 4：E2E 测试（Week 4-5）

**目标**：完成全链路和场景集成测试

**执行顺序**：
1. 文件上传→检索完整链路（E2E-001）
2. 标书审查+RAG场景集成（E2E-002, SI-TR-001 ~ SI-TR-008）
3. 批量入库检索（E2E-003）
4. 删除后检索验证（E2E-004）
5. 混合检索+Rerank质量验证（E2E-005）

**入口标准**：
- E2E 测试可独立运行
- 标书审查 RAG 证据正确组装
- 场景集成测试验证 TenderReviewRagService 逻辑

### 4.5 Phase 5：回归测试（持续）

**触发条件**：
- 代码提交后自动触发
- 每周五下午执行全量测试

**执行内容**：
- Phase 1-4 全部测试用例
- 生成测试报告
- 覆盖率统计

---

## 5. 测试环境配置

### 5.1 测试配置文件

| 环境 | 配置文件 | 用途 |
|------|----------|------|
| 本地开发 | application-test.yml | 本地单元测试 |
| CI | application-ci.yml | GitHub Actions |
| 集成测试 | application-integration.yml | TestContainers |

### 5.2 测试依赖服务

| 服务 | 版本 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL + PGVector | pg16 | 5432 | 集成测试 |
| Redis | 7.x | 6379 | 可选缓存测试 |
| 百炼API | - | - | Mock或Test Server |

---

## 6. 测试质量指标

### 6.1 覆盖率目标

| 模块 | 行覆盖率目标 | 关键路径覆盖率 |
|------|--------------|----------------|
| TextExtractor | > 90% | 100% |
| Chunker | > 85% | 100% |
| EmbeddingService | > 80% | 100% |
| HybridSearchService | > 80% | 100% |
| RerankService | > 80% | 100% |
| IngestService | > 85% | 100% |
| RagService | > 80% | 100% |
| TenderReviewRagService | > 90% | 100% |

### 6.2 测试用例数量规划

| 层级 | 用例数量 | 负责人 |
|------|----------|--------|
| 单元测试 | ~50 | 各模块开发者 |
| 接口测试 | ~20 | 测试工程师 |
| 集成测试 | ~25 | 测试工程师 |
| E2E 测试 | ~10 | 测试工程师 |
| 场景集成测试 | ~15 | 测试工程师 |
| **合计** | **~120** | - |

---

## 7. 附录

### 7.1 测试类命名规范

```
src/test/java/com/liang/drugagent/
├── shared/rag/
│   ├── service/
│   │   ├── TextExtractorTest.java          # 单元测试
│   │   ├── ChunkerTest.java               # 单元测试
│   │   ├── EmbeddingServiceTest.java       # 单元测试
│   │   ├── HybridSearchServiceTest.java    # 单元测试
│   │   ├── RerankServiceTest.java         # 单元测试
│   │   ├── IngestServiceIntegrationTest.java   # 集成测试
│   │   └── RagServiceIntegrationTest.java      # 集成测试
│   └── controller/
│       └── RagControllerTest.java         # 接口测试
├── scene/tender_review/
│   └── service/
│       ├── TenderReviewRagServiceTest.java # 单元测试
│       └── TenderReviewRagE2ETest.java     # E2E测试
└── e2e/
    └── RagFullChainE2ETest.java            # E2E测试
```

### 7.2 测试数据文件位置

```
src/test/resources/
├── test-documents/
│   ├── regulation/
│   │   ├── 招标投标法.md
│   │   └── 药品管理法.md
│   ├── tender/
│   │   ├── 投标书A.txt
│   │   └── 投标书B.txt
│   └── empty/
│       └── 空文档.txt
├── test-queries/
│   └── test-queries.yml
└── fixtures/
    └── mock-responses/
```

### 7.3 已知限制

1. **百炼API Mock**：E2E 测试阶段使用 Mock Server 模拟，避免调用真实 API 产生费用
2. **PGVector 版本**：测试使用 `pgvector/pgvector:pg16`，需确保 CI 环境版本一致
3. **大文档测试**：超过 10MB 的文档测试在 CI 环境可能超时，需设置 `@Timeout` 注解
4. **中文分词**：当前 Chunker 使用简单正则分词，复杂中文场景（如法律条文）可能需要专业分词器增强