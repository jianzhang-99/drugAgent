# Drug Agent - 医药监管AI系统

## 项目概述

横渡智能监管系统后端服务，提供医药监管领域的AI辅助审查能力。

## 技术栈

- **框架**: Spring Boot 3.4
- **AI**: Spring AI Alibaba (通义千问)
- **数据库**: MySQL 8.0 + MyBatis Plus 3.5
- **文档**: Knife4j (Swagger UI)

## 快速开始

### 1. 启动 MySQL
```bash
docker-compose up -d mysql
```

### 2. 初始化数据库
```bash
mysql -h localhost -u root -proot123 -e "CREATE DATABASE IF NOT EXISTS drug_agent"
# 执行 schema.sql 初始化表结构
```

### 3. 启动后端
```bash
./mvnw spring-boot:run
```

### 4. 访问 API 文档
http://localhost:8080/doc.html

## 核心模块

### 历史对话功能 (Chat History)

#### 数据模型
- **ChatSession**: 聊天会话，包含标题、场景、创建/更新时间
- **ChatMessage**: 聊天消息，包含角色、内容、元数据

#### API 端点

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/sessions | 获取所有会话列表 |
| GET | /api/sessions/{id} | 获取会话详情（含消息） |
| POST | /api/sessions | 创建新会话 |
| PUT | /api/sessions/{id}/title | 更新会话标题 |
| DELETE | /api/sessions/{id} | 删除会话（软删除） |
| GET | /api/sessions/search?q=关键词 | 搜索会话 |
| GET | /api/sessions/{sessionId}/messages | 获取会话消息 |
| POST | /api/sessions/{sessionId}/messages | 发送消息 |

#### 数据库表

```sql
chat_session     -- 会话表
chat_message     -- 消息表
```

## 开发指南

### 添加新场景
1. 在 `SceneEnum` 中添加新场景类型
2. 在 `WorkflowRegistry` 中注册新工作流
3. 更新前端场景选择组件

### 添加新规则执行器
1. 继承 `AbstractTenderExecutor`
2. 实现 `execute()` 方法
3. 在相应 Engine 中注册

## 目录结构

```
drug-agent/
├── src/main/java/com/liang/drugagent/
│   ├── config/          # 配置类
│   ├── controller/     # 控制器
│   ├── service/        # 服务层
│   ├── mapper/         # MyBatis Mapper
│   ├── domain/         # 实体类
│   │   ├── entity/     # 数据库实体
│   │   ├── req/        # 请求DTO
│   │   └── resp/       # 响应DTO
│   ├── enums/          # 枚举类
│   ├── advisor/        # AI Advisor
│   ├── agent/          # Agent 逻辑
│   ├── workflow/       # 工作流
│   ├── engine/         # 规则引擎
│   └── executor/       # 执行器
└── docker-compose.yml   # Docker 配置
```

## 后续迭代计划

### Phase 2: 增强功能
- [ ] AI 自动生成会话标题
- [ ] 会话导出（Markdown/PDF）
- [ ] 消息附件支持

### Phase 3: 高级功能
- [ ] 基于 Embedding 的语义搜索
- [ ] 跨设备同步（用户认证）
- [ ] 会话分享功能

### Phase 4: 性能优化
- [ ] 消息分页加载
- [ ] 缓存优化
- [ ] 数据库索引优化
