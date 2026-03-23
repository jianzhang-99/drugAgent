# 百炼模型驱动 Workflow 路由计划

## 1. 目标

本计划用于将当前基于关键词和文件数量的场景路由，升级为“规则优先 + 百炼大模型判别 + 兜底回退”的 Workflow 决策机制。

当前项目已经具备以下基础：

- 已接入阿里云百炼 `DashScope`，默认模型为 `qwen-plus`
- 已有统一场景枚举 `SceneEnum`
- 已有工作流注册机制 `WorkflowRegistry`
- 已有三类核心 workflow：
  - 标书审查 `TENDER_REVIEW`
  - 合同预审 `CONTRACT_PRECHECK`
  - 风险预警 `RISK_ALERT`
  - 以及未知场景兜底 `UNKNOWN`

当前问题是：

- 路由逻辑主要依赖 `SceneRouter` 中的关键词匹配
- 规则虽然可解释，但泛化能力较弱
- 用户表达一旦不标准，就容易误路由或落到 `UNKNOWN`
- 无法给出“为什么走这个 workflow”的更丰富判别依据

本次改造的目标不是完全抛弃规则，而是引入百炼做“语义判别层”，提升路由准确率和可扩展性。

## 2. 成功标准

满足以下条件，可认为该能力完成第一阶段落地：

- 请求进入系统后，可由百炼模型判别目标 workflow
- 路由结果仍统一落到 `SceneEnum`
- 规则路由、LLM 路由、兜底路由三者职责清晰
- 路由结果可记录原因、置信度和原始分类依据
- 模型异常、超时、返回非法结构时，可稳定回退到规则或 `UNKNOWN`
- 能够通过样例集验证分类准确率

## 3. 当前现状

### 3.1 现有路由机制

当前 `SceneRouter` 逻辑如下：

- 优先读取前端传入的 `sceneHint`
- 有附件时根据文件数和文件名进行推断
- 无附件时根据 query 关键词命中三类场景
- 都不命中时返回 `UNKNOWN`

这种方式的优点是：

- 可解释
- 实现简单
- 成本低
- 延迟稳定

缺点也很明显：

- 依赖关键词覆盖范围
- 同义表达、复杂表达识别能力不足
- 多意图混合输入时难以判断
- 后续新增场景时规则会越来越碎

### 3.2 现有模型接入基础

项目已在 `application.yml` 中配置：

- `spring.ai.dashscope.api-key`
- `spring.ai.dashscope.chat.options.model=qwen-plus`

并且已有 `QwenService` 使用 `ChatClient` 调用模型，说明百炼接入基础已经具备。

### 3.3 当前最适合的改造思路

不建议一步改成“完全 LLM 决策”，而建议采用：

`显式 hint > 高置信规则 > 百炼判别 > fallback`

这样做的好处是：

- 对已有稳定规则零破坏
- 对明显场景保留低成本快速路由
- 对模糊表达使用大模型补强
- 出问题时容易定位和回滚

## 4. 总体方案

建议把路由能力拆成 4 层：

### 第一层：显式指定

如果前端或上游系统明确传入 `sceneHint`，优先使用。

适用原因：

- 用户在明确入口点击了“标书审查”“合同预审”等按钮
- 上游业务系统本身就知道当前任务类型
- 可以避免不必要的模型调用

### 第二层：高置信规则直达

对于高置信度特征，直接路由，不经过模型。

例如：

- 上传 2 份及以上标书类文件，直接进入 `TENDER_REVIEW`
- 单份合同文件且文件名特征明确，直接进入 `CONTRACT_PRECHECK`
- 后端明确创建的是某类 case，可直接使用 case 类型

这层保留的价值在于降低成本、减少时延、保留强可解释性。

### 第三层：百炼模型判别

对于规则无法高置信判断的请求，调用百炼进行意图分类。

模型任务不是直接回答用户问题，而是只做一件事：

- 判断该请求最适合交给哪个 workflow

输出建议为结构化 JSON，包括：

- `scene`
- `confidence`
- `reason`
- `requiresClarification`
- `clarificationQuestion`

### 第四层：兜底回退

以下情况统一进入兜底逻辑：

- 模型超时
- 模型异常
- 模型返回无法解析
- 模型置信度过低
- 用户请求本身就不够明确

兜底结果仍回到：

- 规则二次判定
- 或 `SceneEnum.UNKNOWN`

## 5. 推荐技术设计

## 5.1 新增模块划分

建议新增以下组件：

| 组件 | 作用 |
|---|---|
| `WorkflowRouteClassifier` | 封装百炼分类调用 |
| `WorkflowRouteDecision` | 统一承载路由结果、原因、置信度、来源 |
| `RoutePromptBuilder` | 负责构建分类 Prompt |
| `RouteDecisionParser` | 解析模型输出 JSON |
| `RouteDecisionValidator` | 校验 scene 是否合法、confidence 是否合理 |
| `SceneRouter` | 作为统一入口，编排规则和 LLM 路由 |

