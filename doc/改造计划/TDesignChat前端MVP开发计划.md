# TDesign Chat 前端 MVP 开发计划

## 项目目标

**目标**：基于 `Vue 3 + TDesign Vue Next + @tdesign-vue-next/chat` 重建一个最小可用的 Agent 前端页面，先跑通“会话列表 + 对话区 + 输入发送 + 标书上传 + 结果展示”主链路。  
**阶段定位**：MVP 阶段，只解决“能稳定演示、能联调、能承载后续迭代”的问题，不追求一次性做成完整工作台。  
**建议周期**：5 到 7 个工作日。  
**适用范围**：仅针对 `drug-agent-web` 前端重建。  

## MVP 成功标准

满足以下条件即可认为本阶段完成：

1. 用户可以进入一个统一的 Agent 页面。
2. 用户可以创建会话、切换会话、查看历史消息。
3. 用户可以发送文本消息，并看到后端同步返回结果。
4. 用户可以上传文件，并走通 `/agent/submit` 链路。
5. 后端返回普通文本、澄清信息、结果卡片时，前端都能正确渲染。
6. 页面在桌面端可正常使用，移动端只要求基本不崩。
7. 目录结构、状态模型、消息模型收敛为一套，不再并存多套工作台实现。

## 当前约束

- 前端现状已经重置，不再沿用旧页面实现。
- 后端主链路当前以同步接口为主：
  - `POST /agent/chat`
  - `POST /agent/submit`
  - `GET /agent/sessions`
  - `GET /agent/sessions/{id}`
  - `GET /agent/sessions/{sessionId}/messages`
- 当前后端虽然有流式接口和澄清字段，但整体仍偏 MVP，同步链路优先级最高。
- 当前业务最成熟的场景是标书审查，合同预审与风险预警仍偏 prompt 驱动。

## 技术方案

### 技术栈

- 框架：`Vue 3`
- 构建：`Vite`
- 状态管理：`Pinia`
- UI 基础：`tdesign-vue-next`
- 聊天基础组件：`@tdesign-vue-next/chat`
- 网络请求：保留现有 `request.js`，统一收口 API 层

### 选型原则

1. 使用 TDesign 作为页面壳和基础交互组件来源。
2. 使用 TDesign Chat 承接对话区基础能力，而不是自己从零画聊天气泡。
3. 所有业务结构仍由我们自己定义，不被第三方组件的数据结构绑死。
4. 结果卡片、澄清卡片、上传面板这类业务组件优先自研薄封装。

## 明确不做

以下内容不进入本期 MVP：

- 不做复杂多栏工作台编排
- 不做任务中心、执行日志、Trace 时间线
- 不做完整 SSE 流式渲染闭环
- 不做复杂拖拽上传和批量管理
- 不做多角色权限体系
- 不做主题系统、深色模式、国际化
- 不做高保真视觉打磨
- 不做过度抽象的通用 Agent UI 引擎

## 信息架构

本期页面只保留 3 个一级区域：

1. 左侧会话栏
2. 中间对话主区域
3. 顶部轻量操作栏

不再引入右侧常驻复杂面板。报告、证据、结构化结果优先使用抽屉或弹层承载。

## 页面范围

### 1. Agent 主页面

承载整个 MVP 主交互，包含：

- 会话列表
- 新建会话
- 当前会话标题
- 消息列表
- 输入框
- 上传入口

### 2. 结果详情抽屉

仅在消息为结果卡片时打开，用于展示：

- 风险等级
- 分数
- 摘要
- steps
- evidenceList
- report 基础内容

### 3. 错误与空状态

- 无会话时空状态
- 无消息时欢迎态
- 请求失败时错误提示

## 核心数据模型

本期只保留一套前端消息模型：

### Session

- `id`
- `title`
- `scene`
- `updatedAt`

### Message

- `id`
- `role`
- `type`
- `content`
- `createdAt`
- `status`
- `attachments`
- `result`
- `raw`

### Message.type 约束

- `user_text`
- `assistant_text`
- `assistant_clarify`
- `assistant_result_card`
- `system_error`
- `uploading`

## 接口对接策略

### 文本对话

前端调用 `/agent/chat`：

- 入参：`query + sessionId + userId + sceneHint`
- 出参：映射为统一 `Message`

### 文件上传

前端调用 `/agent/submit`：

