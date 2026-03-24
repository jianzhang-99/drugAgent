package com.liang.drugagent.shared.llm;

import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.model.LlmProviderType;
import com.liang.drugagent.shared.llm.model.LlmRequest;
import com.liang.drugagent.shared.llm.model.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * LLM 统一门面服务
 * 给业务层使用的统一入口，不直接关心底层 provider 实现
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Service
public class LlmFacadeService {

    private static final Logger log = LoggerFactory.getLogger(LlmFacadeService.class);

    private final LlmClientFactory clientFactory;

    @Value("${llm.default-provider:DASHSCOPE}")
    private LlmProviderType defaultProvider;

    @Value("${llm.routing-provider:DASHSCOPE}")
    private LlmProviderType routingProvider;

    @Value("${llm.chat-provider:DASHSCOPE}")
    private LlmProviderType chatProvider;

    @Value("${llm.report-provider:DASHSCOPE}")
    private LlmProviderType reportProvider;

    public LlmFacadeService(LlmClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * 通用对话（使用默认provider）
     */
    public LlmResponse chat(LlmRequest request) {
        LlmProviderType provider = resolveProvider(request);
        log.info("LLM chat request - provider: {}, model: {}", provider, request.getModel());
        return clientFactory.getClient(provider).chat(request);
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
        log.info("LLM stream chat request - provider: {}, model: {}", provider, request.getModel());
        return clientFactory.getClient(provider).streamChat(request);
    }

    /**
     * 路由场景专用（可配置切换provider）
     */
    public LlmResponse chatForRouting(LlmRequest request) {
        request.setProvider(routingProvider);
        log.info("LLM routing request - provider: {}", routingProvider);
        return clientFactory.getClient(routingProvider).chat(request);
    }

    /**
     * 报告场景专用（可配置切换provider）
     */
    public LlmResponse chatForReport(LlmRequest request) {
        request.setProvider(reportProvider);
        log.info("LLM report request - provider: {}", reportProvider);
        return clientFactory.getClient(reportProvider).chat(request);
    }

    /**
     * 通用问答专用（可配置切换provider）
     */
    public LlmResponse chatForChat(LlmRequest request) {
        request.setProvider(chatProvider);
        log.info("LLM chat request - provider: {}", chatProvider);
        return clientFactory.getClient(chatProvider).chat(request);
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
}
