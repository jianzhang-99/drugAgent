package com.liang.drugagent.advisor;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一日志监控 Advisor
 * 作用：拦截调用过程，打印完整的请求与响应参数，包括耗时等，便于研发阶段的调试与监控
 */
public class LoggingAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);

    @NotNull
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        logRequest(advisedRequest);
        long startTime = System.currentTimeMillis();

        try {
            AdvisedResponse response = chain.nextAroundCall(advisedRequest);
            long costTime = System.currentTimeMillis() - startTime;
            logResponse(response, costTime);
            return response;
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("【AI 同步调用链路异常】总耗时: {} ms, 异常信息: {}", costTime, e.getMessage(), e);
            throw e;
        }
    }

    @NotNull
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        logRequest(advisedRequest);
        long startTime = System.currentTimeMillis();

        return chain.nextAroundStream(advisedRequest)
                .doOnComplete(() -> {
                    long costTime = System.currentTimeMillis() - startTime;
                    log.info("【AI 流式响应链路结束】流式文本接收完毕，总耗时: {} ms", costTime);
                })
                .doOnError(e -> {
                    long costTime = System.currentTimeMillis() - startTime;
                    log.error("【AI 流式调用链路异常】总耗时: {} ms, 异常信息: {}", costTime, e.getMessage(), e);
                });
    }

    private void logRequest(AdvisedRequest request) {
        if (!log.isInfoEnabled()) {
            return;
        }
        List<Message> messages = request.messages();
        String currentMessagesStr = messages.stream()
                .map(m -> String.format("[%s]: %s", m.getMessageType().name(), m.getText()))
                .collect(Collectors.joining("\n  "));

        log.info("AI request started: adviseContext={}, messages=\n{}",
                request.adviseContext(),
                currentMessagesStr);
    }

    private void logResponse(AdvisedResponse response, long costTime) {
        if (!log.isInfoEnabled()) {
            return;
        }
        if (response != null && response.response() != null) {
            ChatResponse chatResponse = response.response();
            String outputStr = chatResponse.getResults().stream()
                    .map(r -> r.getOutput().getText())
                    .collect(Collectors.joining("\n  "));

            log.info("AI response completed: durationMs={}, usage={}, content=\n{}",
                    costTime,
                    chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : "未知",
                    outputStr);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @NotNull
    @Override
    public String getName() {
        return "LoggingAdvisor";
    }
}
