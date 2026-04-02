# 标书场景 LLM 增强实现方案

> 文档版本：V1.0
> 更新时间：2026-04-02
> 适用范围：`scene/tender_review` 标书审查场景
> 文档目标：在不破坏现有分层边界的前提下，为标书审查场景引入 LLM 语义理解能力，提升对软性围标特征的识别效果

---

## 1. 背景与目标

当前标书审查链路已经具备以下能力：

- 文档接入与解析
- 结构化字段抽取
- RuleExecutor 规则命中
- 风险融合
- 报告生成

现阶段系统对硬特征的检测效果较好，例如：

- 报价梯度异常
- 联系方式近邻
- 版式模板同源
- 罕见错别字共现
- 罕见数字共现

但对以下依赖语义理解的特征表现较弱：

- 章节级改写抄袭
- 风险识别内容同源
- 商务条款互补配合
- 同一人员跨文档身份漂移
- 隐式元数据与上下文关系判断

这说明当前能力重心仍然是“结构化规则命中”，而不是“基于业务语义的围标判断”。

本方案的目标不是用 LLM 替代全部规则，而是建设一条“规则筛选 + LLM 裁决 + 结构化回写”的混合判定链路，让系统既有语义理解能力，又保留证据链和可解释性。

---

## 2. 核心设计原则

### 2.1 LLM 不直接替代整个 Workflow

不建议将两份标书全文直接交给 LLM 输出最终结论。

原因：

- 结论不可控
- 成本高
- 耗时不稳定
- 难以复核
- 难以形成证据链

LLM 应只负责它最擅长的部分：语义判断、关系判断、改写识别、互补行为识别。

### 2.2 规则负责召回候选，LLM 负责裁决

推荐模式：

1. 规则层先做候选召回
2. 将疑似段落对、字段对、章节对缩小到可控范围
3. 仅将候选对提交给 LLM
4. LLM 输出结构化判断结果
5. Workflow 将 LLM 结果转为 `RuleHit`
6. 后续继续走免责、融合、证据、报告主链路

### 2.3 LLM 输出必须结构化

不允许 LLM 只返回自然语言结论。

LLM 输出至少应包含：

- 是否命中
- 风险类型
- 置信度
- 判定理由
- 命中的关键证据片段
- 不命中原因或保留意见

### 2.4 LLM 增强应放在场景层，不放到通用层

本方案遵循当前项目架构边界：

- `AgentChatService` 仍只负责会话编排
- `AgentSceneService` 仍只负责场景识别与分发
- LLM 增强逻辑放在 `scene/tender_review` 场景内
- 优先通过场景专属分析器或 Orchestrator 承接

---

## 3. 推荐总体链路

```text
AgentController
-> AgentChatService
-> AgentSceneService
-> TenderReviewSceneService
-> TenderReviewToolOrchestrator（目标收敛入口）
-> ReviewTenderTool
-> TenderReviewWorkflow
   -> Preparation / Extraction
   -> Deterministic Rule Executors
   -> LLM Semantic Analyzers
   -> Exemption
   -> Risk Fusion
   -> Evidence Assemble
   -> Report Generation
-> AgentExecutionResult
-> DrugAgentResp
```

说明：

- 硬规则继续保留在 `TenderRuleExecutor`
- 语义能力通过新增 `LLM Semantic Analyzer` 接入
- 对外返回结构不变，降低接入风险

---

## 4. 哪些规则优先做 LLM 增强

### 4.1 第一优先级

#### W-P1 技术方案抄袭

当前问题：

- 主要依赖整段文本相似度
- 对同义改写、术语替换、句式重写不敏感

LLM 适合判断：

- 是否属于同一设计思路的改写
- 是否存在核心模块描述高度同源
- 是否明显缺乏独立编写痕迹

#### W-P4 风险识别抄袭

当前问题：

- 风险点、影响、措施容易被轻度改写
- 编辑距离很难识别结构化同源关系

LLM 适合判断：

