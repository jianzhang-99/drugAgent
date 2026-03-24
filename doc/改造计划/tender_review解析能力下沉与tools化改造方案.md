# tender_review 解析能力下沉与 tools 化改造方案

## 1. 改造背景

当前标书审查场景中的解析代码位于：

- `src/main/java/com/liang/drugagent/scene/tender_review/support/parser`

其中主要包含几类能力：

1. 文件格式解析
2. 文本切块与结构化
3. 字段识别与抽取
4. 将解析结果组装为 `TenderReviewData`

目前这些能力都放在 `tender_review` 场景目录下，存在一个明显问题：

**“通用文档解析能力”和“标书审查领域语义抽取能力”被混在了一起。**

这会带来几个后果：

1. 其他场景如果也要解析 `doc/docx/markdown`，无法自然复用
2. 底层解析能力被场景名称绑死，目录语义不准确
3. 解析层和业务层边界不清晰，后续扩展容易继续耦合
4. 一旦想把解析能力暴露为 agent tools，会发现当前实现过于场景化

所以这次改造的核心目标不是简单移动目录，而是：

**把“通用解析”下沉成可复用 tools / 基础设施，把“招采语义理解”保留在 tender_review 场景层。**

---

## 2. 当前代码职责判断

### 2.1 适合下沉为通用能力的部分

以下类更接近文档解析基础设施：

- `TenderDocxParser`
- `TenderDocParser`
- `TenderMarkdownParser`

这几类的主要职责是：

1. 读取不同文件格式
2. 提取段落、表格、标题
3. 生成统一结构化结果

这些职责不依赖“标书审查”这个业务场景，本质上是通用能力。

### 2.2 需要拆分后部分下沉的部分

- `TenderTextStructureSupport`

这个类里混了两类职责：

1. 通用解析支持
2. 招采领域字段抽取

其中下面这些更偏通用：

1. 文本规范化
2. 章节标题判断
3. 基础 block 构建
4. 基础标签识别框架

而下面这些明显带有标书审查语义：

1. `bid_price`
2. `team_member`
3. `contact_phone`
4. `contact_email`
5. 价格、团队、联系方式等基于招投标材料的字段识别规则

因此它不应该整体下沉，而应该拆开。

### 2.3 应保留在场景层的部分

- `TenderReviewDataResolver`

这个类虽然名字里也带 `parser`，但实际不只是“解析器”，它已经承担了：

1. 从 `metadata` 装配场景输入
2. 构建 `TenderCase`
3. 构建 `TenderDocument`
4. 构建 `CompareScope`
5. 构建 `ExtractionMeta`
6. 基于招采语义提取业务字段

这已经是典型的“场景适配器 / 场景数据装配器”，不适合下沉成通用 tools。

---

## 3. 改造目标

本次改造希望达到以下状态：

### 3.1 目录语义清晰

解析工具目录只放通用解析能力，场景目录只放场景专属逻辑。

### 3.2 能力可复用

后续如果出现：

1. 处罚文书审查
2. 监管材料比对
3. 知识库入库解析
4. 通用 agent 文档理解

都可以直接复用同一套文档解析 tools。

### 3.3 便于 tools 化

后续可以把底层文档解析能力封装成统一工具，例如：

1. 文档转结构化文本
2. 文档标题树提取
3. 文档表格抽取
4. 文档基础字段扫描

这样更符合 agent 系统中“通用工具 + 场景编排”的分层思路。

### 3.4 控制重构成本

不追求一次性大改，只做清晰拆层，尽量保证：

1. 现有业务流程可继续运行
2. 现有模型结构尽量少动
3. 迁移路径平滑

---

## 4. 目标分层设计

建议将当前能力拆成三层。

### 4.1 第一层：通用文档解析工具层

职责：

1. 读取 `doc`
2. 读取 `docx`
3. 读取 `markdown`
4. 提取段落、标题、表格
5. 输出统一文档结构

这一层不应该知道：

1. 什么是标书
2. 什么是报价
3. 什么是团队成员
4. 什么是风险点

建议输出的是中性结构，例如：

