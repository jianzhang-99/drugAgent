# 上层 Agent 场景识别与统一调度改造计划

## 1. 目标

本文档用于指导 `drug-agent` 从“规则路由 + LLM 分类补充 + workflow 注册分发”的现状，升级为“上层通用 Agent 先理解用户意图、识别场景、输出决策，再统一调度下层 workflow”的架构。

这次改造的核心不是简单把 `SceneRouter` 换成一次模型调用，而是把“场景判断”从当前的工具型分类逻辑，提升为一个真正的上层调度能力。

改造后的系统应具备以下特点：

- 用户请求先经过上层 Agent 理解，而不是直接靠关键词或文件数量硬判
- 上层 Agent 输出结构化决策，而不是只返回一个 `SceneEnum`
- 所有 AI 入口都走统一调度链，不再出现部分接口绕过 workflow 的情况
- 下层 workflow 只关注业务执行，不再承担“猜用户想干什么”的职责
- 保留规则兜底、异常回退和可观测能力，确保线上可控

## 2. 为什么要做这次改造

当前项目虽然已经有 `SceneRouter + WorkflowRegistry + SceneWorkflow` 这层骨架，但它本质上仍然偏向“静态分发系统”，还不是真正的“上层 Agent 调度系统”。

当前主要问题有：

- 场景识别仍以规则为主，泛化能力有限
- 聊天接口和统一入口没有完全收敛到同一条调度链
- 一些接口仍直接调用 `AgentChatService`，绕过 workflow
- 当前路由虽然可解释，但不够“智能”，对复杂表达、多意图表达的理解仍偏弱
- workflow 层与上层判断层之间的职责边界还不够清晰

从产品视角看，用户希望系统表现得更像一个真正的智能助手：

- 能先理解“我要做什么”
- 再决定“该走哪条业务链路”
- 并且能解释“为什么这样判断”

所以这次改造的方向，不是增加更多关键词规则，而是补上一层真正的 Agent 调度能力。

## 3. 当前现状

### 3.1 当前主链路

目前同步请求主链路主要在：

- `src/main/java/com/liang/drugagent/service/DrugAgentService.java`
- `src/main/java/com/liang/drugagent/agent/SceneRouter.java`
- `src/main/java/com/liang/drugagent/agent/WorkflowRegistry.java`

当前处理流程大致如下：

```text
请求进入 DrugAgentService
  -> SceneRouter.route(req, context)
      -> sceneHint
      -> RuleBasedRouteDecider
      -> WorkflowRouteClassifier
      -> fallback
  -> WorkflowRegistry.get(scene)
  -> SceneWorkflow.execute(context)
```

### 3.2 当前已经具备的基础

当前项目已经有几项非常适合作为本次改造基础的能力：

- 有统一场景枚举 `SceneEnum`
- 有统一的 workflow 注册与分发机制 `WorkflowRegistry`
- 有结构化路由决策对象 `WorkflowRouteDecision`
- 有规则路由器 `RuleBasedRouteDecider`
- 有 LLM 分类器 `WorkflowRouteClassifier`
- 有上下文对象 `AgentContext`
- 有三类主要 workflow：
  - `TENDER_REVIEW`
  - `CONTRACT_PRECHECK`
  - `RISK_ALERT`
  - `UNKNOWN`

这意味着本次改造不需要推翻重来，更适合在现有架构上做职责升级。

### 3.3 当前最大的问题

当前系统已经有“路由层”，但还没有“上层 Agent 调度层”。

更准确地说：

- `SceneRouter` 现在是路由器，不是上层 Agent
- `WorkflowRouteClassifier` 现在是分类器，不是 orchestrator
- `WorkflowRegistry` 现在是注册表，不是决策层
- `ChatMessageController` 现在还有直接走 `AgentChatService` 的路径

因此系统表现出来会更像：

“先按规则或分类器判一下，再执行某个 workflow”

而不是：

“上层 Agent 先理解目标，再决定如何调度系统能力”

## 4. 改造目标与成功标准

满足以下条件，可以认为本轮改造完成第一阶段落地：

- 用户请求先经过上层 Agent 进行场景识别
- 上层 Agent 输出统一结构化决策：
  - `scene`
  - `confidence`
  - `reason`
  - `requiresClarification`
  - `source`
- `DrugAgentService`、上传入口、流式入口、聊天入口都走统一调度链
- 下层 workflow 只关注业务执行，不负责场景推断
- 模型异常、超时、非法输出、低置信度时可稳定回退
- 系统可记录完整路由信息，便于排查和后续评估

## 5. 总体设计原则

