package com.liang.drugagent.agent.prompt;

/**
 * RAG 检索场景 Prompt。
 *
 * <p><b>[已重构]</b> 请使用新分层结构：
 * {@link com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt}
 *
 * @author liangjiajian
 * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt}
 */
@Deprecated
public final class RagPrompt {

    private RagPrompt() {
        // 工具类，禁止实例化
    }

    /**
     * RAG 系统提示词模板。
     *
     * @deprecated 请使用 {@link com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt#SYSTEM_PROMPT}
     */
    @Deprecated
    public static final String SYSTEM_PROMPT = com.liang.drugagent.agent.prompt.shared.rag.SharedRagPrompt.SYSTEM_PROMPT;
}