## 5.2 推荐的数据结构

建议新增一个统一决策对象：

```java
public class WorkflowRouteDecision {
    private SceneEnum scene;
    private String source; // sceneHint / rule / llm / fallback
    private String reason;
    private Double confidence;
    private boolean requiresClarification;
    private String clarificationQuestion;
    private Map<String, Object> raw;
}
```

这样做的目的不是为了“多封装一层”，而是为了把路由这件事从“只返回枚举”升级为“返回一个可观察、可调试、可评估的决策对象”。

## 5.3 SceneRouter 的改造方向

当前 `SceneRouter` 只返回 `SceneEnum`。建议升级为两步：

1. `decide(...)` 返回 `WorkflowRouteDecision`
2. `route(...)` 基于 `decide(...)` 继续返回 `SceneEnum`

这样可以兼容现有调用方，同时逐步让上下游使用更完整的决策信息。

建议流程如下：

```text
接收请求
  -> sceneHint 判断
  -> 高置信规则判断
  -> 百炼分类
  -> 分类结果校验
  -> 低置信回退
  -> 返回 WorkflowRouteDecision
  -> 提取 SceneEnum
```

## 6. Prompt 设计建议

百炼在这里的职责是“分类器”，不是“问答助手”。Prompt 必须足够收敛。

### 6.1 输入信息建议

模型输入建议包含：

- 用户 query
- `fileIds` 数量
- 文件名列表
- 前端传入的 metadata 摘要
- 当前支持的场景定义
- 输出格式要求

### 6.2 输出格式建议

建议强制模型输出 JSON，不允许自由散文。

示例：

```json
{
  "scene": "TENDER_REVIEW",
  "confidence": 0.93,
  "reason": "用户请求比对多份标书并识别雷同风险，明显属于标书审查场景",
  "requiresClarification": false,
  "clarificationQuestion": ""
}
```

### 6.3 Prompt 约束建议

应明确告诉模型：

- 只允许从给定 `scene` 列表中选择
- 不允许创造新的 workflow 名称
- 如果无法判断，输出 `UNKNOWN`
- 如果信息不足但可以提示补充，设置 `requiresClarification=true`

## 7. 实施阶段

## Phase 1：抽象路由决策层

### 目标

先把现有路由改造成“可插拔决策框架”，不急着直接上模型。

### 任务

| 任务 | 预估 | 依赖 | 完成标准 |
|---|---:|---|---|
| 新增 `WorkflowRouteDecision` 结构 | 2h | 无 | 可统一承载路由信息 |
| 重构 `SceneRouter`，新增 `decide()` 方法 | 4h | 决策结构 | 保留对外 `route()` 兼容 |
| 将当前规则路由逻辑迁移到 `RuleBasedRouteDecider` | 4h | 决策结构 | 规则逻辑独立可测试 |
| 在 `AgentContext` 中记录 `routeSource/confidence/reason` | 2h | Router 重构 | 下游可拿到完整路由信息 |

### 产出

- 路由从“函数判断”升级为“决策对象”
- 后续接 LLM 时不需要大改主调用链

## Phase 2：接入百炼分类器

### 目标

新增基于百炼的 workflow 分类能力，并接入 `SceneRouter`。

### 任务

| 任务 | 预估 | 依赖 | 完成标准 |
|---|---:|---|---|
| 新建 `WorkflowRouteClassifier` 服务 | 4h | Phase 1 | 可独立调用百炼完成分类 |
| 编写分类 Prompt 模板 | 2h | 分类服务 | Prompt 可稳定约束输出 |
| 实现 JSON 解析和结果校验 | 4h | 分类服务 | 模型输出异常可识别 |
| 配置模型超时、异常处理和日志 | 3h | 分类服务 | 失败可回退不炸主流程 |
| 将百炼判别接入 `SceneRouter` 编排 | 4h | 前置任务 | 模糊请求可以走 LLM 路由 |

### 产出

- 模型可参与 workflow 判别
- 结果可记录来源、理由、置信度

## Phase 3：灰度策略与回退机制

### 目标

让模型分类在生产侧可控，而不是“一刀切全量替换”。

### 任务

| 任务 | 预估 | 依赖 | 完成标准 |
|---|---:|---|---|
| 增加配置开关 `agent.routing.llm-enabled` | 1h | Phase 2 | 可随时关闭 LLM 路由 |
| 增加置信度阈值配置 | 1h | Phase 2 | 低置信结果自动回退 |
| 定义高置信规则直达清单 | 2h | Phase 1 | 明确哪些请求不走模型 |
| 增加分类耗时与命中来源日志 | 2h | Phase 2 | 便于观察效果 |
| 增加异常时回退到规则路由 | 2h | Phase 2 | 百炼不可用时主流程不受阻 |

