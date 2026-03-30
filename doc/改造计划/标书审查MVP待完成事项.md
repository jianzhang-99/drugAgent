# 标书审查 MVP 待完成事项

> 文档版本：v1.0
> 更新时间：2026-03-30
> 目标：明确当前距离“标书审查可用 MVP”还差的关键事项，并给出建议实施顺序

## 1. 当前判断

从当前代码结构看，标书审查链路已经具备以下基础能力：

1. 已有标书场景入口
2. 已有 `TenderReviewPreparationService`
3. 已有 `TenderReviewWorkflow`
4. 已有规则执行、免责处理、风险融合、证据组装、报告生成能力
5. 已有通用 `DocumentTool`

因此，当前状态不是“从零开始”，而是：

**主链路骨架基本具备，但上传入口、场景装配、端到端联调和测试验收还没有完全闭环。**

---

## 2. 距离可用 MVP 还差哪些内容

### 2.1 文件上传入口真正打通

这是当前最关键的缺口。

现状问题：

1. 前端上传走的是 `multipart/form-data`
2. 后端 `/agent/submit` 仍按 `@RequestBody` 普通 JSON 请求处理
3. 这样无法真正接收到上传文件

影响：

即使 `TenderReviewPreparationService` 已经支持从 `req.getFiles()` 构建数据，实际运行时也拿不到真实文件。

目标：

将 `/agent/submit` 改造成真正的文件上传对话入口，使其可以接收：

1. `query`
2. `sceneHint`
3. `sessionId`
4. `userId`
5. `submittedBy`
6. `MultipartFile[] files`

完成标准：

前端上传两份标书时，后端能在 Controller 层接收到真实文件数组。

---

### 2.2 DocumentTool 到 TenderReviewData 的装配质量验证

现状：

当前已经存在链路：

```text
MultipartFile[]
-> TempDocument[]
-> DocumentTool
-> ParsedDocument[]
-> TenderReviewDataAssembler
-> TenderReviewData
```

但当前仍需重点确认：

1. 文档名是否正确传递
2. 文本内容是否完整进入 `TenderDocument`
3. 解析后的文本是否足够支撑规则命中
4. `CompareScope` 是否正确生成
5. `ExtractionMeta` 是否完整

风险：

如果这里只是“对象组起来了”，但字段映射不完整，后续 workflow 虽然能跑，规则命中结果会失真。

目标：

确保 `ParsedDocument -> TenderReviewData` 转换后的数据，已经满足规则引擎和报告生成的最低输入要求。

完成标准：

使用真实样本文件时，`TenderReviewWorkflow` 可以稳定读取到至少 2 份完整文档文本，并输出有效结果。

---

### 2.3 标书场景执行结果回传完整性确认

现状：

当前链路已经是：

```text
TenderReviewSceneService
-> TenderReviewWorkflow
-> WorkflowResult
-> AgentExecutionResult
-> AgentChatResp
```

但还需要确认以下字段是否都能稳定返回给前端：

1. `scene`
2. `summary`
3. `answer`
4. `riskLevel`
5. `score`
6. `report`
7. `evidenceList`
8. `evidenceGroups`
9. `steps`

风险：

如果中间转换层字段丢失，前端会看到“有回答但没有证据”或者“有报告但没有风险等级”的不完整结果。

目标：

确保标书审查的核心结果字段全部贯通到统一响应中。

完成标准：

一次标书审查结束后，前端拿到的响应包含可展示的风险等级、风险分、摘要、报告和证据。

---

### 2.4 异常与边界场景处理补齐

MVP 阶段必须先处理最常见失败路径。

至少需要覆盖：

1. 文件数少于 2 份
2. 文件为空
3. 文件格式不支持
4. 文件解析成功数不足 2 份
5. Workflow 执行异常
6. 报告生成失败

目标：

让每个关键失败路径都能返回明确、稳定、用户可理解的错误提示，而不是空结果或系统异常。

完成标准：

以上异常场景都能返回明确中文提示，并且不会导致主链路崩溃。

---

### 2.5 端到端测试补齐

当前如果没有端到端测试，主链路虽然“看起来已经差不多”，但上线前风险仍然很高。

最少建议补齐以下测试：

1. `DocumentTool` 多格式解析测试
2. `TenderReviewPreparationService` 上传文件构建数据测试
3. `TenderReviewWorkflow` 样本文件执行测试
4. `/agent/submit` 上传文件接口测试
5. 两份真实样本文件的端到端集成测试
6. 异常边界测试

目标：

让“标书审查已具备 MVP 能力”这件事可验证，而不是只靠代码阅读判断。

完成标准：

至少存在一组自动化测试，能证明上传两份样本文件后可以完成标书审查并返回结构化结果。

---

## 3. 当前建议暂缓的内容

为了先把 MVP 做实，以下内容建议暂缓：

1. 文件持久化存储
2. 文件中心与 `fileId` 生命周期管理
3. 通用文档 compare tool
4. 标书场景 Tool Calling 编排增强
5. 多场景统一文件缓存机制
6. 复杂 OCR
7. 流式标书审查输出

原因：

这些都不是当前“先把标书审查主链跑通”的阻塞项。

---

## 4. 建议实施顺序

建议按以下顺序推进：

### 第一步：打通文件上传入口

目标：

让 `/agent/submit` 真正接住 `multipart/form-data`。

原因：

没有真实文件输入，其余链路都无法验证。

### 第二步：完成上传文件到 `TenderReviewData` 的联调

目标：

确保 `MultipartFile[] -> ParsedDocument[] -> TenderReviewData` 真正成立。

原因：

这是 workflow 的直接输入，必须先稳定。

### 第三步：跑通 Workflow 和统一响应

目标：

确保 workflow 执行后，关键字段能返回给前端。

原因：

这一步完成后，标书审查才算真正“有可见结果”。

### 第四步：补齐失败路径

目标：

保证异常场景可控。

原因：

MVP 不是只跑通 happy path，还要能稳定失败。

### 第五步：补齐自动化测试

目标：

让这条链路具备可重复验证能力。

原因：

否则后续继续改造时非常容易回归损坏。

---

## 5. 一句话结论

当前距离“标书审查可用 MVP”已经不远，真正还差的不是大架构，而是：

**把上传入口接通、把文档装配联调好、把最终响应打透、再用测试把这条链路钉牢。**
