# Agent 层轻量化收敛改造计划

> 文档版本：v1.0
> 更新时间：2026-03-26
> 目标：在最短时间内把 Agent chat 主链路收敛到可运行的 MVP 形态

## 1. 改造背景

当前 Agent 层虽然已经开始朝新方案收敛，但整体仍然存在以下问题：

1. 新旧方案并存
2. service 职责边界不清
3. 会话层和场景层仍有耦合
4. chat 主链路还没有真正稳定闭环
5. 文件链路、流式链路、复杂路由链路同时存在，超出当前 MVP 范围

当前阶段的首要目标不是继续扩展，而是：

**先把同步 chat 主链路和标书审查第一个场景打通。**

---

## 2. 改造目标

本次改造只做一件事：

**将 Agent 层收敛成最小可运行结构，确保用户发起一次对话后，系统可以完成会话读取、场景判断、场景执行、结果回写和统一响应。**

---

## 3. 改造范围

本次只改造上层 Agent 编排层：

- `AgentChatService`
- `AgentSessionService`
- `AgentSceneService`
- `AgentResponseService`

相关辅助类按需调整，但不扩展新的复杂体系。

---

## 4. 目标结构

当前阶段只保留 4 个核心 service：

### 4.1 AgentChatService

职责：

- 对话总入口
- 读取会话上下文
- 构建本轮上下文
- 调用场景服务
- 回写消息
- 返回统一响应

### 4.2 AgentSessionService

职责：

- 获取或创建会话
- 加载历史消息和摘要
- 保存用户消息
- 保存助手消息
- 更新会话标题
- 更新会话摘要

### 4.3 AgentSceneService

职责：

- 判断当前请求属于哪个场景
- 执行对应场景
- 返回统一执行结果

当前阶段只支持：

1. `TENDER_REVIEW`
2. `UNKNOWN`

### 4.4 AgentResponseService

职责：

- 将执行结果统一装配成 `AgentChatResp`

---

## 5. 必须删除或停用的冗余内容

以下内容当前阶段不应继续进入主链：

### 5.1 从 AgentChatService 中去掉

1. 直接依赖 `ChatMemoryService`
2. 自己拼历史消息
3. 自己构建会话摘要
4. 自己处理场景细节

要求：

`AgentChatService` 只能通过 `AgentSessionService` 读取和回写会话数据。

### 5.2 从 AgentSessionService 中清理

1. 旧的 `scene` 会话语义
2. 与当前表结构不一致的字段使用
3. 不必要的 Controller 交互复杂逻辑

要求：

会话层只负责会话和消息，不负责场景逻辑。

### 5.3 从 AgentSceneService 中删减

1. 文件上传链路主流程
2. 流式分发主流程
3. 未完成的多场景分支
4. 过重的扩展性预留

要求：

当前阶段只保留：

- 同步 chat 场景判断
- `TENDER_REVIEW`
- `UNKNOWN`

### 5.4 停用旧类

以下旧类如果还存在引用，应逐步停用或移出主链：

- `AgentRouteService`
- `AgentSceneDispatcher`

说明：

当前方案已经决定收敛为 `AgentSceneService`，不要再让 route 和 dispatch 双轨并存。

---

## 6. 数据库与会话模型同步改造

当前会话模型已经调整方向，接下来必须让 service 使用新语义。

### 6.1 chat_session

新语义：

- 不再绑定固定 `scene`
- 使用 `last_scene`
- 使用 `summary`
- 使用 `message_count`
- 使用 `last_message_at`

### 6.2 chat_message

保留字段：

- `role`
- `scene`
- `content`
- `metadata`
- `type`

### 6.3 会话层约束

`AgentSessionService` 必须基于新的表结构读写，不允许继续使用旧 `scene` 语义。

---

## 7. 记忆层当前阶段方案

当前阶段不要实现复杂记忆系统。

只做最小可用：

1. 全量消息入库
2. 每次只取最近 10~20 条消息
3. 使用 `chat_session.summary` 保存简要摘要
4. 摘要更新逻辑可以先简单，后续再优化

当前阶段不做：

1. 向量记忆
2. 跨会话记忆
3. 复杂压缩策略
4. 多层记忆召回

---

## 8. 主链路目标形态

本次改造后的同步对话链路必须固定为：

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

当前阶段不允许在主链路中再混入：

- 文件上传主链
- SSE 主链
- 多场景复杂扩展

---

## 9. 分步执行计划

### 第一步：收敛 AgentChatService

目标：

- 正式实现 `chat(req)`
- 不再直接依赖 `ChatMemoryService`
- 只保留主流程编排

完成标准：

- `chat(req)` 可运行
- 主流程清晰
- 无场景细节泄漏

### 第二步：收敛 AgentSessionService

目标：

- 实现最小会话能力
- 与新表结构对齐

必须完成的方法：

- `getOrCreateSession`
- `loadSessionContext`
- `saveUserMessage`
- `saveAssistantMessage`
- `updateSessionSummary`
- `updateSessionTitleIfNeeded`

### 第三步：收敛 AgentSceneService

目标：

- 合并场景判断和场景执行
- 只保留同步主链

当前阶段必须只支持：

- `TENDER_REVIEW`
- `UNKNOWN`

### 第四步：打通标书审查场景

目标：

- 用户输入标书审查问题
- 可以进入标书审查链路
- 返回 `answer + report + evidence`

---

## 10. 当前阶段的明确约束

后续 AI 执行改造时，必须遵守：

1. 不新增新的上层核心 service
2. 不为了“未来扩展”增加大量空壳代码
3. 不提前恢复文件主链和 SSE 主链
4. 不继续让 session 和 scene 强绑定
5. 不继续把会话逻辑分散到多个 service
6. 不在 `AgentChatService` 中直接写场景执行细节

---

## 11. 验收标准

改造完成后，至少应满足：

1. `/agent/chat` 可以正常工作
2. 能正确读取或创建会话
3. 能保存用户消息和助手消息
4. 能识别 `TENDER_REVIEW`
5. 能进入标书审查执行链路
6. 能统一返回 `AgentChatResp`

---

## 12. 一句话结论

当前阶段不要继续做“大而全”的 Agent 体系，而是：

**先删冗余、先收职责、先打通 chat 和标书审查这一个最小闭环。**