### 产出

- 模型可灰度启用
- 出问题可快速降级

## Phase 4：评测与优化

### 目标

建立路由评测机制，避免“感觉准”但实际不可控。

### 任务

| 任务 | 预估 | 依赖 | 完成标准 |
|---|---:|---|---|
| 建立 30-50 条样例集 | 4h | 无 | 覆盖三大场景和模糊输入 |
| 标注标准答案 `SceneEnum` | 2h | 样例集 | 每条样例有期望 scene |
| 为分类器编写单测和集成测试 | 6h | Phase 2 | 正常、异常、低置信都有测试 |
| 统计规则命中率、LLM 命中率、兜底率 | 4h | 测试可运行 | 可量化评估效果 |
| 迭代 Prompt 和阈值 | 3h | 评测输出 | 分类稳定性提升 |

### 产出

- 有真实评测依据
- 可持续优化，而不是靠人工拍脑袋调 Prompt

## 8. 建议的路由策略

建议使用以下决策优先级：

1. `sceneHint`
2. 高置信规则
3. 百炼模型判别
4. 低置信回退规则
5. `UNKNOWN`

推荐阈值初版：

- `confidence >= 0.75`：直接采纳模型 scene
- `0.5 <= confidence < 0.75`：结合规则结果二次判断
- `confidence < 0.5`：直接回退

说明：

- 阈值不需要一开始就追求绝对正确
- 第一版重点是“稳定、可观察、可回退”

## 9. 测试样例建议

评测样例建议至少覆盖以下类型：

- 明确标书审查表达
- 明确合同预审表达
- 明确风险预警表达
- 多意图混合表达
- 模糊表达
- 带附件但 query 很短
- 无 query 只有文件
- 文件名误导场景
- 用户表达口语化、非关键词表达
- 完全无关请求

示例：

- “帮我看这两份投标文件有没有大段雷同”
- “审查这份供应协议里有没有不利条款”
- “分析近三个月耗材采购异常波动”
- “我上传了一个文件，帮我看看有没有风险”
- “这个能不能帮我先过一遍”

## 10. 风险与应对

| 风险 | 影响 | 概率 | 应对策略 |
|---|---|---|---|
| 模型输出不稳定 | 高 | 中 | 强制 JSON 输出 + 结果校验 |
| 模型分类成本增加 | 中 | 中 | 仅对模糊请求启用 LLM |
| 路由延迟上升 | 中 | 中 | 高置信规则直达，设置超时 |
| 低置信误分类 | 高 | 中 | 引入阈值和规则回退 |
| 后续新增场景导致 Prompt 变复杂 | 中 | 高 | 把场景定义抽成独立配置 |
| 模型异常影响主流程 | 高 | 中 | 开关、超时、回退三重兜底 |

## 11. 第一周执行建议

如果按一周做一个可运行版本，建议这样安排：

### Day 1

- 重构 `SceneRouter`
- 新增 `WorkflowRouteDecision`
- 把现有规则路由抽离成独立组件

### Day 2

- 编写百炼分类 Prompt
- 新增 `WorkflowRouteClassifier`
- 实现模型调用和基本解析

### Day 3

- 接入置信度校验
- 增加异常回退逻辑
- 在 `AgentContext` 中记录路由元数据

### Day 4

- 建立样例集
- 编写单元测试和集成测试
- 调整 Prompt 和阈值

### Day 5

- 加入配置开关
- 打通日志和观测指标
- 在开发环境灰度验证

## 12. 建议优先改造的代码位置

- `src/main/java/com/liang/drugagent/agent/SceneRouter.java`
- `src/main/java/com/liang/drugagent/service/QwenService.java`
- `src/main/java/com/liang/drugagent/service/DrugAgentService.java`
- `src/main/java/com/liang/drugagent/enums/SceneEnum.java`
- `src/main/resources/application.yml`

建议新增的代码区域：

- `src/main/java/com/liang/drugagent/agent/routing/`
- `src/main/java/com/liang/drugagent/domain/routing/`
- `src/test/java/com/liang/drugagent/agent/`

## 13. 最终建议

这件事最好的落地方式不是“把关键词判断删掉改成全模型”，而是：

- 用规则保住稳定和低成本
- 用百炼补强模糊意图识别
- 用结构化决策对象承接路由结果
- 用测试样例和阈值控制风险

这样做出来的 workflow 路由会更像一个真正可演进的 Agent 调度层，而不是一次性写死的判断逻辑。
