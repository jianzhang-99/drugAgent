# Agent工作台前端改造计划

## 1. 改造背景

当前前端已经具备基础工作台能力，但整体实现仍然偏“接口驱动拼页面”，与期望中的 Agent 产品形态还有明显差距。

本次前端改造以 [原型.vue](/Users/liangjiajian/Desktop/医药监管AI项目/drug-agent/drug-agent-web/src/原型.vue) 为主要参考，目标不是简单照抄样式，而是把原型中已经验证过的交互方式整理成一套可持续开发的工程方案。

本次改造的核心判断是：

1. 标书审查不是单独页面流程
2. 标书审查、合同预审、风险预警都应当是 Agent 对话中的一种结果形态
3. 工作台的主线应该是“会话时间线 + 任务中心 + 右侧结果抽屉”
4. 前端必须先形成稳定的消息模型，再去接真实后端

---

## 2. 前端目标

本次前端改造的目标如下：

1. 将工作台重构为统一的 Agent 对话工作台
2. 保留左侧导航、历史会话、顶部任务中心、右侧报告抽屉四个核心区域
3. 将所有结果统一收敛为消息时间线中的消息类型
4. 优先实现前端交互效果，后端先只负责满足契约接口
5. 为后续接真实工作流保留稳定的组件边界与状态模型

前端“完成”的定义不是把所有接口都接上，而是达到下面的演示标准：

1. 新建会话稳定
2. 历史会话切换稳定
3. 发送消息只产生一条用户消息和一条 Agent 执行态
4. Agent 执行态会被最终消息替换，不残留重复 loading
5. 标书审查结果以结果卡形式出现在时间线中
6. 点击结果卡可以打开右侧报告抽屉
7. 顶部任务中心能展示后台任务状态
8. 页面视觉和交互节奏与原型基本一致

---

## 3. 原型拆解

根据 [原型.vue](/Users/liangjiajian/Desktop/医药监管AI项目/drug-agent/drug-agent-web/src/原型.vue)，前端工作台应拆成以下四个一级区域：

### 3.1 左侧区域

左侧区域负责全局导航和会话入口，包含：

1. 品牌区
2. 新建会话按钮
3. 一级导航
4. 历史会话分组列表
5. 设置入口
6. 侧边栏折叠按钮

### 3.2 中间主工作区

中间区域是产品主线，包含：

1. 顶部标题栏
2. 活跃任务按钮
3. 会话时间线
4. 空态快捷入口
5. 底部输入区

### 3.3 顶部任务中心

任务中心负责展示后台运行中的任务状态，属于独立信息流，不直接替代聊天结果。

它的价值是：

1. 告知用户 Agent 仍在执行
2. 展示多任务并发能力
3. 为后续接入更复杂的 workflow trace 做预留

### 3.4 右侧报告抽屉

右侧抽屉用于承载结构化报告详情，不应再弹 Modal，也不应跳详情页。

建议保持原型中的结构：

1. 风险等级
2. 综合分值
3. 结果摘要
4. 管理摘要
5. 建议动作
6. 执行步骤

---

## 4. 前端状态设计

本次改造必须先统一状态模型，避免继续在页面里以 `role + result + isLoading` 的方式临时拼装。

### 4.1 会话模型

建议前端统一维护两类会话数据：

1. `SessionSummary`
2. `SessionDetail`

其中：

- `SessionSummary` 用于左侧列表
- `SessionDetail` 用于中间时间线渲染

建议结构：

```ts
type SessionSummary = {
  id: string
  title: string
  scene: string
  dateGroup: string
  updatedAt: string
}

type SessionDetail = SessionSummary & {
  messages: ChatMessage[]
}
```

### 4.2 消息模型

消息模型是本次前端重构的核心。

建议统一为：

```ts
type ChatMessage =
  | {
      id: string
      role: 'user'
      type: 'user_text'
      content: string
      attachments?: string[]
      createdAt: string
    }
  | {
      id: string
      role: 'assistant'
      type: 'assistant_progress'
      content: string
      status: 'running'
      createdAt: string
    }
  | {
      id: string
      role: 'assistant'
      type: 'assistant_text'
      content: string
      createdAt: string
    }
  | {
      id: string
      role: 'assistant'
      type: 'assistant_clarify'
      content: string
      createdAt: string
    }
  | {
      id: string
      role: 'assistant'
      type: 'assistant_result_card'
      content: string
      result: ReportSummary
      createdAt: string
    }
  | {
      id: string
      role: 'system'
      type: 'system_error'
      content: string
      createdAt: string
    }
```

重点原则：

1. 前端只认 `type`
2. 不再根据 `answer`、`summary`、`report` 去猜渲染方式
3. 一次请求只允许有一个当前执行中的 `assistant_progress`

### 4.3 任务模型

顶部任务中心和右侧任务信息采用独立模型：

```ts
type TaskItem = {
  id: string
  name: string
  scene: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  progress: number
  updatedAt: string
  traceId?: string
}
```

### 4.4 报告模型

右侧抽屉展示的详情建议统一为：

```ts
type ReportSummary = {
  traceId: string
  scene: string
  riskLevel: string
  score: number
  docCount: number
  summary: string
}

type ReportDetail = ReportSummary & {
  managementSummary: string[]
  suggestedActions: string[]
  steps: string[]
  evidenceList?: string[]
}
```

---

## 5. 组件拆分方案

建议本次前端不要继续把大量逻辑堆在 `WorkspacePage.vue`，而是按工作台结构拆分组件。

### 5.1 一级组件

建议新增或重构为：

