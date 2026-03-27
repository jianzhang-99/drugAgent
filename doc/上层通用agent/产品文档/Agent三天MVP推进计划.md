# Agent 三天 MVP 推进计划

> 文档版本：v1.0
> 更新时间：2026-03-26
> 目标：三天内打通“标书审查”最小闭环

## 总目标

三天内只做一件事：

**让用户可以发起一次标书审查请求，并稳定拿到结果。**

目标闭环：

```text
前端发请求
-> AgentController
-> AgentChatService
-> AgentSessionService
-> AgentSceneService
-> 标书审查执行链路
-> AgentResponseService
-> 返回 answer + report + evidence
```

---

## 开发原则

这三天只遵守 4 条原则：

1. 先打通一条链，不追求全
2. 只保留 4 个核心 service
3. 只做同步 chat，不做复杂 SSE
4. 只打通 `TENDER_REVIEW` 一个场景

---

## 当前阶段只保留的 4 个 service

| Service | 只负责什么 |
|---|---|
| `AgentChatService` | 总入口、组装主流程 |
| `AgentSessionService` | 会话、消息、摘要、标题 |
| `AgentSceneService` | 判断场景并执行 |
| `AgentResponseService` | 统一返回 `AgentChatResp` |

其余上层复杂结构先不要继续扩。

---

## 三天执行表

| 天数 | 主要目标 | 具体要做 | 完成标准 |
|---|---|---|---|
| Day 1 | 收敛 Agent 主链路 | 收敛 4 个核心 service；完成 `AgentChatService.chat()`；完成 `AgentSessionService` 最小能力；数据库会话表与实体对齐 | `/agent/chat` 可正常调用；能创建/读取 session；能保存用户消息和助手消息 |
| Day 2 | 打通标书审查场景 | `AgentSceneService` 只支持 `TENDER_REVIEW` 和 `UNKNOWN`；接通标书审查执行链；返回 `answer + report + evidence` | 输入标书审查问题后，后端能识别 `TENDER_REVIEW` 并返回结果，不再是空响应或通用兜底 |
| Day 3 | 前端联调与异常兜底 | 前端接聊天接口；展示 `answer/summary/report/evidence`；补基础异常提示；留时间调试字段映射 | 前端可以发起一次标书审查并展示结果；异常时不白屏，有明确提示 |

---

## 每天验收清单

## Day 1 验收

满足以下 5 条才算完成：

1. `AgentChatService.chat()` 已正式实现主流程
2. `AgentChatService` 不再直接处理会话底层细节
3. `AgentSessionService` 可以获取/创建会话
4. 用户消息和助手消息都能入库
5. `chat_session` / `chat_message` 表和实体一致

如果还有以下问题，说明 Day 1 没完成：

- 还在混用旧 route / dispatcher
- `AgentChatService` 还直接依赖底层存储细节
- session 和 scene 还强绑定

## Day 2 验收

满足以下 4 条才算完成：

1. `AgentSceneService` 可以识别 `TENDER_REVIEW`
2. 标书审查请求能进入你现有的审查链路
3. 返回结果中至少有 `answer`、`summary`、`report`、`evidenceList`
4. 非标书请求仍可走 `UNKNOWN` 兜底

如果还有以下问题，说明 Day 2 没完成：

- 仍然返回通用对话
- 审查链路没真正跑起来
- 结果字段不稳定

## Day 3 验收

满足以下 4 条才算完成：

1. 前端能调用 chat 接口
2. 能展示标书审查结果
3. 基础异常有提示
4. 可以完整跑一遍“请求 -> 返回 -> 展示”

如果还有以下问题，说明 Day 3 没完成：

- 只能后端自测，前端不能跑
- 前端字段映射错乱
- 出错直接空白或报红

---

## 这三天不要做的事

为了保证你能出 MVP，这几件事先不要做：

1. 不做复杂记忆系统
2. 不做向量记忆
3. 不做多场景全打通
4. 不做复杂 SSE
5. 不做大规模代码美化式重构
6. 不做过度抽象

---

## 当前阶段的记忆策略

只做最小可用：

| 内容 | 当前做法 |
|---|---|
| 全量消息 | 入数据库 |
| 模型上下文 | 最近 10~20 条消息 |
| 长对话压缩 | 预留 `summary` 字段，逻辑后补 |

一句话：

**先把消息存好，先把窗口取好，摘要先预留，不要现在做重。**

---

## 最终交付标准

本周结束时，不要求你做完完整智能体平台，只要求做到：

1. 有一个可用聊天入口
2. 有一个可用会话系统
3. 有一个打通的标书审查场景
4. 前端能展示结果
5. 出错时有基本兜底

---

## 一句话提醒

这三天的重点不是“把系统做完整”，而是：

**先把一条最小业务链路跑通。**
