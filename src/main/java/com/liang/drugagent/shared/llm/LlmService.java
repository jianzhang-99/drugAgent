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
        request.setProvider(routingProvider);
        ensureModel(request, routingProvider);
        log.info("LLM路由请求 - provider: {}", routingProvider);
        return getClient(routingProvider).chat(request);
    }

    /**
     * 报告场景专用（可配置切换provider）
     */
    public LlmResponse chatForReport(LlmRequest request) {
        request.setProvider(reportProvider);
        ensureModel(request, reportProvider);
        log.info("LLM报告请求 - provider: {}", reportProvider);
        return getClient(reportProvider).chat(request);
    }

    /**
     * 通用问答专用（可配置切换provider）
     */
    public LlmResponse chatForChat(LlmRequest request) {
        request.setProvider(chatProvider);
        ensureModel(request, chatProvider);
        log.info("LLM对话请求 - provider: {}", chatProvider);
        return getClient(chatProvider).chat(request);
    }

    /**
     * 根据请求中的provider或配置决定使用哪个provider
     */
    private LlmProviderType resolveProvider(LlmRequest request) {
        if (request.getProvider() != null) {
            return request.getProvider();
        }
        return defaultProvider;
    }

    /**
     * 当请求中模型为空时，根据provider设置默认模型
     */
    private void ensureModel(LlmRequest request, LlmProviderType provider) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            String defaultModel = LlmProviderType.MINIMAX.equals(provider) ? "MiniMax-M2.7-highspeed" : "qwen3.5-plus";
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
}
