package com.liang.drugagent.llm;

import com.liang.drugagent.config.MiniMaxProperties;
import com.liang.drugagent.llm.model.LlmProviderType;
import com.liang.drugagent.llm.model.LlmRequest;
import com.liang.drugagent.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MiniMax LLM 接入测试
 */
@SpringBootTest
public class MiniMaxLlmClientTest {

    @Autowired
    private MiniMaxLlmClient miniMaxLlmClient;

    @Test
    public void shouldInjectMiniMaxClient() {
        assertNotNull(miniMaxLlmClient, "MiniMaxLlmClient should be injected");
    }

    @Test
    public void shouldReturnCorrectProvider() {
        assertEquals(LlmProviderType.MINIMAX, miniMaxLlmClient.getProvider());
    }

    @Test
    public void shouldCheckAvailability() {
        boolean available = miniMaxLlmClient.isAvailable();
        System.out.println("MiniMax available: " + available);
        // 如果配置了 API key 则应该可用
        assertTrue(available, "MiniMax should be available when API key is configured");
    }

    @Test
    public void shouldChatSuccessfully() {
        // 跳过测试如果没有配置真实的 API Key
        String apiKey = System.getProperty("minimax.api-key", "");
        if (apiKey.isBlank()) {
            System.out.println("SKIP: Please set -Dminimax.api-key=your-real-api-key to run this test");
            return;
        }

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                        LlmRequest.ChatMessage.builder()
                                .role("user")
                                .content("请简单介绍一下你自己")
                                .build()
                ))
                .temperature(0.7f)
                .maxTokens(500)
                .build();

        LlmResponse response = miniMaxLlmClient.chat(request);

        System.out.println("=== MiniMax Chat Response ===");
        System.out.println("Success: " + response.getSuccess());
        System.out.println("Provider: " + response.getProvider());
        System.out.println("Content: " + response.getContent());
        if (response.getErrorMessage() != null) {
            System.out.println("Error: " + response.getErrorMessage());
        }

        assertTrue(response.getSuccess(), "Chat should be successful");
        assertNotNull(response.getContent(), "Content should not be null");
        assertTrue(response.getContent().length() > 0, "Content should not be empty");
    }

    @Test
    public void shouldReturnModelInfo() {
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(
                        LlmRequest.ChatMessage.builder()
                                .role("user")
                                .content("你是什么模型？请简单回答。")
                                .build()
                ))
                .temperature(0.7f)
                .maxTokens(200)
                .build();

        LlmResponse response = miniMaxLlmClient.chat(request);

        System.out.println("=== MiniMax Model Info ===");
        System.out.println("Success: " + response.getSuccess());
        System.out.println("Model: " + response.getModel());
        System.out.println("Content: " + response.getContent());
        if (response.getErrorMessage() != null) {
            System.out.println("Error: " + response.getErrorMessage());
        }

        assertTrue(response.getSuccess(), "Chat should be successful");
        assertNotNull(response.getContent(), "Content should not be null");
    }
}
