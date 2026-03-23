# Drug Agent - 横渡智能监管系统

## 项目简介

横渡智能监管系统是一款 AI 驱动的医药监管智能体平台。基于 Spring Boot + Spring AI Alibaba，提供多场景路由、文档解析与合规风险识别能力。

## 任务看板升级改造

本次升级将场景一标书审查的任务看板从"技术列表"升级为"业务任务调度与处置中枢"。

### 核心目标

- **3秒可读性**: 让用户3秒内看懂每个任务是什么、状态如何、是否要处理
- **视觉优先级**: 风险驱动视觉优先级，高风险任务一目了然
- **操作闭环**: 提供完整的操作闭环入口

### 核心功能

- [x] 任务卡片可读性改造
- [x] 状态模型与阶段进度展示
- [x] 风险驱动视觉优先级
- [x] 筛选/排序/统计能力
- [x] 操作闭环入口

## 技术栈

### 后端

- **框架**: Spring Boot 3.4
- **AI**: Spring AI Alibaba (通义千问)
- **数据库**: MySQL 8.0 + MyBatis Plus 3.5
- **文档**: Knife4j (Swagger UI)
- **Java**: 17

### 前端

- **框架**: Vue 3 + Composition API
- **构建**: Vite 5
- **样式**: Tailwind CSS 3
- **状态**: Pinia
- **路由**: Vue Router 4
- **图标**: Lucide Vue Next

## 快速开始

### 1. 启动 MySQL

```bash
docker-compose up -d mysql
```

### 2. 初始化数据库

```bash
mysql -h localhost -u root -proot123 -e "CREATE DATABASE IF NOT EXISTS drug_agent"
mysql -h localhost -u root -proot123 drug_agent < schema.sql
mysql -h localhost -u root -proot123 drug_agent < taskboard-schema.sql
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 填入实际配置
```

### 4. 启动后端

```bash
./mvnw spring-boot:run
```

### 5. 启动前端

```bash
cd drug-agent-web
npm install
npm run dev
```

### 6. 访问

- 后端 API: http://localhost:8080
- Swagger 文档: http://localhost:8080/doc.html
- 前端页面: http://localhost:3000

## 项目结构

```
drug-agent/
├── src/main/java/com/liang/drugagent/
│   ├── config/          # 配置类
│   ├── controller/       # 控制器
│   │   └── TaskBoardController.java  # 任务看板API
│   ├── service/          # 服务层
│   │   └── TaskCardService.java      # 任务卡片服务
│   ├── mapper/          # MyBatis Mapper
│   │   └── TaskCardMapper.java
│   ├── domain/          # 领域模型
│   │   ├── entity/     # 数据库实体
│   │   │   └── TaskCard.java
│   │   ├── req/        # 请求DTO
│   │   │   ├── TaskCardReq.java
│   │   │   └── TaskCardQueryReq.java
│   │   └── resp/       # 响应VO
│   │       ├── TaskCardVO.java
│   │       └── TaskStatisticsVO.java
│   └── enums/          # 枚举类
│       ├── TaskStatusEnum.java
│       └── RiskLevelEnum.java
├── drug-agent-web/
│   └── src/
│       ├── components/
│       │   └── taskboard/  # 任务看板组件
│       │       ├── TaskBoard.vue
│       │       ├── TaskCard.vue
│       │       ├── FilterBar.vue
│       │       ├── StatsPanel.vue
│       │       ├── TaskDetailDrawer.vue
│       │       ├── CreateTaskModal.vue
│       │       └── EmptyState.vue
│       ├── api/
│       │   └── taskboard.js
│       ├── stores/
│       │   └── taskboard.js
│       └── views/
│           └── TaskBoardView.vue
├── schema.sql           # 基础数据库schema
├── taskboard-schema.sql # 任务看板数据库schema
├── docker-compose.yml   # Docker配置
└── .env.example         # 环境变量示例
```

## API 文档

### 任务看板 API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/task-board/statistics | 获取看板统计 |
| GET | /api/task-board/tasks | 获取任务列表（分页） |
| GET | /api/task-board/tasks/{taskId} | 获取任务详情 |
| POST | /api/task-board/tasks | 创建新任务 |
| PATCH | /api/task-board/tasks/{taskId}/status | 更新任务状态 |
| PATCH | /api/task-board/tasks/{taskId}/progress | 更新任务进度 |
| GET | /api/task-board/filter-options | 获取筛选项 |

### 响应示例

#### TaskCardVO

```json
{
  "id": "tc-001",
  "taskName": "2024年度药品采购标书审查",
  "taskType": "TENDER_REVIEW",
  "taskTypeText": "标书审查",
  "status": "COMPLETED",
  "statusText": "已完成",
  "progress": 100,
  "riskLevel": "HIGH",
  "riskLevelText": "高风险",
  "score": 65,
  "scoreGrade": "不合格",
  "summary": "发现3处高风险项，包括报价异常和资质造假嫌疑",
  "riskItemCount": 5,
  "unhandledRiskCount": 2,
  "createdAtAgo": "2天前",
  "availableActions": ["VIEW", "EXPORT", "ARCHIVE"]
}
```

#### TaskStatisticsVO

```json
{
  "totalCount": 100,
  "todayNewCount": 5,
  "todayCompletedCount": 8,
  "pendingCount": 10,
  "runningCount": 5,
  "completedCount": 80,
  "failedCount": 5,
  "highRiskCount": 12,
  "mediumRiskCount": 25,
  "lowRiskCount": 43,
  "completionRate": 80.0,
  "avgScore": 72.5
}
```

## 后续迭代计划

### Phase 1: 基础能力 (当前)
- [x] 任务卡片UI改造
- [x] 状态与进度展示
- [x] 筛选与排序
- [x] 基础统计面板

### Phase 2: 增强功能
- [ ] SSE实时任务进度推送
- [ ] 任务批量操作
- [ ] 任务收藏与关注
- [ ] 任务模板支持

### Phase 3: 高级功能
- [ ] AI智能任务分类
- [ ] 风险自动评估
- [ ] 执行日志详情
- [ ] 报告导出（PDF/Word）

### Phase 4: 性能与协作
- [ ] 任务分页优化
- [ ] 前端缓存优化
- [ ] 协作 @ 提及
- [ ] 任务评论

---

## 原有项目文档

### 启动

服务启动后监听 `http://localhost:8124`，所有接口统一前缀 `/api`。

### Swagger UI

```
http://localhost:8124/api/swagger-ui.html
```

### 测试

```bash
# 全量测试
mvn test

# 仅 tenderreview 模块测试
mvn test -Dtest="InMemoryCaseStoreTest,CaseServiceTest,DocxParserTest,DocumentParseServiceTest,TenderReviewControllerTest"
```

## License

MIT License