- `multipart/form-data`
- 字段：`query / sessionId / userId / sceneHint / submittedBy / files`

### 会话加载

- 会话列表：`GET /agent/sessions`
- 会话详情：`GET /agent/sessions/{id}`
- 消息列表：优先直接用详情接口返回的 `messages`

## 组件拆分

建议只保留以下核心组件：

1. `AgentPage.vue`
2. `SessionSidebar.vue`
3. `ChatPanel.vue`
4. `MessageRenderer.vue`
5. `ComposerBar.vue`
6. `UploadPanel.vue`
7. `ResultCard.vue`
8. `ResultDrawer.vue`

### 组件职责

`AgentPage.vue`

- 页面装配
- 初始加载
- 会话切换

`SessionSidebar.vue`

- 会话列表展示
- 新建会话
- 当前会话高亮

`ChatPanel.vue`

- 消息列表容器
- 欢迎态和空状态

`MessageRenderer.vue`

- 根据消息类型选择不同渲染

`ComposerBar.vue`

- 文本输入
- 发送
- 上传触发

`UploadPanel.vue`

- 文件选择
- 文件列表展示
- 上传前校验

`ResultCard.vue`

- 风险等级、摘要、查看详情

`ResultDrawer.vue`

- 展示报告和证据详情

## 状态管理收敛方案

建议只保留一个 Agent Store，例如：

`src/module/agent/store/agentStore.ts`

Store 只管理这几类状态：

- `sessions`
- `activeSessionId`
- `messagesBySession`
- `loading`
- `sending`
- `uploading`
- `currentResult`

以下旧状态实现不再继续并存：

- `chatStore.js`
- `session.js`
- 其他重复工作台 store

原则：本期只能存在一套会话状态来源。

## 开发阶段计划

## 阶段 1：骨架搭建与目录收敛

**目标**：项目能跑起来，基础目录与依赖明确。

| 任务 | 预计工作量 | 依赖 | 完成标准 |
|------|------------|------|----------|
| 安装并验证 `tdesign-vue-next` 与 `@tdesign-vue-next/chat` | 2h | 无 | 本地能启动并渲染基础 Chat 组件 |
| 清理旧工作台入口与重复目录引用 | 3h | 无 | 当前页面入口只指向一套 Agent 页面 |
| 新建 `module/agent` 统一目录结构 | 2h | 无 | 页面、组件、store、api 目录可用 |
| 建立基础路由和页面壳 | 2h | 依赖安装完成 | 能进入 Agent 主页面 |

**阶段产出**：

- 可运行的页面骨架
- 明确唯一入口
- 明确唯一状态目录

## 阶段 2：会话与消息主链路

**目标**：跑通最基本的文本对话。

| 任务 | 预计工作量 | 依赖 | 完成标准 |
|------|------------|------|----------|
| 封装 Agent API 层 | 2h | 阶段1 | `/agent/chat`、`/agent/sessions` 等接口有统一方法 |
| 定义统一 Session/Message 类型 | 2h | 阶段1 | 不再出现多套 message schema |
| 实现 Agent Store | 4h | API 层完成 | 会话切换、消息存取、发送状态可用 |
| 实现会话栏 | 4h | Store 完成 | 可加载会话、创建会话、切换会话 |
| 实现对话区与文本消息渲染 | 4h | Store 完成 | 可发送文本并看到响应 |

**阶段产出**：

- 文本对话可联调
- 会话列表可用
- 历史消息可展示

## 阶段 3：文件上传与结果卡片

**目标**：跑通最重要的业务场景展示。

| 任务 | 预计工作量 | 依赖 | 完成标准 |
|------|------------|------|----------|
| 实现上传面板 | 3h | 阶段2 | 可选择文件并显示待上传列表 |
| 对接 `/agent/submit` | 3h | 上传面板完成 | 可提交文件并拿到后端响应 |
| 实现结果卡片渲染 | 4h | 接口联调完成 | `assistant_result_card` 有独立展示 |
| 实现结果详情抽屉 | 4h | 结果卡片完成 | 可查看 report / evidence / steps |
| 实现澄清消息渲染 | 2h | MessageRenderer 完成 | `assistant_clarify` 有独立样式 |

**阶段产出**：

- 标书上传审查主链路跑通
- 报告摘要可见
- 结果详情可查看

