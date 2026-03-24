package com.liang.drugagent.shared.llm;

import reactor.core.publisher.Flux;

/**
 * LLM 统一调用接口
 * 定义 LLM 调用的标准契约，支持多 Provider 实现
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
public interface LlmClient {

    /**
     * 获取该 Client 支持的 Provider 类型
     *
     * @return Provider 枚举
     */
    LlmProviderType getProvider();

    /**
     * 检查是否支持指定 Provider
     *
     * @param provider Provider 类型
     * @return 是否支持
     */
    default boolean supports(LlmProviderType provider) {
        return getProvider() == provider;
    }

    /**
     * 同步调用 LLM
     *
     * @param request 请求参数
     * @return 统一响应对象
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 流式调用 LLM
     *
     * @param request 请求参数
     * @return 流式响应 Flux（每次 emit 一个增量内容块）
     */
    Flux<LlmResponse> streamChat(LlmRequest request);

    /**
     * 检查 Client 是否可用
     *
     * @return true if available
     */
    default boolean isAvailable() {
        return true;
    }
}