- 风险项拆解逻辑是否一致
- 风险影响链条是否一致
- 应对措施是否只是换一种写法

#### W-M8 商务条款配合

当前问题：

- 当前没有稳定规则入口
- 本质是关系型语义判断，不是单字段相等判断

LLM 适合判断：

- 一方完全接受、一方附条件接受是否构成互补配合
- 表面差异背后是否存在策略协同
- 是否存在“一个强响应、一个柔性偏离”的配合模式

### 4.2 第二优先级

#### W-P2 实施方法抄袭

LLM 适合判断：

- 阶段名称不同但流程骨架一致
- 关键里程碑、交付顺序、组织方式是否同源

#### W-P3 服务承诺抄袭

LLM 适合判断：

- 服务等级、时效组合、承诺逻辑是否高度同源
- 表达不同但服务体系配置基本一致

#### W-M3 核心团队重叠

LLM 适合辅助：

- 同一人不同岗位包装
- 简历表达改写但履历骨架一致
- 团队构成关系相似

### 4.3 不建议优先 LLM 化的规则

以下规则更适合继续走确定性执行：

- W-M1 报价梯度异常
- W-M2 联系方式近邻
- W-M4 版式模板同源
- W-M5 罕见错误共现
- W-M6 商务条款雷同
- W-M7 元数据聚集性
- W-P5 错误复现抄袭
- W-P6 案例数据抄袭

原因：

- 规则本身已经较稳定
- 结果具备较强确定性
- LLM 加入后的收益不一定高于复杂度和成本

其中 `W-M7` 的重点应先放在元数据提取，而不是先做 LLM 裁决。

---

## 5. 目标能力分层

建议将标书审查能力拆成三层。

### 5.1 确定性信号层

职责：

- 提取硬性异常信号
- 生成基础证据
- 召回 LLM 候选片段

典型实现：

- 现有 `TenderRuleExecutor`
- 章节标题匹配
- 轻量文本相似度
- 电话、金额、错词、案例数字等结构化特征

### 5.2 LLM 语义裁决层

职责：

- 对候选片段进行语义判断
- 判断是否属于同源改写、互补配合、协同行为
- 给出结构化理由和置信度

### 5.3 风险融合与报告层

职责：

- 将 LLM 结果与规则结果统一转为 `RuleHit`
- 与免责、融合、证据组装、报告生成对接

这样做的好处是：

- 不破坏现有 Workflow 主链路
- AI 能力可以逐步引入
- 融合层仍然统一

---

## 6. 推荐新增组件

### 6.1 新增语义分析器接口

建议在 `scene/tender_review` 下新增语义分析器接口，例如：

```java
public interface TenderSemanticAnalyzer {

    String analyzerCode();

    List<RuleHit> analyze(TenderReviewData data);
}
```

说明：

- 接口输出仍然是 `RuleHit`
- 便于和当前 `TenderRuleEngine` 的产出结构保持一致
- 后续可以独立做开关、熔断、降级

### 6.2 新增语义裁决服务

建议新增：

- `TenderSemanticReviewService`

职责：

- 组织 prompt
- 调用 LLM
- 解析结构化输出
- 处理失败重试、超时、降级

### 6.3 新增候选召回器

建议新增：

- `TenderSemanticCandidateService`

职责：

- 基于章节、字段类型、轻量相似度召回候选对
- 限制每类规则送入 LLM 的片段数量
- 降低成本和时延

### 6.4 新增结果解析 DTO

建议新增：

- `TenderSemanticJudgeReq`
- `TenderSemanticJudgeResp`
- `TenderSemanticEvidence`

---

## 7. 推荐目录结构

