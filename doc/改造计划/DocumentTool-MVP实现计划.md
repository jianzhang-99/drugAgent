# DocumentTool MVP 实现计划

> 文档版本：v1.0
> 更新时间：2026-03-30
> 目标：在不落库存储文件的前提下，提供一个可被标书、合同等场景复用的最小文档工具

## 1. 改造背景

当前项目已经明确采用多场景 Agent 架构，标书审查、合同预审等场景都会涉及文件上传与文档解析。

但当前阶段的实际目标不是建设完整的文件中心，也不是提前抽象复杂的通用中台，而是：

**先提供一个最小可用的通用文档工具，让不同场景都能基于本次请求上传的临时文件完成后续 workflow 分析。**

本阶段明确约束如下：

1. 文件只在当前请求内使用
2. 不做原文件持久化
3. 不做解析结果持久化
4. 不做通用 compare / analyze 平台化设计
5. 不在 tool 层输出任何场景结论

---

## 2. 改造目标

本次只做一件事：

**在 `src/main/java/com/liang/drugagent/tool` 下实现一个最小文档工具 `DocumentTool`，负责将上传文件解析为统一的 `ParsedDocument` 列表，供场景层继续进入 PreparationService 和 Workflow。**

---

## 3. 设计原则

### 3.1 单一职责

`DocumentTool` 只负责：

1. 接收本次请求中的临时文件
2. 按文件类型选择解析器
3. 输出统一文档结构

它不负责：

1. 文件存储
2. 文件权限管理
3. 场景路由
4. 标书风险判断
5. 合同风险判断
6. 通用比对引擎

### 3.2 一次性请求内使用

本阶段文件是临时输入，不设计 `fileId` 持久化体系，不引入文件中心，不增加对象存储依赖。

### 3.3 场景结论下沉到 Workflow

`tool` 层产出的是标准化文档结果，不是业务结论。

例如：

- `DocumentTool` 输出 `ParsedDocument`
- `TenderReviewPreparationService` 负责组装 `TenderReviewData`
- `TenderReviewWorkflow` 负责输出围标风险结论

### 3.4 先跑通，再扩展

当前只保留最小字段和最小目录结构，避免提前设计复杂的通用文档中台。

---

## 4. 目标结构

建议在 `src/main/java/com/liang/drugagent/tool` 下保留如下最小结构：

```text
tool
└── document
    ├── DocumentTool.java
    ├── DocumentToolReq.java
    ├── DocumentToolResult.java
    ├── TempDocument.java
    ├── ParsedDocument.java
    ├── DocumentParser.java
    ├── DocxDocumentParser.java
    ├── DocDocumentParser.java
    ├── MarkdownDocumentParser.java
    └── support
        └── DocumentTextNormalizer.java
```

说明：

1. 当前只保留一个入口工具 `DocumentTool`
2. 当前不引入 `compare`、`analyze`、`ingest` 等子目录
3. 当前不引入复杂章节树模型
4. `support` 仅保留一个轻量文本清洗工具

---

## 5. 类职责设计

### 5.1 DocumentTool

定位：

通用文档解析入口。

职责：

1. 校验请求参数
2. 遍历多个临时文件
3. 根据扩展名选择对应 `DocumentParser`
4. 调用 `DocumentTextNormalizer` 做基础清洗
5. 汇总返回 `DocumentToolResult`

建议方法：

```java
public DocumentToolResult parse(DocumentToolReq req)
```

### 5.2 DocumentToolReq

定位：

文档工具请求对象。

建议字段：

1. `List<TempDocument> documents`

当前阶段不建议加入过多开关参数。

### 5.3 DocumentToolResult

定位：

文档工具统一返回对象。

建议字段：

1. `List<ParsedDocument> documents`
2. `List<String> warnings`
3. `List<String> errors`
4. `Integer successCount`
5. `Integer failureCount`

### 5.4 TempDocument

定位：

表示一次请求中的临时文件。

建议字段：

1. `String documentId`
2. `String filename`
3. `byte[] content`

说明：

1. `documentId` 仅用于本次请求内关联
2. 不承载持久化语义

### 5.5 ParsedDocument

定位：

统一解析后的文档对象。

建议字段：

1. `String documentId`
2. `String filename`
3. `String fileType`
4. `String plainText`
5. `String normalizedText`

