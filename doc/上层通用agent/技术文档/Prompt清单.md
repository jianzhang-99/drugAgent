# Prompt 清单

> 文档版本：V1.0
> 更新时间：2026-04-02
> 状态：初稿

---

## 1. 概述

本文档记录项目中所有 Prompt 资产的清单，包括：
- Prompt 名称
- 所属场景
- 所属层级（L0-L5）
- 当前载体（Java 类）
- 调用位置
- 输出类型

---

## 2. Prompt 清单表

| Prompt 名称 | 所属场景 | 所属层级 | 当前载体 | 调用位置 | 输出类型 | 备注 |
|------------|---------|---------|---------|---------|---------|------|
| DRUG_REGULATION_EXPERT_PROMPT | shared | L0 | SystemPrompt.java | LLMChatService.java | JSON | 通用药品监管 AI 助手人设 |
| TENDER_REVIEW_PROMPT | tender_review | L3 | SystemPrompt.java | LLMChatService.java | JSON | 标书雷同与语义查重场景主 Prompt |
| CONTRACT_PRECHECK_PROMPT | contract | L3 | SystemPrompt.java | LLMChatService.java | JSON | 合同文件 AI 预审核场景主 Prompt |
| RISK_ALERT_PROMPT | risk | L3 | SystemPrompt.java | LLMChatService.java | JSON | 医疗耗材与药品合规风险预警场景主 Prompt |
| GENERAL_CHAT_SYSTEM_PROMPT | shared | L0 | GeneralPrompt.java | AgentSceneService.java | Markdown | 通用对话 System Prompt（无特定场景匹配时） |
| INTENT_CLASSIFY_PROMPT | shared | L1 | GeneralPrompt.java | AgentSceneService.java | 文本 | LLM 意图分类 Prompt，输出 TENDER_REVIEW/CONTRACT_PRECHECK/RISK_ALERT/DEFAULT |
| SCENE_CLASSIFICATION | shared | L1 | AgentPrompt.java | 未被调用 | JSON | 场景分类 Prompt（当前未使用） |
| TenderReviewValidationPrompt.SYSTEM_PROMPT | tender_review | L4 | TenderReviewValidationPrompt.java | 未被调用 | JSON | 标书审查输出校验 Prompt |
| RagPrompt.SYSTEM_PROMPT | shared | L3 | RagPrompt.java | RagService.java | Markdown | RAG 检索场景 Prompt |

---

## 3. Prompt 分层说明

### L0：基础边界 Prompt
定义通用角色、回答语言、合规边界、禁止事项。

| Prompt 名称 | 说明 |
|------------|------|
| DRUG_REGULATION_EXPERT_PROMPT | 通用药品监管 AI 助手人设 |
| GENERAL_CHAT_SYSTEM_PROMPT | 通用对话 System Prompt |

### L1：路由与意图识别 Prompt
只负责场景识别，不负责回答用户问题。

| Prompt 名称 | 说明 |
|------------|------|
| INTENT_CLASSIFY_PROMPT | LLM 意图分类，输出枚举值 |
| SCENE_CLASSIFICATION | 场景分类（当前未使用） |

### L3：场景执行 Prompt
为 Orchestrator 或场景内 LLM 子任务服务。

| Prompt 名称 | 说明 |
|------------|------|
| TENDER_REVIEW_PROMPT | 标书雷同与语义查重场景主 Prompt |
| CONTRACT_PRECHECK_PROMPT | 合同文件 AI 预审核场景主 Prompt |
| RISK_ALERT_PROMPT | 医疗耗材与药品合规风险预警场景主 Prompt |
| RagPrompt.SYSTEM_PROMPT | RAG 检索场景 Prompt |

### L4：结果校验 Prompt
校验 Workflow 或场景执行结果是否完整、合规、可展示。

| Prompt 名称 | 说明 |
|------------|------|
| TenderReviewValidationPrompt.SYSTEM_PROMPT | 标书审查输出校验 Prompt |

---

## 4. 未启用 Prompt

以下 Prompt 已定义但当前未被代码调用：

| Prompt 名称 | 所属场景 | 所属层级 | 说明 |
|------------|---------|---------|------|
| SCENE_CLASSIFICATION | shared | L1 | 场景分类 Prompt，定义了但未集成 |
| TenderReviewValidationPrompt.SYSTEM_PROMPT | tender_review | L4 | 标书审查输出校验 Prompt，定义了但未集成 |

---

## 5. Prompt 统计

| 维度 | 数量 |
|------|------|
| 总 Prompt 数 | 9 |
| 已启用 | 7 |
| 未启用 | 2 |
| L0 层 | 2 |
| L1 层 | 2 |
| L3 层 | 4 |
| L4 层 | 1 |

---

## 6. 后续优化方向

1. **启用未调用 Prompt**：将 SCENE_CLASSIFICATION 和 TenderReviewValidationPrompt 集成到代码中
2. **拆分大 Prompt**：TENDER_REVIEW_PROMPT、CONTRACT_PRECHECK_PROMPT、RISK_ALERT_PROMPT 体积较大，建议按职责拆分为多个小 Prompt
3. **统一结构化输出**：中间层 Prompt 应尽量统一输出 JSON，固定字段和枚举
4. **建立评测集**：为每个关键 Prompt 建立评测样本
