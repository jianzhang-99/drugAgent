# Agent可用性测试体系完善 Team

## 项目目标

为 drug-agent 项目建立完整的 Agent 可用性测试体系，重点完善"可用性测试 + 场景链路测试 + 质量回归测试"。

## 核心问题背景

当前测试体系存在以下核心问题：
1. **测试偏单元，缺端到端** - 现有测试主要是 Executor、Service 单元测试，缺乏用户视角的端到端链路测试
2. **测试文档分散** - `Agent多轮会话与AI回归测试方案.md` 和 `测试计划.md` 是两份独立文档，没有形成统一体系
3. **路由测试覆盖不全** - `AgentSceneServiceRouteTest` 只覆盖了部分场景（S1-S10 中仅覆盖 MT-01~MT-06）
4. **缺少多轮对话测试** - 多轮会话是 Agent 系统最容易出问题的点，但目前几乎没有覆盖
5. **测试用例没有 P0/P1/P2 优先级**，导致问题修复没有方向，疲惫感强

## 技术栈上下文

- **项目类型**：Java Spring Boot 后端项目
- **核心架构**：AgentController -> AgentChatService -> AgentRouteService -> TenderReviewToolOrchestrator -> ReviewTenderTool -> TenderReviewWorkflow
- **测试框架**：JUnit 5 + Mockito + SpringBootTest
- **核心场景**：标书审查（TENDER_REVIEW）、合同预审（CONTRACT_REVIEW）、风险预警（RISK_WARNING）
- **关键实体**：AgentChatReq、AgentChatResp、WorkflowRouteDecision、SceneEnum

## 团队角色与职责

### Team Lead（测试架构师）

**职责**：
- 统筹协调四个 Agent 的工作
- 定义测试体系统一规范
- 审核最终输出的测试用例和文档
- 确保测试用例覆盖用户提出的所有核心场景
- 把控 P0/P1/P2 优先级划分

**边界**：不直接编写测试用例，负责审核和整合

---

### Agent 1：路由测试完善 Agent

**职责**：完善路由测试用例，覆盖 S1-S10 全部场景

**具体任务**：

1. 分析现有 `AgentSceneServiceRouteTest.java`，找出已覆盖和未覆盖的场景

2. 补充以下缺失的路由测试用例（每个用例需要完整的 given-when-then）：

   | 场景 | 用户输入 | 前置条件 | 期望路由 | 期望行为 |
   |-----|---------|---------|---------|---------|
   | S1 | 帮我看看这两份标书是否有围标风险 | 上传两份标书 | TENDER_REVIEW | 要求进入Tool Orchestrator |
   | S2 | 帮我看看这个合同有没有风险 | 上传合同 | CONTRACT_REVIEW | 不误进TENDER_REVIEW |
   | S3 | 最近这个企业有没有监管风险 | 未提供企业名 | RISK_WARNING | 说明需要企业名称 |
   | S4 | 这个文件帮我看看有没有问题 | 有上传文件 | 追问 | 不直接乱审 |
   | S5 | 帮我审一下这两份标书 | 未上传文件 | TENDER_REVIEW | 提示上传文件，不报错 |
   | S6 | 帮我审查围标 | 只上传一份标书 | TENDER_REVIEW | 明确说明材料不足，给出有限分析 |
   | S7 | 那具体风险点在哪里？ | 上一轮已完成审查 | TENDER_REVIEW | 沿用上次结果继续解释 |
   | S8 | 不是合同，是招标文件 | 上一轮误进了CONTRACT | TENDER_REVIEW | 重新路由 |
   | S9 | 你是谁 | 无 | DEFAULT | 简短说明能力 |
   | S10 | （空文本/超长文本/乱码） | 无 | DEFAULT | 有兜底响应，不抛异常 |

3. 为每个测试用例添加清晰的 javadoc 说明测试目的

4. 补充测试用例 ID 编号规则：`RT-S1-001`（Route Test - 场景1 - 第1个用例）

**输出文件**：
- `src/test/java/com/liang/drugagent/agent/chat/AgentSceneServiceRouteTest.java`（扩展）
- 补充新的测试方法

**边界**：只负责路由层测试，不涉及多轮会话上下文继承

---

### Agent 2：多轮对话测试 Agent