## 阶段 4：MVP 收尾

**目标**：解决联调、空状态和基础体验问题。

| 任务 | 预计工作量 | 依赖 | 完成标准 |
|------|------------|------|----------|
| 完善错误提示和空状态 | 2h | 阶段2-3 | 请求失败和无数据场景可用 |
| 补基础 loading / sending / uploading 状态 | 2h | 阶段2-3 | 页面交互不突兀 |
| 做一次目录和组件收敛检查 | 2h | 阶段1-3 | 没有重复组件实现继续并存 |
| 自测 5 条核心场景 | 3h | 全部完成 | 核心交互无阻塞问题 |

## 关键依赖关系

```text
依赖安装
  -> 页面骨架
  -> API 层
  -> Store
  -> 会话栏 / 对话区
  -> 上传面板
  -> 结果卡片
  -> 结果抽屉
  -> 联调与收尾
```

## 核心验收场景

本期至少验证以下 5 条场景：

1. 首次进入页面，能看到欢迎态并成功创建新会话。
2. 输入普通文本问题，能成功收到普通回答。
3. 切换历史会话，能正确展示历史消息。
4. 上传 2 份标书文件，能收到审查结果卡片。
5. 打开结果详情，能看到风险等级、摘要、steps、evidence。

## 风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|----------|
| `@tdesign-vue-next/chat` 业务能力不完全贴合 | 中 | 中 | 仅将其作为聊天基础壳，业务卡片自行实现 |
| 后端响应结构不稳定 | 高 | 中 | 前端增加统一 mapper，避免页面直接依赖原始字段 |
| 旧代码残留再次造成双轨并存 | 高 | 高 | 本期严格保留一套页面入口和一套 store |
| 想一次做太多工作台能力 | 高 | 高 | 以 5 条验收场景为边界，超出范围全部延后 |
| 上传链路体验复杂度偏高 | 中 | 中 | MVP 只支持基础选择与提交，不做高级上传体验 |

## 开发注意事项

### 1. 先跑通，再美化

MVP 阶段先保证：

- 有页面
- 能发消息
- 能传文件
- 能看结果

视觉打磨放到下一阶段。

### 2. 先收敛模型，再写组件

必须先定：

- 会话模型
- 消息模型
- 响应映射规则

不要先写很多页面，再回头改数据结构。

### 3. 页面不做“超前设计”

本期不要为了未来能力预留太多抽象层，例如：

- 通用消息插件系统
- 动态场景注册 UI
- 超复杂渲染引擎
- 多工作台布局引擎

### 4. 所有新能力都要能回答一个问题

**“这项能力是否直接服务于 MVP 的 5 条验收场景？”**

如果不能，原则上不进入本期。

## 建议目录

```text
drug-agent-web/src/module/agent/
├── api/
│   └── agentApi.ts
├── components/
│   ├── AgentPage.vue
│   ├── SessionSidebar.vue
│   ├── ChatPanel.vue
│   ├── MessageRenderer.vue
│   ├── ComposerBar.vue
│   ├── UploadPanel.vue
│   ├── ResultCard.vue
│   └── ResultDrawer.vue
├── store/
│   └── agentStore.ts
├── types/
│   └── agent.ts
└── utils/
    └── messageMapper.ts
```

## 里程碑

| 里程碑 | 建议时间 | 成功标志 |
|--------|----------|----------|
| M1：页面骨架完成 | 第 1-2 天 | 页面可访问、依赖可用、目录收敛 |
| M2：文本对话完成 | 第 3-4 天 | 能创建会话并完成同步文本聊天 |
| M3：上传审查完成 | 第 5-6 天 | 能上传标书并展示结果卡片 |
| M4：MVP 可演示 | 第 6-7 天 | 5 条验收场景全部通过 |

## 下一阶段再考虑的内容

以下内容建议在 MVP 稳定后再做：

- 流式响应
- 更丰富的任务中心
- 结果对比视图
- 执行日志与 trace 面板
- 右侧证据常驻面板
- 场景化快捷操作
- 更完整的移动端体验

## 最终建议

这次前端重建的关键，不是“把页面做大”，而是“把主链路做窄、做稳、做干净”。

本期只围绕一个核心目标推进：

**用 TDesign Chat 快速搭起可联调的最小 Agent 前端，并用一套清晰的数据模型承接后续演进。**
