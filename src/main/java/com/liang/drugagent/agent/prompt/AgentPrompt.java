package com.liang.drugagent.agent.prompt;

/**
 * Agent 提示词常量。
 *
 * <p><b>[已重构]</b> 本类保留用于向后兼容，请优先使用新分层结构：
 * <ul>
 *   <li>L0 基础边界：{@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt}</li>
 *   <li>L1 路由识别：{@link com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt}</li>
 * </ul>
 *
 * @author liangjiajian
 * @deprecated 请使用新的分层 Prompt 类，按场景-层级-用途格式命名
 */
@Deprecated
public final class AgentPrompt {

    private AgentPrompt() {
        // 工具类，禁止实例化
    }

    /**
     * 通用对话系统 Prompt（当未识别到特定场景时使用）。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt#GENERAL_CHAT}
     */
    @Deprecated
    public static final String GENERAL_CHAT = com.liang.drugagent.agent.prompt.shared.base.SharedBasePrompt.GENERAL_CHAT;

    /**
     * 场景分类系统 Prompt。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt#SCENE_CLASSIFY}
     */
    @Deprecated
    public static final String SCENE_CLASSIFICATION = com.liang.drugagent.agent.prompt.shared.route.SharedRoutePrompt.SCENE_CLASSIFY;
}
