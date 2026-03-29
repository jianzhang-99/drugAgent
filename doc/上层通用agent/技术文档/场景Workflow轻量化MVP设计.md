# 场景 Workflow 轻量化 MVP 设计

> 文档版本：V1.2
> 更新时间：2026-03-29
> 目标：让 `AgentSceneService` 成为统一场景编排入口，LLM 负责意图识别、参数抽取和输出校验，各 workflow 按场景自由返回结果，由 `AgentSceneService` 统一整理给前端。

---

## 1. 设计目标

当前项目需要的不是一套很重的通用工作流框架，而是一套能快速支撑 MVP 的轻量场景执行链路。

目标链路是：

1. 前端统一走 `/agent/chat`
2. `AgentChatService` 负责会话编排
3. `AgentSceneService` 负责场景识别、参数抽取、场景执行、输出校验
4. 各场景 workflow 自由定义自己的输入输出
5. `AgentSceneService` 把 workflow 原始结果整理成前端可消费结果
6. 统一返回 `AgentChatResp`

一句话描述：

`统一入口，统一编排，场景内部自由，前端响应统一。`

---

## 2. 核心判断

### 2.1 AgentSceneService 收口是合理的

当前阶段，不建议继续拆出：

- `AgentDecisionService`
- `AgentOutputGuardService`
- `SceneWorkflowRegistry`

原因：

1. 当前场景数量还不多
2. workflow 差异明显
3. 继续拆会增加理解和维护成本
4. 对 MVP 来说收益不高

因此，推荐让 `AgentSceneService` 直接承担以下职责：

1. 场景识别
2. 参数抽取
3. 场景分发
4. workflow 执行
5. 输出校验
6. 结果适配

### 2.2 不强推统一 SceneWorkflow 接口

当前不建议强推统一 `SceneWorkflow` 接口。

原因：

1. 每个场景的输入差异较大
2. 每个场景的输出差异较大
3. 统一接口很容易沦为“空壳抽象”
4. 最后通常还是在实现里大量特判

因此，本阶段更适合直接使用场景服务：

- `TenderReviewSceneService`
- `ContractPrecheckSceneService`
- `RiskAlertSceneService`

由 `AgentSceneService` 直接调用。

### 2.3 不强推统一 WorkflowResult

当前也不建议强推统一 `WorkflowResult`。

原因：

1. 标书审查、合同预审、风险预警的结果结构差异大
2. 强行统一会产生一个臃肿 DTO
3. 这种统一在 MVP 阶段大多没有真实价值

因此更适合：

- 每个 workflow 返回自己的原生结果对象
- `AgentSceneService` 负责把不同结果整理成统一前端响应

---

## 3. 推荐总体链路

```text
AgentController
-> AgentChatService
-> AgentSceneService
   -> 场景识别（规则 + LLM）
   -> 参数抽取（LLM）
   -> 调用具体场景服务
   -> 获取 workflow 原始结果
   -> 输出校验（LLM）
   -> 适配为前端响应数据
-> AgentResponseService
-> AgentChatResp
```

对应职责：

- `AgentChatService`：会话编排
- `AgentSceneService`：场景总编排
- 场景服务：场景执行
- `AgentResponseService`：最终响应装配

---

## 4. 分层职责

### 4.1 Controller 层

职责：

- 接收 HTTP 请求
- 转为 `AgentChatReq`
- 返回统一 `AgentChatResp`

禁止：

- 直接做场景判断
- 直接执行具体 workflow

### 4.2 AgentChatService

职责：

- 会话读取
- 构建 `AgentChatContext`
- 调用 `AgentSceneService`
- 回写用户消息和助手消息
- 返回统一响应

不负责：

- 场景识别细节
- workflow 业务逻辑
- 输出校验逻辑

### 4.3 AgentSceneService

`AgentSceneService` 是当前阶段的核心编排层。

职责：

1. 基于 `sceneHint`、规则、LLM 判断场景
2. 基于用户问题和上下文抽取场景参数
3. 分发到具体场景服务
4. 接收 workflow 原始结果
5. 调用 LLM 做输出规范校验
6. 将场景结果整理为前端统一响应字段

不负责：

1. 场景内部规则计算
2. 具体业务证据生成
3. 具体场景数据解析主链路

### 4.4 各场景服务

本阶段建议直接使用场景服务作为 workflow 入口，例如：

- `TenderReviewSceneService`
- `ContractPrecheckSceneService`
- `RiskAlertSceneService`

