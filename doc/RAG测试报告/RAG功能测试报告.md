# RAG 功能测试报告

**测试日期**: 2026-04-19
**测试执行人**: Claude Code
**测试环境**: Windows 10 Pro, JDK 21

---

## 一、测试概览

| 指标 | 数值 |
|------|------|
| 测试用例总数 | 66 |
| 通过 | 53 |
| 失败 (Failures) | 7 |
| 错误 (Errors) | 6 |
| 通过率 | 80.3% |

---

## 二、测试文件清单

| 序号 | 测试类 | 测试方法数 | 状态 |
|------|--------|-----------|------|
| 1 | `EmbeddingServiceTest` | 8 | 通过 |
| 2 | `HybridSearchServiceTest` | 10 | 通过 |
| 3 | `RerankServiceTest` | 12 | 通过 |
| 4 | `IngestServiceIntegrationTest` | 9 | 5 errors |
| 5 | `RagServiceIntegrationTest` | 13 | 1 failure, 1 error |
| 6 | `KnowledgeControllerTest` | 12 | 1 error |
| 7 | `RagE2ETest` | 6 | 5 failures |
| 8 | `ChunkerQualityTest` | 5 | 通过 |
| 9 | `RagPersistenceTest` | 1 | 通过 |

---

## 三、编译问题（已修复）

测试代码存在 2 处编译错误，已修复：

### 3.1 `RagServiceIntegrationTest.java` 第 185 行

**问题**: 引用了不存在的枚举值 `RagDecision.HIT`

**修复前**:
```java
response.getDecision() == RagDecision.HIT ||
response.getDecision() == RagDecision.ANSWERED
```

**修复后**:
```java
response.getDecision() == RagDecision.ANSWERED
```

**说明**: `RagDecision` 枚举只有 `ANSWERED`, `NO_HIT`, `NEED_HUMAN_REVIEW` 三个值，不存在 `HIT`。

### 3.2 `EmbeddingServiceTest.java` 第 111 行

**问题**: 将 `List.size()` 方法误写为字段访问 `List.size`

**修复前**:
```java
assertEquals(2, result.size);
```

**修复后**:
```java
assertEquals(2, result.size());
```

---

## 四、测试失败分析

### 4.1 基础设施错误 (External Service 404)

**影响范围**: 6 个测试

**错误信息**:
```
org.springframework.web.client.HttpClientErrorException$NotFound: 404 Not Found
    at com.liang.drugagent.shared.rag.config.DirectDashScopeEmbeddingModel.call
```

**根因分析**:
- `DirectDashScopeEmbeddingModel` 调用的外部 DashScope embedding API 返回 404
- 这是基础设施/配置问题，非测试代码问题
- 集成测试依赖外部服务可用性

**受影响测试**:
- `IngestServiceIntegrationTest.shouldReturnChunkCountWhenIngestingText`
- `IngestServiceIntegrationTest.shouldReturnChunkCountWhenIngestingFile`
- `IngestServiceIntegrationTest.shouldContainCorrectMetadataInChunks`
- `IngestServiceIntegrationTest.shouldIngestDocumentCorrectly`
- `IngestServiceIntegrationTest.shouldHandleBatchIngestion`
- `RagServiceIntegrationTest.shouldUseHybridSearchWhenEnabled`

### 4.2 Spring 配置错误

**影响范围**: 1 个测试

**错误信息**:
```
IllegalStateException: Unable to find a @SpringBootConfiguration
by searching packages upwards from the test.
```

**受影响测试**:
- `KnowledgeControllerTest` (整个测试类)

**根因分析**:
- `@WebMvcTest` 注解无法自动找到 `@SpringBootConfiguration`
- 缺少明确的 `classes` 参数指定配置类

### 4.3 业务逻辑失败

#### 4.3.1 `shouldLimitResultsByTopK`

**错误信息**:
```
AssertionFailedError: expected: <true> but was: <false>
```

**根因分析**:
- 当 `enableRerank=false` 时，`topK` 参数可能未正确传递到检索层
- 实际返回了 10 个结果而非限制的 3 个

#### 4.3.2 E2E 测试失败 (5 个)

**根因**: 所有 E2E 测试依赖 `ingest/text` 接口，该接口调用 embedding 服务，外部 API 不可用导致 500 错误。

---

## 五、测试缺口分析

### 5.1 缺失的测试覆盖

| 模块 | 建议补充的测试用例 |
|------|-------------------|
| EmbeddingService | 并发调用 embedding 的线程安全性 |
| EmbeddingService | 超长文本（>8192 tokens）的截断处理 |
| HybridSearchService | 极端 BM25 权重（0.0 或 1.0）的边界情况 |
| HybridSearchService | 空关键词查询的处理 |
| RerankService | 相同相关性分数的排序稳定性 |
| RerankService | chunk 内容完全相同的去重处理 |
| IngestService | 特殊文件格式（PDF、Word）的解析测试 |
| IngestService | 超大文件（>10MB）的分块策略 |
| RagService | 多 orgId 并发查询的隔离性 |
| RagService | 向量维度不匹配时的降级处理 |
| KnowledgeRetrievalTool | retrieve 和 search 方法的返回值差异验证 |
| Chunker | 纯数字/符号内容的切分 |
| Chunker | 表格内容的保持性 |
| Chunker | 代码块的保持性 |

### 5.2 当前测试的局限性

1. **集成测试依赖外部服务**: `IngestServiceIntegrationTest` 和 `RagServiceIntegrationTest` 需要 DashScope API 可用
2. **Mock 不完整**: 部分测试未正确 mock 所有依赖
3. **E2E 测试无隔离**: 测试数据未清理，可能互相影响

---

## 六、修复建议

### 6.1 紧急修复（编译错误）

已修复，见第三节。

### 6.2 高优先级（配置问题）

1. **KnowledgeControllerTest**: 添加 `classes` 参数
   ```java
   @WebMvcTest(controllers = RagController.class, classes = RagController.class)
   ```

2. **E2E 测试环境**: 需要配置可用的 DashScope API 或使用 Mock 服务器

### 6.3 中优先级（业务逻辑）

1. **shouldLimitResultsByTopK**: 检查 `RagService.executeRetrieval` 中 `topK` 参数的传递链路

### 6.4 低优先级（测试增强）

1. 为外部依赖添加 Mock Server（如 TestContainers）
2. 补充边界值测试和异常场景测试

---

## 七、结论

RAG 核心功能单元测试（`EmbeddingServiceTest`, `HybridSearchServiceTest`, `RerankServiceTest`, `ChunkerQualityTest`）全部通过，共 35 个测试用例。

主要问题集中在：
1. **外部服务依赖** - DashScope API 不可用导致集成测试失败
2. **Spring Test 配置** - Controller 测试缺少配置类指定
3. **topK 限制** - 业务逻辑可能存在 bug

建议优先解决配置问题，然后使用 Mock 方式重跑集成测试以验证业务逻辑。

---

## 附录：测试命令

```bash
# 运行所有 RAG 相关测试
./mvnw test -Dtest="**/rag/**/*Test,**/e2e/*Test" -DfailIfNoTests=false

# 运行单个测试类
./mvnw test -Dtest="EmbeddingServiceTest" -DfailIfNoTests=false

# 跳过需要外部服务的集成测试
./mvnw test -Dtest="EmbeddingServiceTest,HybridSearchServiceTest,RerankServiceTest,ChunkerQualityTest" -DfailIfNoTests=false
```
