# tender_review 模型目录简化改造计划

## 1. 改造背景

当前目录：

`src/main/java/com/liang/drugagent/scene/tender_review/model`

里面已经放了不少对象，但现在主要问题不是“类多”本身，而是：

1. 有些对象拆得太细
2. 有些对象只是为了包一层数据单独成类
3. 有些对象只是另一个对象的附属结构
4. 同一目录里混了任务对象、解析对象、规则结果对象、辅助对象
5. 部分类名过于抽象，阅读门槛偏高

所以这次改造的目标不是继续加结构，而是：

**把没必要单独存在的小对象合并回去，把真正重要的主对象保留下来。**

---

## 2. 改造原则

### 2.1 不为“小结构”单独建类

如果一个对象只是另一个对象的附属信息，而且字段很少、没有独立行为，就优先考虑合并。

### 2.2 结果包装类不要滥用

如果一个类只是：

- 包一层 `List`
- 没有额外业务语义
- 没有额外统计字段
- 没有独立行为

那么优先考虑直接删掉。

### 2.3 只保留真正独立的主对象

只有下面这些情况才值得单独保留：

1. 它是一个稳定业务主对象
2. 它会被多个流程使用
3. 它后面还会继续扩字段
4. 它单独存在会让语义更清晰

### 2.4 演示版优先简单

当前项目阶段是演示版，不需要为了“以后可能复杂”而提前保留很多细碎模型。

---

## 3. 当前模型分类

当前 `model` 目录里的对象大致可以分成 4 类：

### 3.1 任务和文档主对象

- `TenderCase`
- `TenderCaseStatus`
- `TenderDocument`
- `TenderReviewData`

### 3.2 文档解析结构

- `TenderDocumentParseResult`
- `TenderSectionNode`
- `Block`
- `Field`
- `Anchor`
- `ExtractionMeta`

### 3.3 规则命中与风险结果

- `RuleResult`
- `RuleHit`
- `RuleEvidence`
- `RiskFusionResult`
- `ExemptionResult`
- `ExemptionHit`

### 3.4 辅助对象

- `CompareScope`

这些对象里，有些保留合理，有些已经可以继续收缩。

---

## 4. 建议保留的对象

下面这些对象建议保留：

### 4.1 `TenderCase`

这是标书审查任务主对象，保留合理。

### 4.2 `TenderCaseStatus`

这是稳定枚举，保留合理。

### 4.3 `TenderDocument`

这是参与审查的文档主对象，保留合理。

### 4.4 `TenderReviewData`

这是标书审查流程的总输入对象，保留合理。

### 4.5 `TenderDocumentParseResult`

这是文档解析阶段的结果对象，建议保留。

### 4.6 `TenderSectionNode`

章节树本身有明确语义，建议保留。

### 4.7 `RuleHit`

规则命中结果属于核心业务结果对象，建议保留。

### 4.8 `RuleEvidence`

证据明细和命中结果是稳定关系，建议保留。

### 4.9 `RiskFusionResult`

风险融合后的结果是主输出之一，建议保留。

---

## 5. 建议优先合并的对象

下面这些对象建议作为第一批收缩目标。

### 5.1 `Anchor` 合并进 `Block` 和 `Field`

当前 `Anchor` 只是定位信息容器：

- chapterPath
- paragraphIndex
- tableIndex
- pageNo
- sectionNo
- paragraphNo
- tableNo

它当前更像是：

- `Block` 的附属结构
- `Field` 的附属结构

而不是一个真正独立的业务对象。

建议：

1. 将定位字段直接合并进 `Block`
2. 将定位字段直接合并进 `Field`
3. 删除独立的 `Anchor`

适用前提：

- 当前只有少量对象使用 `Anchor`
- 后续暂时不做复杂定位体系抽象

---

### 5.2 `ExtractionMeta` 合并进 `TenderDocumentParseResult`

当前 `ExtractionMeta` 只有：

- `schemaVersion`
- `parserVersion`
- `parseSuccess`

这类字段明显更适合作为解析结果的一部分，而不是独立模型。

建议：

1. 把这 3 个字段并进 `TenderDocumentParseResult`
2. 删除独立的 `ExtractionMeta`

---

### 5.3 `RuleResult` 删除，直接使用 `List<RuleHit>`

当前 `RuleResult` 本质上只是：