**职责**：设计多轮会话测试用例，验证上下文保持、场景切换、追问纠错等能力

**具体任务**：

1. 设计以下多轮链路测试用例：

   **链路 A：追问与上下文继承**
   - `MC-01`：标书审查完成 → 问"最严重的问题是什么" → 继续TENDER_REVIEW，不要求重新上传
   - `MC-02`：标书审查完成 → 问"把结论说得更通俗" → 继续TENDER_REVIEW，输出简化结论
   - `MC-03`：标书审查完成 → 问"具体证据是什么" → 继续TENDER_REVIEW，返回evidenceList

   **链路 B：场景切换**
   - `MC-04`：标书审查完成 → 问"你是什么模型" → 切换DEFAULT，不要求上传
   - `MC-05`：标书审查完成 → 问"你还能做什么" → 切换DEFAULT
   - `MC-06`：普通对话 → 问监管知识 → 再上传标书 → 切换TENDER_REVIEW

   **链路 C：纠错与重新路由**
   - `MC-07`：误进合同预审 → 说"这是标书不是合同" → 重新路由TENDER_REVIEW
   - `MC-08`：材料不足 → 补充上传文件 → 继续原场景

   **链路 D：异常与边界**
   - `MC-09`：审查过程中 → 问完全不相关问题 → 保持或切DEFAULT
   - `MC-10`：长对话后 → 问最开始的问题 → 保持上下文或正确路由

2. 每个测试用例需要验证：
   - 场景是否正确保持或切换
   - 是否不必要地要求重新上传文件
   - 是否正确复用上轮审查结果
   - 是否不把通用问题误判为业务问题

3. 测试实现建议：可以使用 SpringBootTest + MockMvc，模拟多轮请求

**输出文件**：
- 新建 `src/test/java/com/liang/drugagent/agent/chat/MultiTurnConversationTest.java`

**边界**：不涉及标书审查Workflow内部逻辑，只测试会话层路由和多轮上下文

---

### Agent 3：标书审查场景端到端测试 Agent

**职责**：设计标书审查场景的端到端测试，覆盖完整链路

**具体任务**：

1. 基于现有样本数据设计 E2E 测试：

   **样本覆盖**：
   - W-M1（报价梯度异常）- 高危样本
   - W-M4（版式模板同源）- 高危样本
   - N-1（完全独立标书）- 合规样本

2. 测试用例设计：

   | 用例ID | 样本 | 验证点 |
   |-------|-----|--------|
   | E2E-001 | W-M1双文件 | 完整链路：解析->规则命中->风险融合->报告生成->包含riskLevel和evidenceList |
   | E2E-002 | W-M4双文件 | 同源检测正确，输出关联性证据 |
   | E2E-003 | N-1双文件 | 不产生高危误报，输出低风险或无风险 |
   | E2E-004 | 单文件上传 | 提示材料不足，不崩溃 |
   | E2E-005 | 损坏文件上传 | 明确错误提示，不抛500 |

3. 验证输出完整性（每个E2E用例必须包含）：
   - `scene` = TENDER_REVIEW
   - `riskLevel`（HIGH/MEDIUM/LOW）
   - `evidenceList`（非空）
   - `summary`（有摘要）
   - `answer`（有可读回复）

4. 注意：`RagE2ETest` 被禁用是因为外部依赖，E2E-001~E2E-003 应使用 Mock 或内部测试

**输出文件**：
- 新建 `src/test/java/com/liang/drugagent/scene/tender_review/e2e/TenderReviewE2ETest.java`

**边界**：只负责标书审查场景的端到端测试，不涉及合同预审和风险预警

---

### Agent 4：测试文档整合 Agent

**职责**：整合所有测试文档，建立统一的测试体系文档

**具体任务**：

1. 创建统一的测试体系文档结构：

   ```
   doc/测试体系/
   ├── README.md                    # 测试体系总览
   ├── P0_核心回归测试集.md          # P0优先级用例
   ├── P1_场景链路测试集.md          # P1优先级用例
   ├── P2_质量回归测试集.md          # P2优先级用例
   ├── 路由测试用例.md               # S1-S10路由测试用例
   ├── 多轮对话测试用例.md           # MC-01~MC-10多轮链路
   └── 测试执行指南.md              # 如何运行测试
   ```