### 5.1 上层 Agent 只做调度，不做业务执行

上层 Agent 的职责是：

- 理解用户目标
- 识别场景
- 决定调用哪个 workflow
- 在必要时要求用户补充信息
- 将决策原因写入上下文

它不应直接承担标书审查、合同预审、风险预警的业务执行细节。

### 5.2 下层 workflow 只做业务执行

workflow 的职责是：

- 接收已经确定好的场景上下文
- 执行该场景的完整业务逻辑
- 产出统一结构化结果

它不应该再重复做“用户是不是想做这个场景”的判断。

### 5.3 规则保留，但降级为辅助能力

这次改造不建议彻底删除规则。

规则仍有三类价值：

- 显式 `sceneHint` 的快速直达
- 高置信特征的低成本兜底
- LLM 异常或超时时的回退能力

但规则不再是用户请求的主判断器，而应成为上层 Agent 的辅助信号。

### 5.4 统一入口优先于增加新能力

在新架构稳定前，不建议继续增加新的路由分支或新的 Agent 能力。

本轮重点应先放在：

- 把所有入口收敛到统一链路
- 明确判断层和执行层边界
- 让调度过程可解释、可回退、可测试

## 6. 目标架构

建议改造成如下结构：

```text
统一请求入口
  -> UpperAgentOrchestrator
      -> ContextEnricher
      -> IntentUnderstandingService
      -> RuleSignalProvider
      -> DecisionMerger
      -> ClarificationPolicy
  -> WorkflowRegistry
      -> TenderReviewWorkflow
      -> ContractPrecheckWorkflow
      -> RiskAlertWorkflow
      -> FallbackWorkflow
```

### 6.1 角色说明

| 组件 | 作用 |
|---|---|
| `UpperAgentOrchestrator` | 上层统一调度入口，负责识别场景并分发 workflow |
| `ContextEnricher` | 在决策前补充文件、会话、元数据等上下文 |
| `IntentUnderstandingService` | 使用 LLM 做意图理解和场景识别 |
| `RuleSignalProvider` | 提供规则信号，作为上层 Agent 的辅助输入 |
| `DecisionMerger` | 融合 LLM 决策和规则信号，生成最终决策 |
| `ClarificationPolicy` | 低置信时决定是否澄清用户 |
| `WorkflowRegistry` | 根据最终 `SceneEnum` 获取对应 workflow |

### 6.2 推荐的执行流程

```text
接收请求
  -> 构建 AgentContext
  -> 上层 Agent 收集上下文信息
  -> 上层 Agent 调用 LLM 做意图理解
  -> 融合规则信号形成 RouteDecision
  -> 判断是否需要澄清
  -> 若明确则分发到 workflow
  -> workflow 执行业务链路
  -> 输出统一响应
```

## 7. 推荐的数据结构

当前项目已有 `WorkflowRouteDecision`，建议继续沿用，并将其明确定位为“上层 Agent 的统一决策对象”。

建议字段如下：

```java
public class WorkflowRouteDecision {
    private SceneEnum scene;
    private String source; // upper_agent / sceneHint / rule / fallback
    private String reason;
    private Double confidence;
    private boolean requiresClarification;
    private String clarificationQuestion;
    private Map<String, Object> raw;
}
```

其中：

- `scene` 表示最终识别出的业务场景
- `source` 表示本次决策主要来源
- `reason` 表示简洁可读的判定原因
- `confidence` 表示置信度
- `requiresClarification` 表示是否应向用户补问
- `clarificationQuestion` 表示建议追问内容
- `raw` 用于保留模型原始输出或融合信息

## 8. 与当前代码结构的映射建议

建议尽量基于现有代码渐进式升级，而不是大面积重写。

### 8.1 保留的类

以下类建议保留：

- `WorkflowRegistry`
- `SceneWorkflow`
- `TenderReviewWorkflow`
- `ContractPrecheckWorkflow`
- `RiskAlertWorkflow`
- `FallbackWorkflow`
- `AgentContext`
- `WorkflowRouteDecision`

### 8.2 重构定位的类

以下类建议重构职责：

| 当前类 | 当前职责 | 改造后建议职责 |
|---|---|---|
| `SceneRouter` | 主路由器 | 兼容层或辅助路由组件 |
| `RuleBasedRouteDecider` | 主规则判断器 | 输出规则信号，不直接主导决策 |
| `WorkflowRouteClassifier` | LLM 分类器 | 迁移或收敛到 `IntentUnderstandingService` |
| `DrugAgentService` | 直接做场景路由和 workflow 分发 | 调用 `UpperAgentOrchestrator` |