说明：

当前阶段只保留纯文本相关结果，不提前引入复杂结构。

### 5.6 DocumentParser

定位：

文档解析器接口。

建议方法：

```java
boolean supports(String filename);

ParsedDocument parse(TempDocument document);
```

### 5.7 DocxDocumentParser / DocDocumentParser / MarkdownDocumentParser

定位：

不同格式的具体解析器。

职责：

1. 从二进制内容中提取文本
2. 输出基础 `ParsedDocument`
3. 不进行业务判断

### 5.8 DocumentTextNormalizer

定位：

轻量文本清洗工具。

职责：

1. 统一换行符
2. 去掉明显多余空白
3. 清理常见脏字符

当前阶段不做：

1. 复杂语义纠错
2. 复杂章节识别
3. OCR

---

## 6. 主链路设计

本次实现后的文件处理链路应为：

```text
Controller
-> 将 MultipartFile[] 转为 TempDocument[]
-> DocumentTool.parse()
-> 得到 ParsedDocument[]
-> 场景 PreparationService 组装 SceneData
-> 场景 Workflow 分析计算
-> 返回结果
```

标书审查场景示意：

```text
AgentController
-> AgentChatService
-> DocumentTool
-> ParsedDocument[]
-> TenderReviewPreparationService
-> TenderReviewWorkflow
-> AgentResponse
```

合同预审场景示意：

```text
AgentController
-> AgentChatService
-> DocumentTool
-> ParsedDocument[]
-> ContractPreparationService
-> ContractReviewWorkflow
-> AgentResponse
```

关键边界：

1. `DocumentTool` 只产生标准输入
2. 场景服务负责解释这些输入
3. 最终结论必须由场景 Workflow 产生

---

## 7. 本阶段不做的内容

为了避免过度设计，本阶段明确不做以下内容：

1. 文件持久化存储
2. 解析结果缓存
3. 通用文件比较工具
4. 通用文档分析工具
5. 章节树识别
6. 表格结构解析增强
7. OCR
8. 文档权限体系
9. 文件中心与 `fileId` 生命周期管理

这些能力可以在后续出现明确复用需求后再增量补充。

---

## 8. 分步实施计划

### 第一步：建立最小模型类

新增：

1. `TempDocument`
2. `ParsedDocument`
3. `DocumentToolReq`
4. `DocumentToolResult`

目标：

先定义 tool 层标准输入输出，避免场景层直接依赖 `MultipartFile`。

### 第二步：建立解析器接口

新增：

1. `DocumentParser`
2. `DocxDocumentParser`
3. `DocDocumentParser`
4. `MarkdownDocumentParser`

目标：

建立按文件类型分发的最小解析机制。

### 第三步：实现 DocumentTextNormalizer

新增：

1. `DocumentTextNormalizer`

目标：

让所有解析结果都经过统一基础清洗，减少后续 workflow 输入噪声。

### 第四步：实现 DocumentTool 主入口

新增：

1. `DocumentTool`

目标：

提供一个统一调用入口，屏蔽各 parser 细节。

### 第五步：接入场景 PreparationService

目标：

让标书、合同等场景都能消费 `ParsedDocument` 列表，而不是直接依赖上传对象。

要求：

1. 通用层只负责调用 `DocumentTool`
2. 场景层负责把 `ParsedDocument` 转为各自 `SceneData`

### 第六步：补充单元测试

至少覆盖：

1. `MarkdownDocumentParser`
2. `DocumentTextNormalizer`
3. `DocumentTool`
4. 多文件输入场景
5. 不支持文件格式场景
6. 空文件场景

---

## 9. 验收标准

完成后应满足以下最小标准：

1. 上传的 `.docx`、`.doc`、`.md` 文件能够被统一解析
2. `DocumentTool` 能返回统一的 `ParsedDocument` 列表
3. 标书场景可以基于 `ParsedDocument` 组装 `TenderReviewData`
4. 合同场景未来可以复用同一入口
5. `tool` 层不包含任何标书、合同专属判断逻辑
6. 整个方案不依赖文件持久化

---

## 10. 一句话结论

本次 `tool` 层 MVP 的目标不是建设完整文件平台，而是：

**先用一个最小的 `DocumentTool`，把“一次性上传文件”稳定转换成“场景可消费的标准文档输入”。**