2. 建立测试用例编号规范：
   - `RT-` 路由测试（Route Test）
   - `MC-` 多轮会话（Multi-Conversation）
   - `E2E-` 端到端（End-to-End）
   - `UT-` 单元测试（Unit Test）

3. 编写 `doc/测试体系/README.md`，包含：
   - 测试目标：定义什么叫"好用"
   - 五层测试框架
   - P0/P1/P2 通过标准
   - 快速执行指南

4. 整理 P0 回归测试集（必须通过的核心用例）：

   **P0-路由类**：
   - RT-S1-001：标书审查首轮路由
   - RT-S5-001：缺文件时路由到澄清
   - RT-S9-001：通用问题不误触发工作流

   **P0-多轮类**：
   - MC-01：追问沿用上轮结果
   - MC-04：问模型切回DEFAULT

   **P0-端到端类**：
   - E2E-001：高危样本输出风险结论
   - E2E-003：合规样本不误报HIGH

5. 编写 `doc/测试体系/测试执行指南.md`：
   - 如何运行单元测试
   - 如何运行集成测试
   - 如何本地模拟E2E测试
   - 如何提交新的bad case到回归集

**输出文件**：
- `doc/测试体系/README.md`
- `doc/测试体系/P0_核心回归测试集.md`
- `doc/测试体系/P1_场景链路测试集.md`
- `doc/测试体系/P2_质量回归测试集.md`
- `doc/测试体系/路由测试用例.md`
- `doc/测试体系/多轮对话测试用例.md`
- `doc/测试体系/测试执行指南.md`

**边界**：不直接编写Java测试代码，负责文档整理

---

## 协作流程

1. **Agent 1（路由）和 Agent 2（多轮）并行工作**，分别完善路由测试和多轮测试用例
2. **Agent 3（E2E）** 在 Agent 1/2 完成后，基于路由测试用例设计端到端覆盖
3. **Agent 4（文档）** 在 Agent 1/2/3 完成后，整合所有输出，建立统一文档结构
4. **Team Lead** 审核最终输出，确保没有重复和遗漏

## 验收标准

### 路由测试完善验收
- [ ] S1-S10 全部10个场景有对应测试用例
- [ ] 每个测试用例有清晰的 given-when-then
- [ ] 测试用例ID编号规范统一

### 多轮对话测试验收
- [ ] MC-01~MC-10 全部10个链路有对应测试
- [ ] 每个链路验证了场景保持/切换正确性
- [ ] 测试模拟了上下文继承场景

### 端到端测试验收
- [ ] W-M1、W-M4、N-1 样本有E2E覆盖
- [ ] 每个E2E用例验证了输出完整性
- [ ] 异常场景（缺文件、损坏文件）有覆盖

### 文档整合验收
- [ ] 测试体系README包含五层框架
- [ ] P0/P1/P2 优先级划分清晰
- [ ] 有可执行的测试指南

## 关键约束

1. **不要破坏现有测试** - 补充的测试用例不能使现有测试失败
2. **不要过度设计** - 测试用例要实用，不要为了"完整"写没有断言的测试
3. **边界要清晰** - 每个 Agent 的职责边界要严格遵守
4. **日志要中文** - 测试中的日志输出使用中文，便于后续维护
5. **每次修改后运行测试** - Team Lead 确保每次代码修改后运行相关测试验证

## 最终交付物检查清单

- [ ] `src/test/java/com/liang/drugagent/agent/chat/AgentSceneServiceRouteTest.java`（扩展，覆盖S1-S10）
- [ ] `src/test/java/com/liang/drugagent/agent/chat/MultiTurnConversationTest.java`（新建）
- [ ] `src/test/java/com/liang/drugagent/scene/tender_review/e2e/TenderReviewE2ETest.java`（新建）
- [ ] `doc/测试体系/README.md`（新建）
- [ ] `doc/测试体系/P0_核心回归测试集.md`（新建）
- [ ] `doc/测试体系/P1_场景链路测试集.md`（新建）
- [ ] `doc/测试体系/P2_质量回归测试集.md`（新建）
- [ ] `doc/测试体系/路由测试用例.md`（新建）
- [ ] `doc/测试体系/多轮对话测试用例.md`（新建）
- [ ] `doc/测试体系/测试执行指南.md`（新建）