场景服务职责：

1. 接收场景参数
2. 做参数校验
3. 执行场景主流程
4. 返回场景原始结果对象

---

## 5. 推荐对象设计

### 5.1 SceneDecision

虽然不再拆单独决策服务，但仍建议保留一个轻量决策对象。

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDecision {

    private SceneEnum scene;
    private String source;
    private String reason;
    private Double confidence;
    private boolean requiresClarification;
    private String clarificationQuestion;
    private Map<String, Object> params;
}
```

说明：

- `scene`：识别出的场景
- `source`：来源，如 `sceneHint` / `rule` / `llm` / `fallback`
- `reason`：路由原因
- `confidence`：置信度
- `requiresClarification`：是否需要澄清
- `clarificationQuestion`：澄清问题
- `params`：场景参数

### 5.2 Workflow 原始结果对象

每个场景返回自己的原生结果对象，不统一。

例如：

- `TenderReviewResult`
- `ContractPrecheckResult`
- `RiskAlertResult`

示例：

```java
public class TenderReviewResult {
    private String caseId;
    private String riskLevel;
    private Integer score;
    private String summary;
    private String answer;
    private Object report;
    private List<?> evidenceList;
}
```

```java
public class ContractPrecheckResult {
    private String contractType;
    private String summary;
    private String answer;
    private List<?> riskClauses;
    private List<String> suggestions;
}
```

### 5.3 AgentChatResp

前端统一响应仍保留。

建议它承担“统一响应壳”的职责，而不是要求内部业务字段完全一致。

通用字段建议保留：

- `traceId`
- `scene`
- `routeReason`
- `routeSource`
- `confidence`
- `summary`
- `answer`
- `requiresClarification`
- `clarificationQuestion`
- `structuredData`

高频通用字段可按需保留，例如：

- `riskLevel`
- `score`
- `report`
- `evidenceList`
- `steps`

核心原则：

- 前端统一接一个壳
- 场景差异放在 `structuredData`

---

## 6. 场景识别与参数抽取

### 6.1 决策策略

推荐三段式：

1. 显式 `sceneHint` 优先
2. 规则直达
3. LLM 兜底

### 6.2 LLM 决策输出规范

LLM 只输出结构化 JSON，不输出最终长答复。

建议格式：

```json
{
  "scene": "TENDER_REVIEW | CONTRACT_PRECHECK | RISK_ALERT | DEFAULT | UNKNOWN",
  "confidence": 0.0,
  "reason": "string",
  "requiresClarification": false,
  "clarificationQuestion": "",
  "params": {
    "fileIds": [],
    "reviewFocus": "",
    "needReport": true
  }
}
```

要求：

1. 不输出多余解释
2. 不输出 markdown
3. 不直接输出最终回复

---

## 7. workflow 执行方式

每个场景服务内部按自己的主链路执行，不强求统一接口。

### 7.1 标书审查

```text
AgentSceneService
-> TenderReviewSceneService
   -> 数据准备
   -> 文档解析
   -> 规则命中
   -> 风险融合
   -> 报告生成
   -> 返回 TenderReviewResult
```

### 7.2 合同预审

```text
AgentSceneService
-> ContractPrecheckSceneService
   -> 合同解析
   -> 条款提取
   -> 风险识别
   -> 修改建议
   -> 返回 ContractPrecheckResult
```

### 7.3 风险预警

```text
AgentSceneService
-> RiskAlertSceneService
   -> 数据加载
   -> 指标分析
   -> 异常识别
   -> 风险归因
   -> 返回 RiskAlertResult