### 8.3 建议新增的类

建议新增以下类：

| 类名 | 职责 |
|---|---|
| `UpperAgentOrchestrator` | 上层调度入口，统一协调场景识别与分发 |
| `IntentUnderstandingService` | 基于 LLM 做意图理解与场景识别 |
| `ContextEnricher` | 收集文件名、文件数、历史消息摘要等 |
| `RuleSignalProvider` | 生成规则信号供上层 Agent 参考 |
| `DecisionMerger` | 合并 LLM 结果与规则信号 |
| `ClarificationPolicy` | 控制低置信请求的补问策略 |

## 9. 分阶段改造计划

## 9.1 Phase 1：统一入口与链路收口

### 目标

先把所有 AI 请求收敛到一条统一调度链，为后续上层 Agent 接管打基础。

### 任务

| Task | Effort | Depends On | Done Criteria |
|---|---:|---|---|
| 盘点当前所有 AI 入口 | 2h | - | 列出所有同步、流式、聊天、上传入口 |
| 识别绕过 workflow 的调用 | 2h | 入口盘点 | 标出所有直接调用 `AgentChatService` 的路径 |
| 设计统一 orchestrator 接口 | 2h | 入口盘点 | 有清晰 `handle/streamHandle` 入口定义 |
| 确定旧接口兼容策略 | 2h | orchestrator 设计 | 明确哪些类暂时保留兼容 |

### 重点文件

- `src/main/java/com/liang/drugagent/service/DrugAgentService.java`
- `src/main/java/com/liang/drugagent/controller/ChatMessageController.java`
- `src/main/java/com/liang/drugagent/controller/DrugAgentController.java`

### 完成标志

- 所有请求入口被统一梳理
- 明确未来只有一条上层调度链

## 9.2 Phase 2：实现上层 Agent 场景识别层

### 目标

让“识别场景”真正由上层 Agent 完成，而不再由规则直接主导。

### 任务

| Task | Effort | Depends On | Done Criteria |
|---|---:|---|---|
| 设计上层 Agent 决策 Prompt | 4h | Phase 1 | 模型输出稳定为结构化 JSON |
| 实现 `ContextEnricher` | 3h | Phase 1 | 能补充文件、会话、元数据摘要 |
| 实现 `IntentUnderstandingService` | 4h | Prompt 设计 | 可生成结构化 `WorkflowRouteDecision` |
| 抽取规则信号组件 | 3h | Phase 1 | 规则信号可作为辅助输入 |
| 实现 `DecisionMerger` | 3h | LLM 与规则信号完成 | 能生成最终决策 |
| 实现 `ClarificationPolicy` | 2h | 决策结构完成 | 低置信度时可触发澄清 |

### 决策逻辑建议

建议采用如下顺序：

1. 显式 `sceneHint` 直接优先
2. 上层 Agent 基于 query、文件、上下文做主判断
3. 规则信号用于修正或增强置信度
4. 模型异常、低置信、输出非法时回退
5. 无法确定时走 `UNKNOWN` 或澄清分支

### 完成标志

- 新决策对象能由上层 Agent 产出
- 规则不再直接充当主入口判断器

## 9.3 Phase 3：切换主链路到上层 Agent

### 目标

把所有核心接口真正切到上层 Agent 调度入口。

### 任务

| Task | Effort | Depends On | Done Criteria |
|---|---:|---|---|
| 改造 `DrugAgentService.handle()` | 4h | Phase 2 | 同步请求走 `UpperAgentOrchestrator` |
| 改造 `DrugAgentService.streamHandle()` | 3h | Phase 2 | 流式请求走统一调度链 |
| 改造 `handleUploadedFiles()` | 3h | Phase 2 | 上传后通过上层 Agent 判场景 |
| 改造 `ChatMessageController` | 3h | Phase 2 | 聊天请求不再默认走 `default` |
| 统一响应中的路由元信息 | 2h | 主链路切换 | 返回 `routeSource/routeReason/routeConfidence` |

### 重点文件

- `src/main/java/com/liang/drugagent/service/DrugAgentService.java`
- `src/main/java/com/liang/drugagent/controller/ChatMessageController.java`
- `src/main/java/com/liang/drugagent/domain/resp/DrugAgentResp.java`

### 完成标志

- 不再存在主要接口绕过统一调度链的情况
- 所有入口能看到统一的路由元信息

## 9.4 Phase 4：补齐用户澄清与体验层

### 目标

让上层 Agent 不只是“更智能判断”，还能够在不确定时自然追问，而不是硬判。

### 任务

