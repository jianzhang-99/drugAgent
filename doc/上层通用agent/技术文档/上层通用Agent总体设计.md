# 上层通用 Agent 总体设计

> 文档版本：V0.2
> 更新时间：2026-03-30
> 状态：当前主设计文档

---

## 1. 项目定位

这是一个医药监管方向的上层通用 Agent 项目。

当前重点场景：
1. 标书审查（TENDER_REVIEW）
2. 合同预审（CONTRACT_PRECHECK）
3. 风险预警（RISK_ALERT）

系统目标：建设一个有明确场景路由、稳定工作流、结构化结果、证据链和可解释输出的业务 Agent 系统，**不是随意聊天机器人**。

---

## 2. 总体架构原则

### 2.1 分层职责

```
AgentController
  -> AgentChatService          # 会话编排
  -> AgentSceneService         # 场景总编排（核心）
  -> [场景服务]                # 场景唯一入口（如 TenderReviewSceneService）
  -> [场景内部链路]            # preparation -> orchestrator -> tool -> workflow
  -> AgentResponseService      # 最终响应装配
  -> DrugAgentResp             # 前端统一响应
```

### 2.2 各层职责边界

| 层次 | 职责 | 不负责 |
|------|------|--------|
| Controller | 接收请求、协议转换、返回响应 | 场景判断、业务编排 |
| AgentChatService | 会话管理、上下文构建、调用路由、结果回写 | 场景内部逻辑 |
| AgentSceneService | 场景识别、参数抽取、场景分发、输出校验、结果适配 | 业务规则执行 |
| 场景服务 (facade) | 接收请求、调用 preparation + orchestrator、返回统一结果 | 上层路由 |
| 场景编排器 (Orchestrator) | 注册 Tool、调用 Tool、LLM 整理 | 底层细节 |
| Tool | 参数校验、调用 Workflow、返回结构化结果 | 业务主链路 |
| Workflow | 确定性业务执行、规则命中、报告生成 | 协议转换 |

### 2.3 核心设计决策

**1. AgentSceneService 收口是合理的**

当前阶段不建议继续拆出：
- `AgentDecisionService`
- `AgentOutputGuardService`
- `SceneWorkflowRegistry`

原因：
- 当前场景数量还不多
- workflow 差异明显
- 继续拆会增加理解和维护成本
- 对 MVP 来说收益不高

**2. 不强推统一 SceneWorkflow 接口**

每个场景的输入输出差异较大，统一接口很容易沦为"空壳抽象"。因此本阶段直接使用场景服务作为入口。

**3. 不强推统一 WorkflowResult**

各场景结果结构差异大，强行统一会产生臃肿 DTO。更适合各 workflow 返回自己的原生结果对象，由 AgentSceneService 负责整理成统一前端响应。

---

## 3. 场景路由设计

### 3.1 路由策略（三段式）

```
1. 显式 sceneHint 优先（前端或调用方显式指定）
2. 规则直达（关键词匹配）
3. LLM 兜底（当前默认走通用对话）
```

### 3.2 关键词匹配规则

| 场景 | 关键词 |
|------|--------|
| TENDER_REVIEW | 围标、串标、标书雷同、标书审查、标书风险、标书比对、标书比较、投标文件、竞标 |
| CONTRACT_PRECHECK | 合同、合同审核、合同预审、条款审查 |
| RISK_ALERT | 风险、风险预警、风险监控 |

### 3.3 路由决策结果

```java
@Data
@Builder
public class WorkflowRouteDecision {
    private SceneEnum scene;          // 识别出的场景
    private String source;            // 来源：sceneHint / rule / llm / fallback
    private String reason;            // 路由原因
    private Double confidence;        // 置信度
    private boolean requiresClarification;
    private String clarificationQuestion;
}
```

---

## 4. 标书审查场景链路

### 4.1 完整调用链

```
AgentController
  -> AgentChatService
  -> AgentSceneService
     -> 场景识别（sceneHint / 关键词）
     -> TenderReviewSceneService (facade)
        -> TenderReviewPreparationService (数据准备)
        -> TenderReviewToolOrchestrator (编排)
           -> ReviewTenderTool (Tool)
              -> TenderReviewWorkflow (确定性引擎)
        -> 返回 TenderReviewResult / AgentExecutionResult
     -> 输出校验（可选）
     -> 适配为 AgentChatResp
  -> AgentResponseService
  -> DrugAgentResp
```