```text
scene/tender_review
├── facade/
├── orchestrator/
├── preparation/
├── tool/
├── workflow/
├── service/
│   ├── TenderSemanticReviewService.java
│   ├── TenderSemanticCandidateService.java
│   └── ...
├── semantic/
│   ├── TenderSemanticAnalyzer.java
│   ├── ProposalSemanticAnalyzer.java
│   ├── RiskIdentificationSemanticAnalyzer.java
│   ├── CommercialCoordinationSemanticAnalyzer.java
│   └── ...
├── model/
│   ├── TenderSemanticJudgeReq.java
│   ├── TenderSemanticJudgeResp.java
│   └── ...
└── support/
```

说明：

- `semantic/` 只放 LLM 语义判断相关实现
- `service/` 放通用语义调用服务
- 不建议把这部分继续塞回通用 `agent/chat`

---

## 8. 推荐执行模式

### 8.1 模式一：规则先行，LLM 补强

适用：

- 当前大多数升级点

流程：

1. 先执行现有确定性规则
2. 对低命中但高风险章节做候选召回
3. 调用 LLM 补做语义判断
4. 补充新的 `RuleHit`

优点：

- 改造成本低
- 对现有系统侵入小
- 容易逐步上线

### 8.2 模式二：专属规则直接走 LLM 裁决

适用：

- W-M8 商务条款配合
- 部分 W-M3 人员关系判断

流程：

1. 召回相关章节
2. 直接进入 LLM 判断
3. 输出结构化 `RuleHit`

优点：

- 更贴合关系型判断场景

### 8.3 不推荐模式：全文直接判断

不建议：

- 一次性把两份全文直接交给模型输出最终围标结论

原因：

- token 成本高
- 结果不稳定
- 难以复用证据链

---

## 9. LLM 输入输出设计

### 9.1 输入对象建议

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticJudgeReq {

    private String ruleCode;
    private String scene;
    private String caseId;
    private String leftDocumentId;
    private String rightDocumentId;
    private String compareTopic;
    private List<String> leftSnippets;
    private List<String> rightSnippets;
    private Map<String, Object> extraContext;
}
```

### 9.2 输出对象建议

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticJudgeResp {

    private Boolean hit;
    private String ruleCode;
    private String riskType;
    private Double confidence;
    private Integer suggestedWeight;
    private String conclusion;
    private String reason;
    private List<TenderSemanticEvidence> evidences;
    private List<String> cautionNotes;
}
```

