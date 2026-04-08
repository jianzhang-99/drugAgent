package com.liang.drugagent.shared.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MiniMax Anthropic 兼容模型配置。
 *
 * <p>直接读取项目业务配置 {@code minimax.*} 创建 AnthropicChatModel，
 * 避免在 yml 中重复维护一套 {@code spring.ai.anthropic.*} 中转配置。</p>
 */
@Configuration
public class MiniMaxAnthropicConfig {

    @Bean
    public AnthropicApi anthropicApi(
            @Value("${minimax.base-url:https://api.minimaxi.com/anthropic}") String baseUrl,
            @Value("${minimax.api-key}") String apiKey,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ObjectProvider<ResponseErrorHandler> responseErrorHandlerProvider) {

        RestClient.Builder restClientBuilder = restClientBuilderProvider.getIfAvailable(RestClient::builder);
        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        ResponseErrorHandler responseErrorHandler = responseErrorHandlerProvider.getIfAvailable(DefaultResponseErrorHandler::new);

        return AnthropicApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(responseErrorHandler)
                .build();
    }

    @Bean("anthropicChatModel")
    public AnthropicChatModel anthropicChatModel(
            AnthropicApi anthropicApi,
            @Value("${minimax.model:MiniMax-M2.7-highspeed}") String model,
            @Value("${minimax.max-tokens:8192}") Integer maxTokens,
            @Value("${minimax.temperature:0.7}") Double temperature,
            @Value("${minimax.top-p:0.9}") Double topP,
            ObjectProvider<ToolCallingManager> toolCallingManagerProvider,
            ObjectProvider<RetryTemplate> retryTemplateProvider,
            ObjectProvider<ObservationRegistry> observationRegistryProvider,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicateProvider) {

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .build();

        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(options);

        ToolCallingManager toolCallingManager = toolCallingManagerProvider.getIfAvailable();
        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }

        RetryTemplate retryTemplate = retryTemplateProvider.getIfAvailable();
        if (retryTemplate != null) {
            builder.retryTemplate(retryTemplate);
        }

        ObservationRegistry observationRegistry = observationRegistryProvider.getIfAvailable();
        if (observationRegistry != null) {
            builder.observationRegistry(observationRegistry);
        }

        ToolExecutionEligibilityPredicate predicate = toolExecutionEligibilityPredicateProvider.getIfAvailable();
        if (predicate != null) {
            builder.toolExecutionEligibilityPredicate(predicate);
        }

        return builder.build();
    }
}
