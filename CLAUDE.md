# CLAUDE.md

本文件为 Claude Code 提供项目约束、架构边界与协作规范。

---

## 1. 项目定位

这是一个医药监管方向的上层通用 Agent 项目。

当前重点场景：
1. 标书审查
2. 合同预审
3. 风险预警

系统目标：建设一个有明确场景路由、稳定工作流、结构化结果、证据链和可解释输出的业务 Agent 系统，**不是随意聊天机器人**。

---

## 2. 总体架构原则

### 2.1 Controller 层
- **职责**：接收 HTTP 请求、基础参数接收与协议转换、返回统一响应
- **禁止**：编排复杂业务流程、场景判断、直接操作具体 Workflow

### 2.2 AgentChatService 层
- **定位**：前端会话请求的统一应用编排器
- **应该负责**：接住前端对话请求、构建统一上下文、调用路由服务判断场景、根据场景分发到对应执行器、统一降级、统一结果回写和响应整理
- **不应该负责**：场景专属业务规则、Workflow 内部逻辑、Tool schema 和调用细节、场景专属数据准备逻辑、底层基础设施细节
- **判断原则**：对所有场景都成立的逻辑可以放 AgentChatService；只对单一场景成立的逻辑不要放

### 2.3 Route 层
- `AgentRouteService` 只负责：场景识别、路由决策、返回 `WorkflowRouteDecision`
- 不要让它直接输出最终业务结果

### 2.4 Orchestrator 层
- 场景复杂且需要 Tool Calling 时，应优先引入场景编排器（如 `TenderReviewToolOrchestrator`）
- **负责**：注册 Tool 给 LLM、调用 Tool、接收结构化结果、调用 LLM 做最终整理
- 不要把这些逻辑塞回 `AgentChatService`

### 2.5 Tool 层
- Tool 是"可调用执行入口"，不是最终业务引擎
- **负责**：参数校验、请求整理、调用底层 Workflow、返回结构化结果对象
- **不负责**：HTTP 接入、前端响应组装、通用路由

### 2.6 Workflow 层
- Workflow 是确定性业务执行引擎
- **负责**：业务执行主链路、规则命中、豁免处理、风险融合、证据组装、报告生成
- 不要在 Workflow 中处理前端协议、会话管理、HTTP 语义

---

## 3. 标书审查目标链路

当用户输入"帮我看看这两份标书是否有围标风险"，目标链路：

```
AgentController
-> AgentChatService
-> AgentRouteService
-> scene = TENDER_REVIEW
-> 若有上传文件，补齐 TenderReviewData
-> 转入 TenderReviewToolOrchestrator
-> Orchestrator 注册 reviewTenderTool 给 LLM
-> LLM 决定是否调用 Tool
-> reviewTenderTool(request)
-> Tool 内部调用 TenderReviewWorkflow
-> Workflow: 文档解析 -> 结构化提取 -> 规则命中 -> 豁免处理 -> 风险融合 -> 证据组装 -> 报告生成
-> Tool 返回 ReviewTenderToolResult
-> LLM 整理成用户可读回复
-> AgentChatService 统一回写并返回 DrugAgentResp
```

- 标书审查场景主入口目标是 `TenderReviewToolOrchestrator`
- `TenderReviewWorkflow` 继续作为底层确定性审查引擎
- `AgentChatService` 不应长期直接驱动 `TenderReviewWorkflow`

---

## 4. 编码规范

### 4.1 命名规范
- 请求对象统一以 `Req` 结尾
- 响应对象统一以 `Resp` 结尾
- 场景编排类优先使用 `Orchestrator`
- 场景数据准备类优先使用 `PreparationService`
- 避免旧命名和新命名混用
- 优先表达职责和层次，不要用模糊名字

### 4.2 DTO 规范
纯 DTO 优先简洁，避免注解堆叠：

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
- 日志要带场景上下文：`sessionId`、`traceId`、`scene`
- **日志内容必须使用中文**，禁止在日志中出现英文（变量值、异常堆栈除外）
- 日志格式：`[类名] 操作描述 + 关键上下文`
- 敏感信息（如文件内容、用户输入）需脱敏后再记录

### 4.4 注释规范
- 注释必须解释**为什么这样做**、**这段代码的业务意图**、**边界条件**，不要只翻译代码表面动作
- 禁止写“赋值型注释”或“逐行翻译型注释”，例如：
  - `// 设置sessionId`
  - `// 调用service方法`
  - `// 遍历list`
- 注释要少而准，能靠命名表达清楚的代码不要额外写注释
- 方法注释优先说明：
  - 方法职责
  - 输入输出语义
  - 关键副作用
  - 适用边界或限制
- 类注释优先说明：
  - 该类在系统中的定位
  - 负责什么
  - 不负责什么
- 修改代码时，如果原注释与现有实现不一致，优先更新或删除注释，禁止保留过时注释
- 禁止为了“显得完整”批量生成模板化注释，尤其是无信息量的 AI 注释
- TODO 注释必须写清楚“待完成什么”和“为什么现在不做”，避免只写 `TODO`
- 注释内容必须使用中文，术语、类名、接口名可保留英文原文

### 4.5 CORS 规范
- 不要在 Controller 上继续新增 `@CrossOrigin`
- 统一使用全局 CORS 配置

### 4.6 返回结构规范
前端对话统一返回 `DrugAgentResp` 或其统一包装，应尽量包含：
- `traceId`、`scene`、`routeReason`、`routeSource`、`confidence`
- `summary`、`answer`、`riskLevel`、`score`
- `report`、`evidenceList`、`evidenceGroups`、`steps`

---

## 5. 重构优先级规范

修改代码时优先顺序：
1. 先保证职责边界清晰
2. 再整理流程顺序
3. 再统一命名
4. 最后再考虑抽象复用

**不要一上来过度抽象。**

优先做：去除明显越层逻辑、减少大而全的 service、将场景专属逻辑从通用层下沉

避免做：为了"看起来高级"引入过多中间抽象、业务尚未稳定就提前做复杂泛化

---

## 6. 文档规范

涉及架构、主流程、Tool 链路的重要调整时，应优先更新：
- `doc/上层通用agent/技术文档/上层通用Agent总体设计.md`
- `doc/上层通用agent/技术文档/标书审查Tool化落地技术设计.md`
- `doc/上层通用agent/技术文档/AgentChatService重构设计.md`

新增重要设计文档请优先放在 `doc/上层通用agent/技术文档`。

---

## 7. AI Agent 工作方式

1. 先读现有代码与文档，再改代码
2. 优先沿用既有目录结构和职责分层
3. 如果发现通用层混入场景专属逻辑，应优先提出拆分方案
4. 对标书审查链路，优先朝 Tool Orchestrator 主入口收敛
5. 不要把临时兼容写法当成长期架构

---

## 8. 一句话协作准则

在这个仓库里，优先做"边界清晰、流程稳定、便于扩展"的设计，而不是"短期能跑但不断堆逻辑"的实现。

---

## 9. 本地环境配置

### 9.1 Maven 配置

本地 Maven 使用自定义配置文件和本地仓库：

| 配置项 | 路径 |
|--------|------|
| settings.xml | `/Users/liangjiajian/.m2/settings_ppy.xml` |
| 本地仓库 | `/Users/liangjiajian/.m2/AIDUNDUN_repository` |

执行 Maven 命令时需指定配置文件：
```bash
mvn clean install -s /Users/liangjiajian/.m2/settings_ppy.xml
```