| Task | Effort | Depends On | Done Criteria |
|---|---:|---|---|
| 设计澄清返回结构 | 2h | Phase 2 | 前后端可识别需要补充信息 |
| 增加低置信度澄清文案 | 2h | 上层 Agent 决策完成 | 输出更自然、更像助手 |
| 前端展示场景与路由原因 | 4h | 后端返回元信息 | 用户能看到系统的判断依据 |
| 优化上传场景识别反馈 | 2h | 调度链稳定 | 上传文件时有更清晰的引导 |

### 完成标志

- 模糊请求不再只能落 `UNKNOWN`
- 用户能感知系统的“判断过程”

## 9.5 Phase 5：测试、灰度与回退

### 目标

让新链路可控上线，而不是一次性硬切。

### 任务

| Task | Effort | Depends On | Done Criteria |
|---|---:|---|---|
| 增加单元测试 | 4h | Phase 3 | 覆盖主场景与异常场景 |
| 增加集成测试 | 4h | Phase 3 | 关键入口都能回归验证 |
| 增加配置开关 | 2h | Phase 3 | 可切换 `rule / upper-agent / hybrid` |
| 增加日志与指标 | 3h | Phase 3 | 路由链路可追踪 |
| 做灰度验证 | 2h | 测试完成 | 可比较新旧路由效果 |

### 建议增加的配置项

```yaml
agent:
  routing:
    mode: upper-agent
    llm-enabled: true
    confidence-threshold: 0.75
    confidence-low-threshold: 0.50
    clarification-enabled: true
```

### 完成标志

- 新链路可灰度
- 出现异常时可快速切回旧逻辑

## 10. 建议的开发顺序

为了降低风险，建议按以下顺序实施：

1. 新增 `UpperAgentOrchestrator`，先不删旧 `SceneRouter`
2. 先让同步主链路接入新 orchestrator
3. 再切流式接口和聊天接口
4. 稳定后再将 `SceneRouter` 降级为兼容层或辅助层
5. 最后补前端展示、灰度开关和回归测试

这样做的好处是：

- 不会一次性影响所有入口
- 更容易比较新旧路由效果
- 可以逐步替换，而不是大改后一起排错

## 11. 推荐的测试场景

建议至少覆盖以下测试样例：

### 11.1 明确场景类

- “请帮我检查这两份投标文件有没有围标风险”
- “帮我审一下这份合同有没有合规问题”
- “分析一下近三个月药品异常用量趋势”

### 11.2 模糊表达类

- “帮我看看这个材料有没有问题”
- “这份文件要不要重点审一下”
- “这批数据是不是有异常”

### 11.3 文件上传类

- 上传 2 份标书文件
- 上传 1 份合同文件
- 上传 1 份无法明确类型的文件

### 11.4 异常与回退类

- LLM 超时
- LLM 返回空
- LLM 返回非法 JSON
- 低置信度场景
- 规则与 LLM 冲突场景

## 12. 风险与应对

| 风险 | 影响 | 概率 | 应对 |
|---|---|---|---|
| LLM 路由结果不稳定 | 误判 workflow | 中 | 保留规则辅助和回退开关 |
| 多入口切换时行为不一致 | 用户体验割裂 | 高 | 优先统一 orchestrator 入口 |
| workflow 内部仍有场景判断 | 职责混乱 | 中 | 明确 workflow 只做执行 |
| 低置信度场景处理不好 | 误导用户 | 中 | 增加澄清机制 |
| 改造范围扩大 | 进度失控 | 中 | 先做单 Agent 调度层，不做多 Agent |

## 13. 预计排期

按 1 名开发者估算，建议排期如下：

| Phase | 内容 | 时间 |
|---|---|---|
| Phase 1 | 入口收口与方案设计 | 0.5 - 1 天 |
| Phase 2 | 上层 Agent 场景识别层实现 | 1.5 - 2 天 |
| Phase 3 | 主链路切换 | 1 天 |
| Phase 4 | 澄清与体验补齐 | 0.5 - 1 天 |
| Phase 5 | 测试、灰度、回退 | 1 - 1.5 天 |

总计约：

- 3.5 - 5.5 个工作日

如果多人协作，可将“上层 Agent 后端改造”和“前端元信息展示”并行推进。

## 14. 一句话实施建议

本次改造最重要的不是“再做一个分类器”，而是补上一层真正的上层 Agent 调度能力。

建议你把本轮目标定为：

**先统一入口，再让上层 Agent 接管场景识别，最后保留规则作为辅助和兜底。**

这样做出来的系统，会更像一个真正可演进的 Agent 产品，而不只是“多加了一层 LLM 路由”的 workflow 分发器。
