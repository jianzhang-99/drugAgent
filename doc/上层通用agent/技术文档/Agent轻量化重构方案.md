# Agent 轻量化重构方案

> 文档版本：v1.0
> 更新时间：2026-03-26
> 目标：先把 chat 主链路做顺，不提前做重，不提前做全

## 1. 这次重构的目标

当前项目的问题不是完全没设计，而是：

- service 偏多
- 职责有重叠
- 新旧方案并存
- AI 容易继续过度开发

所以这次重构的目标非常明确：

1. 先只做 chat 对话主链路
2. 先只保留 4 个核心 service
3. 先不追求大而全
4. 先把同步对话闭环打通
5. 记忆层先做最小可用方案

一句话：

**先做轻，先做通，再做复杂。**

---

## 2. 当前阶段只保留 4 个 service

当前 Agent 层只保留下面 4 个 service，其他相关 service 暂时不要继续扩展。

### 2.1 AgentChatService

职责：

- chat 总入口
- 读取会话上下文
- 构建本轮上下文
- 调用场景服务
- 回写消息
- 返回响应

不负责：

- 场景内部业务
- workflow 细节
- tool 细节

### 2.2 AgentSessionService

职责：

- 获取或创建会话
- 读取历史消息
- 保存用户消息
- 保存助手消息
- 更新标题
- 维护会话摘要

不负责：

- 场景判断
- workflow 执行
- tool 调用

### 2.3 AgentSceneService

职责：

- 判断当前请求属于哪个场景
- 直接执行对应场景
- 返回统一执行结果

说明：

这里直接合并原来的“路由”和“分发”职责。

当前阶段只需要先支持：

1. `TENDER_REVIEW`
2. `UNKNOWN`

其他场景先不要展开。

### 2.4 AgentResponseService

职责：

- 将执行结果统一装配成 `AgentChatResp`

不负责：

- 路由
- 会话
- 场景执行

---

## 3. 当前阶段先去掉的东西

在 chat 主链路稳定之前，下面这些不要继续加重：

1. 额外的 dispatcher / manager / facade
2. 复杂的多层 memory 抽象
3. 复杂的 SSE 主链路
4. 过度泛化的多场景统一框架
5. 提前做完所有场景
6. 为未来扩展准备的大量空壳代码

原则：

**没有形成稳定闭环前，不为了“看起来完整”而加层。**

---

## 4. 轻量版 chat 主流程

当前只实现同步对话主流程：

```text
AgentController
-> AgentChatService
-> AgentSessionService.loadSessionContext
-> AgentChatService.buildContext
-> AgentSceneService.decideAndExecute
-> AgentSessionService.saveUserMessage
-> AgentSessionService.saveAssistantMessage
-> AgentResponseService.buildResponse
-> 返回前端
```

这个阶段不要把主流程搞复杂。

只需要把这条链路打通。

---

## 5. 每个 service 的最小方法集

## 5.1 AgentChatService

当前阶段只保留：

```java
AgentChatResp chat(AgentChatReq req);
```

内部主流程固定为：

```java
loadSessionContext
-> buildContext
-> sceneService.decideAndExecute
-> persistConversation
-> responseService.buildResponse
```

## 5.2 AgentSessionService

当前阶段只保留：

```java
ChatSession getOrCreateSession(String sessionId);
AgentSessionContext loadSessionContext(String sessionId);
void saveUserMessage(String sessionId, String content, String metadata);
void saveAssistantMessage(String sessionId, String content, String metadata, String type);
void updateSessionSummary(String sessionId, String summary);
void updateSessionTitleIfNeeded(String sessionId, String query);
```

## 5.3 AgentSceneService

当前阶段只保留：

```java
AgentSceneExecution decideAndExecute(AgentChatContext context, AgentChatReq req);
```

返回对象只需要包含：

- `WorkflowRouteDecision decision`
- `AgentExecutionResult executionResult`

## 5.4 AgentResponseService

当前阶段只保留：

```java
AgentChatResp buildResponse(
    AgentChatContext context,
    WorkflowRouteDecision decision,
    AgentExecutionResult executionResult
);
```

---

## 6. memory 记忆层最小方案

现在不要把记忆层做重。

当前阶段只做下面 3 件事：

### 6.1 全量消息存数据库

保留所有用户消息和助手消息，用于：

- 前端回放
- 排查问题
- 审计留痕

### 6.2 每次执行只取最近消息窗口

不要每次把全量历史都发给模型。

当前建议：

- 最近 10 到 20 条消息

### 6.3 每个 session 保留一份 summary

summary 只做简单精炼即可，当前阶段够用就行。

summary 用于：

- 长会话压缩
- 下次构建上下文时快速带入

---

## 7. 当前阶段暂不做的 memory 能力

先不要做：

1. 向量记忆
2. 跨会话记忆
3. 复杂记忆检索策略
4. 自动多层摘要压缩
5. 推理型长期记忆系统

原因很简单：

这些都不是当前阶段的主矛盾。

当前主矛盾是：

**chat 主链路还没有完全稳定。**

---

## 8. 当前阶段的场景策略

当前只优先打通一个场景：

### 标书审查

目标：

- 用户发起对话
- Agent 可以识别到 `TENDER_REVIEW`
- 可以进入标书审查执行链路
- 最终能返回 `answer + report + evidence`

当前阶段不要求：

- 所有场景都完善
- 所有接口都统一
- 所有流式能力都完成

---

## 9. 开发顺序

必须按这个顺序来，不要跳。

### 第一步

完成 `AgentChatService.chat()`

要求：

- 主流程清晰
- 不要堆场景细节
- 不要直接写重业务

### 第二步

完成 `AgentSessionService`

要求：

- session 获取与创建
- 历史消息读取
- 用户/助手消息保存
- 标题更新
- summary 更新

### 第三步

完成 `AgentSceneService`

要求：

- 合并场景判断和分发
- 当前只支持 `TENDER_REVIEW` 和 `UNKNOWN`

### 第四步

完成 `AgentResponseService`

要求：

- 保证前端拿到统一 `AgentChatResp`

### 第五步

只打通标书审查这一条完整链路

---

## 10. 当前阶段的明确约束

后续 AI 开发时，必须遵守：

1. 不要新增超过这 4 个核心 service 的上层结构
2. 不要提前做复杂抽象
3. 不要为了未来扩展制造大量空壳类
4. 不要把场景细节塞回 `AgentChatService`
5. 不要把会话逻辑和场景逻辑混在一起
6. 不要把 memory 做成复杂系统

---

## 11. 一句话结论

当前阶段的唯一重点不是“做完整个智能体系统”，而是：

**先用最少的 service，把 chat 主链路和一个核心场景打通。**