1. `AgentWorkbenchPage`
2. `AgentSidebar`
3. `AgentTopBar`
4. `TaskCenterPopover`
5. `ChatTimeline`
6. `ComposerBar`
7. `ReportDrawer`

### 5.2 二级组件

建议继续拆分：

1. `SessionHistoryGroup`
2. `QuickActionGrid`
3. `MessageRenderer`
4. `UserMessageBubble`
5. `AssistantTextMessage`
6. `AssistantClarifyMessage`
7. `AssistantProgressMessage`
8. `AssistantResultCard`
9. `TaskProgressItem`
10. `ReportSummarySection`
11. `ReportActionSection`
12. `ExecutionTraceSection`

### 5.3 渲染原则

会话时间线应使用单一渲染入口：

```vue
<MessageRenderer :message="message" />
```

不要在页面层继续散落多层 `v-if / v-else-if / result / isLoading` 判断。

---

## 6. 交互流方案

### 6.1 新建会话

流程：

1. 用户点击“新建会话”
2. 清空当前选中报告
3. 切换到空态工作区
4. 输入框获得焦点

### 6.2 发送消息

流程：

1. 插入一条 `user_text`
2. 插入一条 `assistant_progress`
3. 发起请求
4. 收到响应后，将 `assistant_progress` 替换为最终消息

最终消息只可能是以下三种之一：

1. `assistant_text`
2. `assistant_clarify`
3. `assistant_result_card`

### 6.3 点击结果卡

流程：

1. 设置 `selectedReport`
2. 打开右侧 `ReportDrawer`
3. 高亮当前结果卡

### 6.4 切换历史会话

流程：

1. 根据 sessionId 加载会话详情
2. 清空当前 `selectedReport`
3. 保持聊天主区滚动位置正确

### 6.5 顶部任务中心

流程：

1. 点击活跃任务按钮打开 Popover
2. 展示运行中与最近完成任务
3. 后续可扩展点击任务定位到对应消息或报告

---

## 7. 前端分阶段实施计划

### 阶段一：原型还原版

目标：

1. 先把工作台结构和交互做出来
2. 所有数据先使用 mock
3. 保证演示体验稳定

任务：

1. 搭建 `AgentWorkbenchPage`
2. 重建左侧导航和历史会话区域
3. 重建顶部任务中心
4. 重建聊天时间线
5. 重建结果卡与右侧抽屉
6. 使用本地 mock service 驱动完整对话流程

完成标准：

1. 原型核心布局还原度达到 85% 以上
2. 演示链路可稳定连续执行

### 阶段二：状态收敛版

目标：

1. 将会话、消息、任务、报告模型统一
2. 清除当前页面中的临时状态拼装逻辑

任务：

1. 建立新的消息模型
2. 建立新的 store
3. 建立消息替换机制
4. 建立右侧抽屉状态管理

完成标准：

1. 不再出现重复 loading
2. 不再出现澄清消息和结果卡混渲
3. 不再出现会话切换后消息残留

### 阶段三：接口联调版

目标：

1. 在不破坏前端体验的前提下接入真实接口

任务：

1. 增加 adapter 层
2. 接入会话摘要接口
3. 接入会话详情接口
4. 接入发送消息接口
5. 接入任务接口
6. 接入报告详情接口

完成标准：

1. mock 与真实接口可切换
2. 页面不依赖后端字段猜测渲染

### 阶段四：演示稳定版

目标：

1. 达到投资人演示标准

任务：

1. 增加 Playwright 冒烟测试
2. 补充空态、错误态、断网态
3. 补充滚动定位与抽屉切换细节
4. 补充上传材料体验

完成标准：

1. 演示主链可重复运行
2. 不出现多消息、多 loading、布局错乱

---

## 8. 前端任务拆分建议

为了便于并行推进，建议拆成以下任务：

### 任务 A：工作台骨架与布局

包含：

1. 左侧导航
2. 顶部栏
3. 任务中心 Popover
4. 中间聊天容器
5. 右侧报告抽屉

### 任务 B：消息时间线系统

包含：

1. 消息模型定义
2. 消息渲染器
3. progress -> final message 替换机制
4. 会话切换

### 任务 C：报告卡与报告抽屉

包含：

1. 结果卡组件
2. 报告详情抽屉
3. 报告高亮选中态

### 任务 D：状态与 mock service

包含：

1. mock 会话接口
2. mock 消息接口
3. mock 任务接口
4. mock 报告接口

### 任务 E：联调与自动化

包含：

1. adapter 层
2. 接口映射
3. Playwright 冒烟测试

---

## 9. 风险与控制

### 风险一：继续沿用当前页面内条件拼装

影响：

1. 会继续出现重复消息
2. 会继续出现 loading 残留
3. 后续每增加一个场景都会更乱

控制方式：

1. 先统一消息模型
2. 先统一消息渲染器

### 风险二：前端直接吃后端业务字段

影响：

1. 接口一变前端就炸
2. 结果卡、澄清消息、普通问答难区分

控制方式：

1. 增加 adapter 层
2. 统一转为前端 message schema

### 风险三：过早接真实工作流

影响：

1. 体验问题会被后端波动放大
2. 演示期不稳定

控制方式：

1. 先做 mock 稳定版
2. 后接真实接口

---

## 10. 建议结论

当前阶段，前端的最优先事项不是继续修补单个 bug，而是以原型为准重建工作台主结构。

优先级建议如下：

1. 先重构前端消息模型
2. 再重构聊天工作台页面
3. 再补齐右侧报告抽屉
4. 再接真实后端

一句话总结：

**先把 Agent 工作台做成，再让后端去适配这个工作台，而不是反过来。**