```

---

## 8. 输出校验设计

### 8.1 为什么保留输出校验 LLM

你提出的顾虑是成立的：

- workflow 输出有可能字段缺失
- answer 可能表达不合规
- 场景结果可能虽然算出来了，但不适合直接给前端

因此，建议在 `AgentSceneService` 内保留一层输出校验。

### 8.2 输出校验职责

输出校验 LLM 只负责：

1. 检查结果是否完整
2. 检查表达是否符合输出规范
3. 检查是否和结构化结果冲突
4. 生成最终展示用 `summary` / `answer`

不负责：

1. 重新推理业务结论
2. 捏造 workflow 未产出的事实
3. 修改证据和评分

### 8.3 输出校验输入

建议输入：

- `scene`
- `routeReason`
- workflow 原始结果对象
- 当前需要的输出规范

### 8.4 输出校验输出

建议返回：

```json
{
  "passed": true,
  "summary": "string",
  "answer": "string",
  "warnings": []
}
```

如果校验发现问题：

- 轻问题：允许生成规范化展示文案，并带 warning
- 重问题：返回失败提示，由 `AgentSceneService` 走降级

---

## 9. AgentSceneService 结果适配原则

这是本设计最关键的一点。

`AgentSceneService` 可以在 workflow 返回后做统一整理，但只能做“结果适配”，不能做“业务重算”。

### 9.1 可以做的事

1. 提取摘要
2. 统一补充 `scene`
3. 统一补充 `routeReason` / `routeSource`
4. 调用输出校验 LLM
5. 组装 `structuredData`
6. 转成 `AgentChatResp`

### 9.2 不应该做的事

1. 重新计算风险等级
2. 重新命中规则
3. 重新生成证据
4. 修正 workflow 的业务结论

否则 `AgentSceneService` 会迅速膨胀成新的“大一统业务类”。

---

## 10. 推荐的适配方式

建议在 `AgentSceneService` 内部按场景分开适配，而不是试图一把梭哈。

例如：

```java
private AgentChatResp buildTenderReviewResp(
        AgentChatContext context,
        SceneDecision decision,
        TenderReviewResult result) {
    // 提取 summary / answer / riskLevel / structuredData
}
```

```java
private AgentChatResp buildContractPrecheckResp(
        AgentChatContext context,
        SceneDecision decision,
        ContractPrecheckResult result) {
    // 提取 summary / answer / suggestions / structuredData
}
```

```java
private AgentChatResp buildRiskAlertResp(
        AgentChatContext context,
        SceneDecision decision,
        RiskAlertResult result) {
    // 提取 summary / answer / alertMetrics / structuredData
}
```

这样虽然有分支，但它是清晰的、可维护的，不是过度抽象。

---

## 11. 与当前代码的收敛建议

### 11.1 保留

- `AgentChatService`
- `AgentSceneService`
- `AgentResponseService`
- `SceneEnum`
- `TenderReviewSceneService`
- `TenderReviewPreparationService`
- `TenderReviewWorkflow`

### 11.2 不建议继续强化

- `SceneWorkflow`
- `SceneWorkflowRegistry`
- `WorkflowResult`
- 过细的通用编排 service 拆分

### 11.3 标书审查链路建议

收敛为：

```text
AgentChatService
-> AgentSceneService
   -> 决策
   -> 参数抽取
   -> TenderReviewSceneService
   -> 获取 TenderReviewResult
   -> 输出校验
   -> 转 AgentChatResp
-> 前端
```

如果当前已经存在 `TenderReviewToolOrchestrator`，也建议控制它的职责：

- 可以保留工具编排
- 不要让它变成上层总编排器
- 最终结果整理仍然放回 `AgentSceneService`

---

## 12. 推荐的最小实现方案

### 方案 A：最轻方案

```text
AgentController
-> AgentChatService
-> AgentSceneService
   -> 决策
   -> 参数抽取
   -> 调用场景服务
   -> 输出校验
   -> 构建 AgentChatResp
-> 返回前端
```

特点：

- 一个总编排服务
- 不引入统一 workflow 接口
- 不引入统一 workflow 结果模型
- 保留统一前端响应

### 方案 B：场景内部保留自己的 orchestrator

```text
AgentSceneService
-> TenderReviewSceneService
   -> TenderReviewToolOrchestrator
   -> TenderReviewWorkflow
   -> TenderReviewResult
-> 输出校验
-> AgentChatResp
```

适用条件：

- 某个场景内部确实存在多工具协同需求

但这不应该影响上层总体设计。

---

## 13. 推荐迭代顺序

### 第一阶段

先把文档和链路收敛：

1. 明确 `AgentSceneService` 为唯一场景编排入口
2. 去掉 `SceneWorkflowRegistry`
3. 去掉 `SceneWorkflow`
4. 去掉统一 `WorkflowResult`

### 第二阶段

标书审查先落地：

1. 标书审查返回 `TenderReviewResult`
2. `AgentSceneService` 负责把它转成 `AgentChatResp`
3. 增加输出校验 LLM

### 第三阶段

扩展其他场景：

1. 合同预审定义自己的结果对象
2. 风险预警定义自己的结果对象
3. `AgentSceneService` 继续按场景适配

---

## 14. 一句话结论

当前 MVP 更适合的不是“统一 workflow 接口 + 统一 workflow 结果”，而是：

`AgentSceneService 统一编排，各 workflow 自由返回，最后由 AgentSceneService 统一整理成前端响应。`

这样更轻，也更符合你现在项目的实际阶段。

---

## 15. 标书审查 Workflow 收敛建议

### 15.1 推荐链路

标书审查场景在 MVP 阶段，建议收敛为下面这条主链路：

```text
AgentChatService
-> AgentSceneService
-> TenderReviewSceneService
   -> 参数校验
   -> TenderReviewPreparationService
   -> TenderReviewWorkflow
   -> 返回 TenderReviewResult
