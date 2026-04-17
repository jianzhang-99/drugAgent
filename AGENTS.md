# AGENTS.md

本文件用于为进入本仓库工作的 AI Agent 提供统一项目约束、架构边界与协作规范。

适用范围：

- 仓库根目录及其所有子目录

---

## 1. 项目定位

这是一个医药监管方向的上层通用 Agent 项目。

当前重点场景包括：

1. 标书审查
2. 合同预审
3. 风险预警

系统目标不是做一个“随意聊天机器人”，而是建设一个：

- 有明确场景路由
- 有稳定工作流
- 有结构化结果
- 有证据链和可解释输出

的业务 Agent 系统。

---

## 2. 总体架构原则

请始终按以下分层理解和修改代码：

### 2.1 Controller 层

职责：

- 接收 HTTP 请求
- 做基础参数接收与协议转换
- 返回统一响应

禁止：

- 在 Controller 中编排复杂业务流程
- 在 Controller 中直接写场景判断
- 在 Controller 中直接操作具体 Workflow

### 2.2 AgentChatService 层

`AgentChatService` 的定位是：

“前端会话请求的统一应用编排器”

它应该负责：

1. 接住前端对话请求
2. 构建统一上下文
3. 调用路由服务判断场景
4. 根据场景分发到对应执行器
5. 做统一降级
6. 做统一结果回写和响应整理

它不应该负责：

1. 场景专属业务规则
2. Workflow 内部逻辑
3. Tool schema 和 Tool 调用细节
4. 大量场景专属数据准备逻辑
5. 底层基础设施细节

判断原则：

- 对所有场景都成立的逻辑，可以放在 `AgentChatService`
- 只对单一场景成立的逻辑，不要放在 `AgentChatService`

### 2.3 Route 层

`AgentRouteService` 只负责：

- 场景识别
- 路由决策
- 返回 `WorkflowRouteDecision`

不要让它直接输出最终业务结果。

### 2.4 Orchestrator 层

场景复杂且需要 Tool Calling 时，应优先引入场景编排器，例如：

- `TenderReviewToolOrchestrator`

Orchestrator 负责：

- 注册 Tool 给 LLM
- 调用 Tool
- 接收 Tool 结构化结果
- 调用 LLM 做最终整理

不要把这些逻辑塞回 `AgentChatService`。

### 2.5 Tool 层

Tool 是“可调用执行入口”，不是最终业务引擎。

例如：

- `ReviewTenderTool`

Tool 负责：

- 参数校验
- 请求整理
- 调用底层 Workflow
- 返回结构化结果对象

Tool 不负责：

- HTTP 接入
- 前端响应组装
- 通用路由

### 2.6 Workflow 层

Workflow 是确定性业务执行引擎。

例如：

- `TenderReviewWorkflow`

Workflow 负责：

- 业务执行主链路
- 规则命中
- 豁免处理
- 风险融合
- 证据组装
- 报告生成

不要在 Workflow 中处理前端协议、会话管理、HTTP 语义。

---

## 3. 当前认可的标书审查目标链路

当用户输入：

“帮我看看这两份标书是否有围标风险”

目标链路应理解为：

```text
AgentController
-> AgentChatService
-> AgentRouteService
-> scene = TENDER_REVIEW
-> 若有上传文件，补齐 TenderReviewData
-> AgentChatService 不直接 executeWorkflow
-> 转入 TenderReviewToolOrchestrator
-> Orchestrator 注册 reviewTenderTool 给 LLM
-> LLM 决定是否调用 Tool
-> reviewTenderTool(request)
-> Tool 内部调用 TenderReviewWorkflow
-> Workflow:
   文档解析 -> 结构化提取 -> 规则命中 -> 豁免处理 -> 风险融合 -> 证据组装 -> 报告生成
-> Tool 返回 ReviewTenderToolResult
-> LLM 整理成用户可读回复
-> AgentChatService 统一回写并返回 DrugAgentResp
```

重要说明：

- 标书审查场景的主入口目标是 `TenderReviewToolOrchestrator`
- `TenderReviewWorkflow` 继续作为底层确定性审查引擎
- `AgentChatService` 不应长期直接驱动 `TenderReviewWorkflow`

---

## 4. 编码规范

### 4.1 命名规范

- 请求对象统一以 `Req` 结尾
- 响应对象统一以 `Resp` 结尾
- 场景编排类优先使用 `Orchestrator`
- 场景数据准备类优先使用 `PreparationService`
- 避免旧命名和新命名混用，例如：
  - 不要同时出现 `DrugAgentReq` 和 `AgentChatReq`
  - 不要出现语义不清的 `buildDrugAgentReq` 这类旧名字

命名要求：

- 优先表达职责
- 优先表达层次
- 不要图省事用模糊名字

### 4.2 DTO 规范

纯 DTO 优先简洁，避免注解堆叠。

推荐：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxReq {}
```

如果类没有继承关系，不要默认使用 `@SuperBuilder`。

### 4.3 日志规范

- 优先使用 Lombok `@Slf4j`
- 不要混用手写 `LoggerFactory` 与 `@Slf4j`
- 日志要带场景上下文，如 `sessionId`、`traceId`、`scene`

### 4.4 CORS 规范

- 不要在 Controller 上继续新增 `@CrossOrigin`
- 统一使用全局 CORS 配置

### 4.5 返回结构规范

前端对话统一返回 `DrugAgentResp` 或其统一包装。

结果应尽量包含：

- `traceId`
- `scene`
- `routeReason`
- `routeSource`
- `confidence`
- `summary`
- `answer`
- `riskLevel`
- `score`
- `report`
- `evidenceList`
- `evidenceGroups`
- `steps`

---

## 5. 重构优先级规范

修改代码时，优先按以下顺序推进：

1. 先保证职责边界清晰
2. 再整理流程顺序
3. 再统一命名
4. 最后再考虑抽象复用

不要一上来过度抽象。

优先做：

- 去除明显越层逻辑
- 减少大而全的 service
- 将场景专属逻辑从通用层下沉

避免做：

- 为了“看起来高级”引入过多中间抽象
- 业务尚未稳定就提前做复杂泛化

---

## 6. 文档规范

涉及架构、主流程、Tool 链路的重要调整时，应优先更新：

- `doc/上层通用agent/技术文档/上层通用Agent总体设计.md`
- `doc/上层通用agent/技术文档/标书审查Tool化落地技术设计.md`
- `doc/上层通用agent/技术文档/AgentChatService重构设计.md`

如果新增重要设计文档，请优先放在：

- `doc/上层通用agent/技术文档`

---

## 7. 本项目内 AI Agent 的工作方式

进入本项目工作的 AI Agent，应遵守以下默认策略：

1. 先读现有代码与文档，再改代码
2. 优先沿用既有目录结构和职责分层
3. 如果发现通用层混入场景专属逻辑，应优先提出拆分方案
4. 对标书审查链路，优先朝 Tool Orchestrator 主入口收敛
5. 不要把临时兼容写法当成长期架构

---

## 8. 一句话协作准则

在这个仓库里，优先做“边界清晰、流程稳定、便于扩展”的设计，而不是“短期能跑但不断堆逻辑”的实现。


<claude-mem-context>
# Memory Context

# [drug-agent] recent context, 2026-04-17 2:12pm GMT+8

No previous sessions found.
</claude-mem-context>