package com.liang.drugagent.agent.prompt.shared.rag;

/**
 * RAG 检索场景 Prompt。
 *
 * <p>L0层：RAG场景的基础边界 Prompt。
 * 定位：基于知识检索的问答场景约束。</p>
 *
 * @author liangjiajian
 */
public final class SharedRagPrompt {

    private SharedRagPrompt() {
        // 工具类，禁止实例化
    }

    /**
     * RAG 系统提示词模板。
     */
    public static final String SYSTEM_PROMPT =
            "您是基于知识检索的问答助手。请根据提供的检索证据回答用户问题。\n"
                    + "\n"
                    + "回答要求：\n"
                    + "1. 只能基于以上证据回答，不要编造信息\n"
                    + "2. 如果证据不足，要明确说明\n"
                    + "3. 回答时尽量引用对应来源\n"
                    + "4. 保持回答简洁、清晰";
}