1. `DocumentParseResult`
2. `DocumentBlock`
3. `DocumentSectionNode`

### 4.2 第二层：通用文本结构支持层

职责：

1. 文本规范化
2. 标题识别
3. 段落切分
4. 表格行列解析
5. 通用标签识别框架

这一层依然不应该直接内置招采字段语义。

### 4.3 第三层：tender_review 场景适配层

职责：

1. 将通用解析结果映射为 `TenderReviewData`
2. 识别招采领域字段
3. 构建招采场景的 `Field`
4. 构建 `CompareScope`
5. 组装 `ExtractionMeta`

这一层才允许出现：

1. `bid_price`
2. `team_member`
3. `service_commitment`
4. `risk_identification`
5. `case_data`

---

## 5. 建议目录结构

下面是一份更适合后续演进的目录方案。

### 5.1 通用解析层目录

建议新增类似目录：

`src/main/java/com/liang/drugagent/core/document`

或者：

`src/main/java/com/liang/drugagent/infra/document`

建议子目录如下：

1. `parser`
2. `model`
3. `support`

例如：

```text
src/main/java/com/liang/drugagent/core/document
├── model
│   ├── DocumentBlock.java
│   ├── DocumentFieldTag.java
│   ├── DocumentParseResult.java
│   └── DocumentSectionNode.java
├── parser
│   ├── DocDocumentParser.java
│   ├── DocxDocumentParser.java
│   ├── MarkdownDocumentParser.java
│   └── DocumentParser.java
└── support
    ├── DocumentTextNormalizer.java
    ├── DocumentSectionRecognizer.java
    └── MarkdownTableSupport.java
```

### 5.2 tender_review 场景层目录

场景目录保留招采专属逻辑，例如：

```text
src/main/java/com/liang/drugagent/scene/tender_review/support
├── assembler
│   └── TenderReviewDataResolver.java
├── extractor
│   ├── TenderFieldExtractor.java
│   ├── TenderParagraphFieldExtractor.java
│   └── TenderTableFieldExtractor.java
└── mapper
    └── TenderDocumentMapper.java
```

其中：

1. `assembler` 负责组装场景数据
2. `extractor` 负责招采字段抽取
3. `mapper` 负责通用结构与场景模型之间的转换

---

## 6. 类级别重构建议

### 6.1 `TenderDocxParser`

建议调整为通用类，例如：

- `DocxDocumentParser`

职责保留：

1. 解析 `docx`
2. 输出统一结构化结果

不再直接依赖 `Tender*` 命名的模型。

### 6.2 `TenderDocParser`

建议调整为：

- `DocDocumentParser`

保持为通用格式解析器。

### 6.3 `TenderMarkdownParser`

建议调整为：

- `MarkdownDocumentParser`

只保留 Markdown 解析职责。

### 6.4 `TenderTextStructureSupport`

建议拆成至少两部分：

1. 通用支持类
2. 场景字段抽取类

推荐拆分方式：

- `DocumentStructureSupport`
- `TenderFieldExtractor`

其中：

`DocumentStructureSupport` 负责：

1. `normalizeText`
2. `isSectionHeader`
3. `buildFromParagraphs`
4. 基础 block 生成

`TenderFieldExtractor` 负责：

1. 电话提取
2. 邮箱提取
3. 报价提取
4. 团队成员提取
5. 招采特定标签识别

### 6.5 `TenderReviewDataResolver`

建议重命名或重新归类，不再放在 `parser` 目录下。

更合适的名字可以是：

1. `TenderReviewDataAssembler`
2. `TenderReviewInputResolver`
3. `TenderReviewDataFactory`

其中我更推荐：

- `TenderReviewDataAssembler`

因为它的本质更接近“装配场景数据”，而不是“解析文档”。

---

## 7. 推荐迁移步骤

为了降低风险，建议分四步迁移。

### 7.1 第一步：只调整命名和目录，不改行为

动作：

1. 新增通用文档解析包
2. 将 `TenderDocxParser`、`TenderDocParser`、`TenderMarkdownParser` 平移到通用目录
3. 保持原有逻辑不变
4. 通过适配层保留旧调用入口