### 4.2 目录结构

```
scene/tender_review
├── facade/
│   └── TenderReviewSceneService    # 对通用层暴露的唯一场景入口
├── preparation/
│   └── TenderReviewPreparationService  # 数据准备
├── orchestrator/
│   └── TenderReviewToolOrchestrator   # 工具编排
├── tool/
│   ├── ReviewTenderTool              # Tool 入口
│   └── ReviewTenderToolRequest/Result
├── workflow/
│   └── TenderReviewWorkflow          # 确定性业务引擎
├── service/
│   ├── RiskFusionService             # 风险融合
│   ├── EvidenceAssemblerService      # 证据组装
│   ├── ReportGenerationService       # 报告生成
│   └── TenderCaseService             # 案例管理
├── support/
│   ├── assembler/
│   │   └── TenderReviewDataAssembler  # 数据装配
│   ├── parser/                        # 文档解析
│   └── rules/                         # 规则引擎
└── model/
    ├── TenderReviewData
    ├── TenderDocument
    ├── TenderCase
    ├── CompareScope
    └── ...
```

### 4.3 Workflow 执行主链路

```
文档解析 -> 结构化提取 -> 规则命中 -> 豁免处理 -> 风险融合 -> 证据组装 -> 报告生成
```

---

## 5. 通用对话场景

非特定场景请求默认走通用对话：

```
AgentSceneService
  -> dispatchToGeneralChat()
  -> LLM 服务（通用 system prompt）
  -> 返回 AgentExecutionResult
```

---

## 6. 响应结构

### 6.1 统一响应 (DrugAgentResp)

```json
{
  "traceId": "string",
  "scene": "TENDER_REVIEW | CONTRACT_PRECHECK | RISK_ALERT | DEFAULT",
  "routeReason": "string",
  "routeSource": "sceneHint | rule | llm | fallback",
  "confidence": 0.0,
  "summary": "string",
  "answer": "string",
  "requiresClarification": false,
  "clarificationQuestion": "string",
  "structuredData": {},
  "riskLevel": "HIGH | MEDIUM | LOW",
  "score": 85,
  "report": {},
  "evidenceList": [],
  "steps": []
}
```

### 6.2 场景原生结果对象（不强制统一）

| 场景 | 结果类 |
|------|--------|
| 标书审查 | TenderReviewResult |
| 合同预审 | ContractPrecheckResult |
| 风险预警 | RiskAlertResult |

---

## 7. 编码规范

### 7.1 命名规范
- 请求对象以 `Req` 结尾
- 响应对象以 `Resp` 结尾
- 场景编排类使用 `Orchestrator`
- 数据准备类使用 `PreparationService`
- 避免旧命名和新命名混用

### 7.2 日志规范
- 使用 Lombok `@Slf4j`
- 日志内容使用**中文**
- 格式：`[类名] 操作描述 + 关键上下文`
- 上下文必须包含：`sessionId`、`traceId`、`scene`

### 7.3 注释规范
- 优先解释"为什么这样做"，而非"做了什么"
- 禁止逐行翻译型注释
- 方法注释说明：职责、输入输出、关键副作用、适用边界

---

## 8. 文档体系

| 文档 | 定位 |
|------|------|
| 本文档 (上层通用Agent总体设计.md) | 主设计文档，架构总览 |
| 场景Workflow轻量化MVP设计.md | 详细设计参考，MVP 阶段具体设计 |
| 标书审查目录收敛与调用设计建议.md | 标书场景实现指南 |
| Prompt清单.md | 项目 Prompt 资产清单，记录名称/场景/层级/载体/调用位置/输出类型 |
| 项目Prompt工程优化方案.md | Prompt 工程优化方案，含分层策略/命名规范/评测方案 |

---

## 9. 一句话总结

**通用层统一入口 + 场景层自由实现 + 结果统一收口。**

`AgentSceneService` 是当前阶段的核心编排层，各 workflow 按场景自由返回，最后由 `AgentSceneService` 统一整理成前端响应。