### 9.3 证据对象建议

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSemanticEvidence {

    private String documentId;
    private String chapterPath;
    private String excerpt;
    private String explanation;
}
```

---

## 10. Prompt 设计原则

### 10.1 Prompt 目标

Prompt 的目标不是让模型自由发挥，而是让模型完成有限任务：

- 比较两个候选片段
- 判断是否疑似同源
- 判断是否缺乏独立编写痕迹
- 输出结构化 JSON

### 10.2 Prompt 必须说明的内容

- 当前规则编号
- 业务判断目标
- 命中标准
- 不命中的典型情况
- 输出 JSON Schema
- 禁止输出无结构化文本

### 10.3 示例 prompt 要点

以 W-P1 为例，Prompt 应要求模型判断：

- 是否属于技术方案的实质同源改写
- 是否共享相同的系统架构骨架
- 是否共享相同的模块划分和业务闭环逻辑
- 仅出现行业通用术语时不得判定命中
- 必须给出来自双方文档的证据片段

### 10.4 反误报约束

Prompt 中应显式加入以下约束：

- 行业通用表述不应直接判定抄袭
- 法规引用、招标文件要求复述不应直接判定抄袭
- 模板化章节标题相同不应单独构成语义抄袭结论
- 置信度不足时允许返回 `hit=false`

---

## 11. Workflow 集成方案

### 11.1 短期落地方案

在 `TenderReviewWorkflow` 中保留当前主链路，新增语义命中补强步骤：

```text
结构化加载
-> 确定性规则命中
-> LLM 语义命中补强
-> 免责判定
-> 风险融合
-> 证据组装
-> 报告生成
```

对应改造建议：

- `TenderRuleEngine` 继续负责确定性规则
- 新增 `TenderSemanticEngine`
- 最终将两个结果合并为 `allHits`

### 11.2 中期目标方案

当 Tool 化链路收敛后，建议将语义能力放入：

- `TenderReviewToolOrchestrator`

职责：

- 控制是否启用 LLM 语义增强
- 按规则编码选择分析器
- 控制预算、超时、降级策略

这样更符合“场景编排层承接 Tool/LLM 调用，Workflow 保持业务主链路”的目标方向。

---

## 12. 候选召回策略

为控制成本，LLM 不应处理全部段落组合。

建议召回策略如下。

### 12.1 章节约束

优先只比较同主题章节：

- 技术方案 对 技术方案
- 风险识别 对 风险识别
- 商务条款响应 对 商务条款响应
- 服务承诺 对 服务承诺

### 12.2 轻量相似度预筛

可使用：

- Jaccard
- TF-IDF
- BM25
- 编辑距离
- 关键词重叠度

仅保留 topN 候选段落对送入 LLM。

### 12.3 数量控制

建议默认上限：

- 每个规则每对文档最多 5 组候选
- 每组候选最多 2 到 4 段核心片段

---

## 13. 风险融合策略调整建议

LLM 增强后，建议对 `RiskFusionService` 做两点调整。

### 13.1 引入 LLM 命中来源标记

建议在 `RuleHit` 中补充来源字段，例如：

- `DETERMINISTIC_RULE`
- `LLM_SEMANTIC_RULE`
- `HYBRID_RULE`

### 13.2 引入置信度调权

LLM 结果建议按置信度映射权重：

- `confidence >= 0.85`：高可信
- `0.70 <= confidence < 0.85`：中可信
- `< 0.70`：低可信，仅作为辅助证据

不建议让低置信度 LLM 结论直接把整体风险打到最高。

---

## 14. 可观测性与治理

引入 LLM 后，必须同步建设治理能力。

建议记录：

- 规则编码
- 是否命中
- 输入 token
- 输出 token
- 耗时
- 模型名称
- prompt 版本
- 候选片段数量
- 最终置信度

建议新增埋点维度：

- `semanticAnalyzer`
- `promptVersion`
- `llmHit`
- `llmConfidence`
- `llmLatencyMs`

---

## 15. 上线策略

### 15.1 Phase 1

先做：

- W-P1
- W-P4
- W-M8

原因：

- 业务价值高
- 当前漏检明显
- LLM 对这些问题有明显优势

### 15.2 Phase 2

再做：

- W-P2
- W-P3
- W-M3

### 15.3 Phase 3

再评估：

- 是否将语义增强统一纳入 `TenderReviewToolOrchestrator`
- 是否为不同规则使用不同 prompt 模板
- 是否接入向量召回或章节 embedding

---

## 16. 测试与验收建议

### 16.1 单元测试

至少覆盖：

- 候选召回正确性
- LLM 输出 JSON 解析
- LLM 失败降级
- 置信度映射

### 16.2 场景测试

至少覆盖：

- W-P1 到 W-P4
- W-M8
- W-M3

### 16.3 对照测试

建议同时保留：

- 纯规则结果
- LLM 增强结果

用于比较：

- 命中率
- 误报率
- 平均耗时
- 单次调用成本

---

## 17. 最终结论

标书场景不应停留在“规则系统包了一层 Agent 外壳”的阶段。

该场景真正需要的，是一套：

- 硬信号由规则稳定识别
- 软信号由 LLM 进行语义判断
- 全链路保留结构化证据和解释

的混合审查体系。

因此，推荐的实现方向是：

1. 保留现有 `TenderReviewWorkflow` 的确定性骨架
2. 为 W-P1、W-P4、W-M8 等弱规则引入 LLM 语义增强
3. 采用“候选召回 + LLM 裁决 + RuleHit 回写”的模式
4. 逐步将该能力收敛到标书场景专属编排层

这样既不丢掉项目的工程可控性，也不会失去“做业务 Agent”最核心的语义理解价值。