-> AgentSceneService
   -> 输出校验
   -> 适配 AgentChatResp
-> 前端
```

这条链路强调两点：

1. 标书审查场景内部尽量保持确定性 workflow
2. 上层统一由 `AgentSceneService` 做结果收口

### 15.2 为什么不建议当前继续保留过多层级

如果当前标书审查链路是：

```text
AgentSceneService
-> TenderReviewSceneService
-> TenderReviewPreparationService
-> TenderReviewToolOrchestrator
-> ReviewTenderTool
-> TenderReviewWorkflow
```

那么对于 MVP 来说偏重，主要问题有：

1. `TenderReviewSceneService`、`TenderReviewToolOrchestrator`、`ReviewTenderTool` 职责容易重叠
2. 当前如果没有真实的多 Tool Calling，`Tool` 和 `Orchestrator` 的价值还没有真正体现
3. 标书审查本质上是强确定性业务流，更适合固定 workflow，而不是多层 agent 化编排
4. 层级过多后，问题排查、规则调整、链路理解都会变重

因此，MVP 阶段更适合收敛成：

`SceneService -> PreparationService -> Workflow`

而不是：

`SceneService -> Orchestrator -> Tool -> Workflow`

### 15.3 各层职责建议

#### AgentSceneService

职责：

1. 识别当前请求是否进入标书审查场景
2. 抽取标书审查所需参数
3. 调用 `TenderReviewSceneService`
4. 拿到 `TenderReviewResult`
5. 做输出校验
6. 适配成 `AgentChatResp`

不负责：

1. 标书规则命中
2. 风险计算
3. 证据生成

#### TenderReviewSceneService

职责：

1. 作为标书审查场景唯一入口
2. 做最低限度参数校验
3. 调用 `TenderReviewPreparationService`
4. 调用 `TenderReviewWorkflow`
5. 返回 `TenderReviewResult`

不负责：

1. 上层场景路由
2. 通用输出校验
3. 前端响应最终装配

#### TenderReviewPreparationService

职责：

1. 整理文件、上下文、前端参数
2. 构建标书审查所需输入数据
3. 补齐 workflow 所需最小数据集

不负责：

1. 风险判断
2. 审查结论生成

#### TenderReviewWorkflow

职责：

1. 执行标书审查主链路
2. 完成文档解析、规则命中、风险融合、报告生成
3. 返回 `TenderReviewResult`

它是标书审查场景的核心确定性执行引擎。

### 15.4 TenderReviewResult 建议

标书审查结果不必统一进通用 `WorkflowResult`，建议单独定义 `TenderReviewResult`。

建议至少包含：

- `caseId`
- `summary`
- `answer`
- `riskLevel`
- `score`
- `report`
- `evidenceList`
- `steps`

如果后续需要，可继续补充：

- `evidenceGroups`
- `documentIds`
- `managementSummary`
- `suggestedActions`

### 15.5 对现有类的处理建议

建议保留：

- `TenderReviewSceneService`
- `TenderReviewPreparationService`
- `TenderReviewWorkflow`

建议弱化或暂时去掉：

- `ReviewTenderTool`
- `TenderReviewToolOrchestrator`

判断标准如下：

1. 如果当前只有一个主审查流程，则优先去掉 `Tool/Orchestrator`
2. 只有当后续真的拆成多个可独立编排的工具时，再恢复 `Tool/Orchestrator`

例如未来如果标书审查拆成：

- 相似片段检测工具
- 围标规则检测工具
- 证据归并工具
- 报告生成工具

这时再引入 `Orchestrator` 才更合理。

### 15.6 一句话总结

标书审查在 MVP 阶段最合理的形态是：

`TenderReviewSceneService -> TenderReviewPreparationService -> TenderReviewWorkflow`

然后由 `AgentSceneService` 在场景外层做统一收口和输出适配。
