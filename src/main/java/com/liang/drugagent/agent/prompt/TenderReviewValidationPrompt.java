package com.liang.drugagent.agent.prompt;

/**
 * 标书审查输出校验 Prompt 模板。
 *
 * <p><b>[已重构]</b> 请使用新分层结构：
 * {@link com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt}
 *
 * @author liangjiajian
 * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt}
 */
@Deprecated
public class TenderReviewValidationPrompt {

    /**
     * 标书审查输出校验 System Prompt。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt#SYSTEM_PROMPT}
     */
    @Deprecated
    public static final String SYSTEM_PROMPT = com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt.SYSTEM_PROMPT;

    /**
     * 构建用户消息（包含 workflow 结果）。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt#buildUserMessage(String, String, String)}
     */
    @Deprecated
    public static String buildUserMessage(String scene, String routeReason, String workflowResult) {
        return com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt.buildUserMessage(scene, routeReason, workflowResult);
    }

    /**
     * 获取校验输出的 JSON Schema（用于结构化输出）。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt#getJsonSchema()}
     */
    @Deprecated
    public static String getJsonSchema() {
        return com.liang.drugagent.agent.prompt.tender_review.validate.TenderReviewValidatePrompt.getJsonSchema();
    }
}