- `List<RuleHit> hits`

如果没有更多字段，例如：

- 汇总统计
- 执行耗时
- 规则版本汇总
- 结果摘要

那么这个类就没有必要存在。

建议：

1. 直接用 `List<RuleHit>` 表达规则命中结果
2. 删除 `RuleResult`

---

### 5.4 `CompareScope` 评估并入 `TenderReviewData`

当前 `CompareScope` 字段较少：

- `scopeId`
- `scopeType`
- `documentIds`

如果当前比对范围逻辑还不复杂，这个对象可以直接并入：

- `TenderReviewData`

或者作为内部静态结构，而不是单独顶层模型。

建议：

1. 先检查它的使用频率和复杂度
2. 如果只是简单传递文档比对范围，就并入 `TenderReviewData`

---

### 5.5 `ExemptionResult` 评估并入风险融合流程对象

当前 `ExemptionResult` 只是：

- `effectiveHits`
- `exemptionHits`

它更像一个中间处理结果，而不是一个长期稳定的领域主对象。

建议两种方向任选其一：

1. 并入 `RiskFusionResult`
2. 作为规则处理流程内部对象，不再放在 `model` 顶层

如果它只在少数流程里短暂使用，就不建议长期保留成目录顶层模型。

---

## 6. 暂时不建议直接合并的对象

下面这些虽然名字还可以优化，但当前不建议直接合并。

### 6.1 `Block`

它表示原文内容块，和字段抽取对象不是一回事。

建议保留，但后续可以考虑改名。

### 6.2 `Field`

它表示抽取出的结构化字段，建议保留。

### 6.3 `RuleHit`

它是规则命中主对象，建议保留。

### 6.4 `RuleEvidence`

它是规则证据明细，建议保留。

### 6.5 `TenderDocumentParseResult`

虽然内部还能收缩，但主对象本身建议保留。

---

## 7. 命名优化建议

当前有些类名太抽象，即使保留，也建议后续逐步优化。

### 7.1 `Block`

建议改成更直白的名字，例如：

- `TenderBlock`
- `DocumentBlock`

### 7.2 `Field`

建议改成更具体的名字，例如：

- `TenderField`
- `ExtractedField`

### 7.3 `Anchor`

如果最终不删除，建议至少改名为：

- `DocumentAnchor`
- `TextAnchor`

### 7.4 `CompareScope`

建议改成更完整的名字，例如：

- `DocumentCompareScope`

### 7.5 `TenderReviewData`

如果后面你觉得语义还不够清晰，可以考虑改成：

- `TenderReviewPayload`
- `TenderReviewContextData`

当前也可以先不改。

---

## 8. 推荐改造步骤

### Phase 1：先删包装壳

优先处理：

- `RuleResult`

这是最容易收掉的一层。

### Phase 2：收解析附属对象

优先处理：

- `ExtractionMeta`
- `Anchor`

把这两个从“独立模型”收回到主对象里。

### Phase 3：评估中间结果对象

评估下面两个：

- `CompareScope`
- `ExemptionResult`

如果只是少数地方中间传递，就不要保留成顶层模型。

### Phase 4：再做命名优化

最后再考虑这些改名：

- `Block`
- `Field`
- `CompareScope`

避免一开始同时做“合并 + 改名 + 重构引用”，改动过大。

---

## 9. 精简后的推荐模型集合

如果按当前建议收缩后，`model` 目录更理想的保留对象大概是：

- `TenderCase`
- `TenderCaseStatus`
- `TenderDocument`
- `TenderReviewData`
- `TenderDocumentParseResult`
- `TenderSectionNode`
- `Block`
- `Field`
- `RuleHit`
- `RuleEvidence`
- `RiskFusionResult`
- `ExemptionHit`

另外：

- `ExtractionMeta` 并入 `TenderDocumentParseResult`
- `Anchor` 并入 `Block` / `Field`
- `RuleResult` 删除
- `CompareScope` 评估并入
- `ExemptionResult` 评估并入

---

## 10. 最终结论

当前 `scene/tender_review/model` 目录的问题，不只是“平铺太乱”，更重要的是：

**有些对象拆得太细，已经超过了当前项目需要的复杂度。**

所以这次改造的核心不是继续加结构，而是：

**保留主对象，合并附属对象，删除包装壳，逐步让模型回到够用就好的状态。**
