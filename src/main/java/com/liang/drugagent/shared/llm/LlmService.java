package com.liang.drugagent.shared.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM 统一服务入口。
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Slf4j
@Service
public class LlmService {

    private final Map<LlmProviderType, LlmClient> clientMap;

    @Value("${llm.default-provider:DASHSCOPE}")
    private LlmProviderType defaultProvider;

    @Value("${llm.routing-provider:DASHSCOPE}")
    private LlmProviderType routingProvider;

    @Value("${llm.chat-provider:DASHSCOPE}")
    private LlmProviderType chatProvider;

    @Value("${llm.report-provider:DASHSCOPE}")
    private LlmProviderType reportProvider;

    @Value("${llm.minimax-enabled:false}")
    private boolean minimaxEnabled;

    public LlmService(List<LlmClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        LlmClient::getProvider,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    /**
     * 通用对话（使用默认provider）
     */
    public LlmResponse chat(LlmRequest request) {
        LlmProviderType provider = resolveProvider(request);
        ensureModel(request, provider);
        log.info("LLM对话请求 - provider: {}, 模型: {}", provider, request.getModel());
        return getClient(provider).chat(request);
    }

    /**
     * 通用对话（快捷方法）
     */
    public String chat(String userMessage, String systemPrompt, String sessionId) {
        LlmRequest request = LlmRequest.builder()
                .provider(defaultProvider)
                .sessionId(sessionId)
                .systemPrompt(systemPrompt)
                .messages(java.util.List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(userMessage)
                        .build()))
                .build();
        LlmResponse response = chat(request);
        if (Boolean.TRUE.equals(response.getSuccess())) {
            return response.getContent();
        }
        throw new RuntimeException(response.getErrorMessage());
    }

    /**
     * 流式对话（使用默认provider）
     */
    public Flux<LlmResponse> streamChat(LlmRequest request) {
        LlmProviderType provider = resolveProvider(request);
        ensureModel(request, provider);
        log.info("LLM流式对话请求 - provider: {}, 模型: {}", provider, request.getModel());
        return getClient(provider).streamChat(request);
    }

    /**
     * 路由场景专用（可配置切换provider）
     */
    public LlmResponse chatForRouting(LlmRequest request) {
        LlmProviderType provider = normalizeProvider(routingProvider);
        request.setProvider(provider);
        ensureModel(request, provider);
        log.info("LLM路由请求 - provider: {}", provider);
        return getClient(provider).chat(request);
    }

    /**
     * 报告场景专用（可配置切换provider）
     */
    public LlmResponse chatForReport(LlmRequest request) {
        LlmProviderType provider = normalizeProvider(reportProvider);
        request.setProvider(provider);
        ensureModel(request, provider);
        log.info("LLM报告请求 - provider: {}", provider);
        return getClient(provider).chat(request);
    }

    /**
     * 通用问答专用（可配置切换provider）
     */
    public LlmResponse chatForChat(LlmRequest request) {
        LlmProviderType provider = normalizeProvider(chatProvider);
        request.setProvider(provider);
        ensureModel(request, provider);
        log.info("LLM对话请求 - provider: {}", provider);
        return getClient(provider).chat(request);
    }

    /**
     * 根据请求中的provider或配置决定使用哪个provider
     */
    private LlmProviderType resolveProvider(LlmRequest request) {
        if (request.getProvider() != null) {
            return normalizeProvider(request.getProvider());
        }
        return normalizeProvider(defaultProvider);
    }

    /**
     * 当请求中模型为空时，根据provider设置默认模型
     */
    private void ensureModel(LlmRequest request, LlmProviderType provider) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            String defaultModel = LlmProviderType.MINIMAX.equals(provider) ? "MiniMax-M2.7-highspeed" : "qwen-plus-2025-07-28";
            request.setModel(defaultModel);
        }
    }

    private LlmClient getClient(LlmProviderType provider) {
        LlmClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("未找到 Provider [" + provider + "] 对应的 LLM Client");
        }
        return client;
    }

    private LlmProviderType normalizeProvider(LlmProviderType provider) {
        if (provider == null) {
            return LlmProviderType.DASHSCOPE;
        }
        if (!minimaxEnabled && LlmProviderType.MINIMAX.equals(provider)) {
            log.warn("MiniMax 当前已被临时屏蔽，自动切换到 DashScope");
            return LlmProviderType.DASHSCOPE;
        }
        return provider;
    }
}
