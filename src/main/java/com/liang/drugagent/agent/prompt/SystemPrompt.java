package com.liang.drugagent.agent.prompt;

/**
 * AI 系统级 Prompt 统一定义与管理类。
 *
 * <p><b>[已重构]</b> 本类保留用于向后兼容，请优先使用新分层结构：
 * <ul>
 *   <li>L0 基础边界：{@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt}</li>
 *   <li>L3 场景执行：标书-{@link com.liang.drugagent.agent.prompt.tender_review.judge.TenderReviewJudgePrompt}、
 *     合同-{@link com.liang.drugagent.agent.prompt.contract_precheck.judge.ContractPrecheckJudgePrompt}、
 *     预警-{@link com.liang.drugagent.agent.prompt.risk_alert.judge.RiskAlertJudgePrompt}</li>
 * </ul>
 *
 * @author liangjiajian
 * @deprecated 请使用新的分层 Prompt 类，按场景-层级-用途格式命名
 */
@Deprecated
public class SystemPrompt {

    /**
     * 默认回复模式：Markdown 文本
     */
    public static final String MODE_MARKDOWN = "markdown";

    /**
     * 结构化 JSON 回复模式
     */
    public static final String MODE_JSON = "json";

    // ==================== 公共常量 ====================

    private static final String JSON_PREFIX = "```json";
    private static final String JSON_SUFFIX = "```";

    /**
     * 统一JSON基础Schema
     */
    private static final String BASE_JSON_SCHEMA = """
            {
              "scene": "STRING, 场景标识",
              "timestamp": "STRING, ISO8601时间戳",
              "thinking": {
                "step1": "STRING, 第一步推理",
                "step2": "STRING, 第二步推理",
                "step3": "STRING, 第三步推理"
              },
              "riskLevel": "STRING, LOW|MEDIUM|HIGH|CRITICAL",
              "score": "INTEGER, 0-100评分",
              "summary": "STRING, 一句话结论",
              "findings": "ARRAY, 发现列表",
              "suggestions": "ARRAY, 建议列表"
            }
            """;

    // ==================== 通用基础人设 ====================

    /**
     * 通用药品监管AI助手人设
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt#DRUG_REGULATION_EXPERT_PROMPT}
     */
    @Deprecated
    public static final String DRUG_REGULATION_EXPERT_PROMPT = com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt.DRUG_REGULATION_EXPERT_PROMPT;

    // ==================== 场景一：标书雷同与语义查重 ====================

    /**
     * 场景一：【标书雷同与语义查重】
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.tender_review.judge.TenderReviewJudgePrompt#SYSTEM_PROMPT}
     */
    @Deprecated
    public static final String TENDER_REVIEW_PROMPT = com.liang.drugagent.agent.prompt.tender_review.judge.TenderReviewJudgePrompt.getFormattedPrompt();

    // ==================== 场景二：合同文件AI预审核 ====================

    /**
     * 场景二：【合同文件AI预审核】
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.contract_precheck.judge.ContractPrecheckJudgePrompt#SYSTEM_PROMPT}
     */
    @Deprecated
    public static final String CONTRACT_PRECHECK_PROMPT = com.liang.drugagent.agent.prompt.contract_precheck.judge.ContractPrecheckJudgePrompt.getFormattedPrompt();

    // ==================== 场景三：医疗耗材与药品合规风险预警 ====================

    /**
     * 场景三：【医疗耗材与药品合规风险预警】
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.risk_alert.judge.RiskAlertJudgePrompt#SYSTEM_PROMPT}
     */
    @Deprecated
    public static final String RISK_ALERT_PROMPT = com.liang.drugagent.agent.prompt.risk_alert.judge.RiskAlertJudgePrompt.getFormattedPrompt();

    // ==================== 内部工具方法 ====================

    static String getJsonPrefix() {
        return JSON_PREFIX;
    }

    static String getJsonSuffix() {
        return JSON_SUFFIX;
    }

    static String getBaseJsonSchema() {
        return BASE_JSON_SCHEMA;
    }
}