目标：

先把“目录语义”改对，避免继续堆积场景耦合。

### 7.2 第二步：拆分 `TenderTextStructureSupport`

动作：

1. 把文本规范化、标题识别、段落构建抽到通用 support
2. 把招采字段抽取迁移到 `TenderFieldExtractor`
3. 让通用 parser 只依赖通用 support

目标：

完成“通用解析”和“场景语义抽取”的第一次明确分离。

### 7.3 第三步：重构 `TenderReviewDataResolver`

动作：

1. 将其迁出 `parser` 包
2. 改名为 `TenderReviewDataAssembler`
3. 让它依赖通用解析结果而非直接内嵌解析细节

目标：

让该类只负责：

1. 输入读取
2. 场景模型装配
3. 业务字段抽取编排

### 7.4 第四步：抽象 agent tools 接口

动作：

1. 对外提供统一文档解析服务入口
2. 统一按文件类型自动路由解析器
3. 形成未来可供 agent 调用的 tool service

例如：

- `DocumentParseService`
- `DocumentStructureTool`

目标：

让其他场景或上层 agent 可以直接复用这套能力。

---

## 8. 推荐接口形态

为了后续 tools 化，建议尽快补一个统一入口接口。

例如：

```java
public interface DocumentParser {
    boolean supports(String fileType);
    DocumentParseResult parse(InputStream inputStream, String documentId) throws IOException;
}
```

再由一个统一服务做分发：

```java
public interface DocumentParseService {
    DocumentParseResult parse(String fileType, InputStream inputStream, String documentId) throws IOException;
}
```

这样好处很明显：

1. 上层不需要知道具体是 `doc`、`docx` 还是 `markdown`
2. 解析器替换成本低
3. 后续新增 `pdf/html/txt` 更自然
4. 更适合封装成 tool

---

## 9. 对现有业务代码的影响

### 9.1 短期影响

短期主要影响：

1. 类名变化
2. 包路径变化
3. Spring 注入点变化

但如果第一阶段先保留适配层，这些影响可以控制在较小范围。

### 9.2 中期收益

完成拆分后会获得几个直接收益：

1. `tender_review` 场景目录更干净
2. 通用解析能力可以被其他场景复用
3. 代码评审时更容易判断职责边界
4. 后续做 agent tools 时改动更少

### 9.3 需要注意的点

需要重点注意下面几个风险：

1. 当前通用解析结果类仍然使用了 `Tender*` 命名模型，迁移时要避免一次性大范围联动
2. `TenderTextStructureSupport` 中的字段抽取逻辑与现有规则执行器之间可能存在隐性耦合
3. `TenderReviewDataResolver` 里对 Markdown 标题、表格、错别字等处理逻辑较多，拆分时要补测试

---

## 10. 建议优先级

如果本轮只做一版“性价比最高”的改造，建议优先顺序如下：

1. 先把三个格式解析器下沉为通用解析类
2. 再拆 `TenderTextStructureSupport`
3. 最后再动 `TenderReviewDataResolver`

原因是：

1. 三个格式解析器的通用属性最强
2. `TenderTextStructureSupport` 是当前职责混杂最明显的点
3. `TenderReviewDataResolver` 业务耦合最重，应该最后收口

---

## 11. 最终建议

这次重构不建议理解成：

**“把 parser 整个目录都挪成 tools。”**

更准确的理解应该是：

**“把 parser 目录里的通用文档解析能力下沉成 tools，把招采场景专属的数据装配和字段抽取保留在场景层。”**

也就是说，真正应该下沉的是：

1. 文件格式解析
2. 文本切块
3. 标题树提取
4. 表格结构提取
5. 通用解析接口

而不应该整体下沉的是：

1. `TenderReviewData` 装配
2. 招采字段抽取
3. 招采语义识别
4. 比对范围构建
5. 场景元数据拼装

这样拆分之后，代码结构会更稳定，也更符合后续 agent 化、tools 化、场景复用化的发展方向。
