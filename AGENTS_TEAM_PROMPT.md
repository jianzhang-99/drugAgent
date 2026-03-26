我要做一个 **drug-agent-rebuild** 项目，请你作为一个由 **Team Lead（技术负责人）、后端工程师（Java Spring AI）、AI 架构师** 组成的小团队，帮我完成 **AgentChatService 重构** 开发。

## 参考文档
**必须严格按照 `doc/上层通用agent/技术文档/AgentChatService重构设计.md` 执行，该文档定义了：**
- AgentChatService 定位：前端会话请求的统一应用编排器
- 目标链路：AgentController -> AgentChatService -> AgentRouteService -> TenderReviewToolOrchestrator -> ReviewTenderTool -> TenderReviewWorkflow
- 必须拆分模块：AgentChatService、AgentSceneDispatcher、TenderReviewPreparationService、TenderReviewToolOrchestrator、AgentResponseAssembler、AgentSessionFacade
- AgentChatService 最终收敛方法结构：chat / fileChat / streamChat / buildContext / route / dispatch / finalizeResponse / fallback
- 标书审查场景主入口目标是 TenderReviewToolOrchestrator
- AgentChatService 不应该具备：场景专属数据装配、Tool注册与调用细节、Workflow内部业务逻辑、基础设施细节

项目目标：
- 将 AgentChatService 从"什么都做的总服务"重构为"前端会话请求的统一应用编排器"
- 核心功能包括：【统一接入与上下文构建】【统一路由分发】【TenderReviewPreparationService 拆分】【AgentSceneDispatcher 拆分】【AgentResponseAssembler 统一响应装配】【AgentSessionFacade 会话管理】

技术栈要求：
- 前端：无（纯后端重构）
- 后端：Java Spring Boot 3.x + Spring AI，Spring MVC
- 数据库：MySQL（会话存储）
- 认证：无（内部服务）
- AI：接入 Claude API / OpenAI SDK，输出结构化 JSON

工程规范：
- Java 17+，Lombok简化代码
- 模块化拆分：Controller / Service / Orchestrator / Tool / Workflow 分层清晰
- 请求对象统一以 Req 结尾，响应对象统一以 Resp 结尾
- 日志使用 @Slf4j，带 traceId / sessionId / scene 上下文
- 不在 Controller 新增 @CrossOrigin，统一全局 CORS
- 前端对话统一返回 DrugAgentResp
- 提供清晰包结构：agent.chat / agent.route / agent.orchestrator / agent.assembler / agent.session / tool / workflow
- Team Lead 需在每个阶段输出当前进度，并在阶段完成时给出阶段汇报

请按顺序输出：
1. 整体架构规划（展示重构前后对比，重点说明拆分后的模块职责）
2. 目标包结构（贴合 Spring Boot 项目）
3. 各模块核心接口定义（Java interface）
4. AgentChatService 重构代码（保留 chat/fileChat/streamChat 入口，拆分 buildContext/route/dispatch/finalizeResponse/fallback）
5. AgentSceneDispatcher 新增代码
6. TenderReviewPreparationService 新增代码
7. AgentResponseAssembler 新增代码
8. AgentSessionFacade 新增代码
9. 重构后的调用链路说明与测试验证方案

要求：
- 不要只给思路，要给实际代码
- 每段代码注明文件路径
- 优先最小可运行版本
- 再逐步扩展成完整项目
