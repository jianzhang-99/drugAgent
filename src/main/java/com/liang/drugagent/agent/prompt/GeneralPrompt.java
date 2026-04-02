package com.liang.drugagent.agent.prompt;

/**
 * 通用对话场景 Prompt。
 *
 * <p><b>[已重构]</b> 本类保留用于向后兼容，请优先使用新分层结构：
 * <ul>
 *   <li>L0 基础边界：{@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt}</li>
 *   <li>L1 路由识别：{@link com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt}</li>
 * </ul>
 *
 * @author liangjiajian
 * @deprecated 请使用新的分层 Prompt 类
 */
@Deprecated
public final class GeneralPrompt {

    private GeneralPrompt() {
        // 工具类，禁止实例化
    }

    /**
     * 通用对话 System Prompt（用于无特定场景匹配时的对话）。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt#GENERAL_CHAT}
     */
    @Deprecated
    public static final String GENERAL_CHAT_SYSTEM_PROMPT = com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt.GENERAL_CHAT;

    /**
     * LLM 意图分类 Prompt。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt#INTENT_CLASSIFY}
     */
    @Deprecated
    public static final String INTENT_CLASSIFY_PROMPT = com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt.INTENT_CLASSIFY;
}
