# Agent工作台前端改造 - 任务追踪

**项目**: Drug Agent 前端工作台重构
**阶段**: 阶段一 - 原型还原版
**创建日期**: 2026-03-25
**状态**: 进行中

---

## 阶段一任务清单

### TASK-A: 工作台骨架与布局
| 子任务 | 描述 | 状态 | 负责人 |
|--------|------|------|--------|
| A-1 | 创建 AgentWorkbenchPage.vue 主页面框架 | **已完成** | 前端工程师 |
| A-2 | 创建 AgentSidebar.vue 左侧导航组件 | **已完成** | 前端工程师 |
| A-3 | 创建 AgentTopBar.vue 顶部栏组件 | **已完成** | 前端工程师 |
| A-4 | 创建 TaskCenterPopover.vue 任务中心弹出面板 | **已完成** | 前端工程师 |
| A-5 | 创建 ComposerBar.vue 底部输入区组件 | **已完成** | 前端工程师 |

### TASK-B: 消息时间线系统
| 子任务 | 描述 | 状态 | 负责人 |
|--------|------|------|--------|
| B-1 | 定义统一消息模型 types/chatMessage.ts | **已完成** | 前端工程师 |
| B-2 | 创建 MessageRenderer.vue 统一消息渲染入口 | **已完成** | 前端工程师 |
| B-3 | 创建 UserMessageBubble.vue 用户消息气泡 | **已完成** | 前端工程师 |
| B-4 | 创建 AssistantTextMessage.vue AI文本消息 | **已完成** | 前端工程师 |
| B-5 | 创建 AssistantProgressMessage.vue AI进度消息 | **已完成** | 前端工程师 |
| B-6 | 创建 AssistantClarifyMessage.vue AI澄清消息 | **已完成** | 前端工程师 |
| B-7 | 实现消息替换机制 (progress -> final) | **已完成** | 前端工程师 |

### TASK-C: 报告卡与报告抽屉
| 子任务 | 描述 | 状态 | 负责人 |
|--------|------|------|--------|
| C-1 | 创建 AssistantResultCard.vue 结果卡片组件 | **已完成** | 前端工程师 |
| C-2 | 重构 ReportDrawer.vue 右侧报告抽屉 | **已完成** | 前端工程师 |
| C-3 | 实现报告高亮选中态 | **已完成** | 前端工程师 |

### TASK-D: 状态与Mock服务
| 子任务 | 描述 | 状态 | 负责人 |
|--------|------|------|--------|
| D-1 | 创建 agentStore.ts 统一状态管理 | **已完成** | 前端工程师 |
| D-2 | 创建 mockService.ts Mock数据服务 | **已完成** | 前端工程师 |
| D-3 | 实现完整的Mock对话流程 | **已完成** | 前端工程师 |

---

## 阶段一完成标准

- [ ] 原型核心布局还原度达到 85% 以上
- [ ] 演示链路可稳定连续执行
- [ ] 新建会话稳定
- [ ] 历史会话切换稳定
- [ ] 发送消息只产生一条用户消息和一条 Agent 执行态
- [ ] Agent 执行态会被最终消息替换，不残留重复 loading
- [ ] 标书审查结果以结果卡形式出现在时间线中
- [ ] 点击结果卡可以打开右侧报告抽屉
- [ ] 顶部任务中心能展示后台任务状态

---

## 已创建的文件结构

```
drug-agent-web/src/
├── components/agent/                    # 主工作台组件（已存在，可工作）
│   ├── AgentWorkbenchPage.vue
│   ├── AgentSidebar.vue
│   ├── AgentTopBar.vue
│   ├── ChatTimeline.vue
│   ├── ComposerBar.vue
│   ├── ReportDrawer.vue
│   ├── MessageRenderer.vue
│   ├── UserMessageBubble.vue
│   ├── AssistantProgressMessage.vue
│   ├── AssistantTextMessage.vue
│   ├── AssistantClarifyMessage.vue
│   ├── AssistantResultCard.vue
│   ├── SystemErrorMessage.vue
│   ├── TaskCenterPopover.vue
│   └── mockService.ts
├── store/agent/
│   └── types.ts                         # 类型定义
└── module/agent/workbench/             # 新创建的组件（备用）
    ├── AgentWorkbenchPage.vue
    ├── AgentSidebar.vue
    ├── AgentTopBar.vue
    ├── ComposerBar.vue
    ├── TaskCenterPopover.vue
    ├── ReportDrawer.vue
    ├── ChatTimeline.vue
    ├── MessageRenderer.vue
    ├── UserMessageBubble.vue
    ├── AssistantProgressMessage.vue
    ├── AssistantTextMessage.vue
    ├── AssistantClarifyMessage.vue
    ├── AssistantResultCard.vue
    ├── SystemErrorMessage.vue
    ├── index.ts
    ├── store/agentStore.ts
    ├── types/chatMessage.ts
    └── mock/mockService.ts
```

---

## 进度日志

### 2026-03-25
- [x] 阶段一启动
- [x] 完成项目结构分析和任务分解
- [x] 创建任务追踪文档
- [x] 完成类型定义 chatMessage.ts
- [x] 完成 agentStore.ts 统一状态管理
- [x] 完成 mockService.ts Mock数据服务
- [x] 完成所有工作台组件（位于 /components/agent/）
- [x] 完成所有消息组件
- [ ] 待验证：启动开发服务器进行演示测试
