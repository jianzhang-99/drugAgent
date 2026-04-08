package com.liang.drugagent.shared.llm;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashScopeLlmClientIntegrationTest {

    @Test
    void shouldChatWithQwen35Plus() throws Exception {
        DashScopeLlmClient client = createClient();

        LlmResponse response = client.chat(buildRequest(false));

        assertTrue(Boolean.TRUE.equals(response.getSuccess()), response.getErrorMessage());
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isBlank(), "同步响应不应为空");
        assertEquals("qwen3.5-plus", response.getModel());
    }

    @Test
    void shouldStreamWithQwen35Plus() throws Exception {
        DashScopeLlmClient client = createClient();

        String firstChunk = client.streamChat(buildRequest(true))
                .map(LlmResponse::getContent)
                .filter(text -> text != null && !text.isBlank())
                .next()
                .block(Duration.ofSeconds(60));

        assertNotNull(firstChunk, "流式响应应返回正文分片");
        assertFalse(firstChunk.isBlank(), "流式首个正文分片不应为空");
    }

    private DashScopeLlmClient createClient() throws Exception {
        Map<String, Object> root = loadLocalYaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> aliyun = (Map<String, Object>) root.get("aliyun");
        @SuppressWarnings("unchecked")
        Map<String, Object> dashscope = (Map<String, Object>) aliyun.get("dashscope");

        return new DashScopeLlmClient(
                stringValue(dashscope.get("api-key")),
                stringValue(dashscope.get("chat-model"))
        );
    }

    private LlmRequest buildRequest(boolean stream) {
        return LlmRequest.builder()
                .provider(LlmProviderType.DASHSCOPE)
                .model("qwen3.5-plus")
                .sessionId("dashscope-it")
                .systemPrompt("你是一个简洁的助手，请用一句中文回答。")
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content("请用一句话介绍你自己。")
                        .build()))
                .stream(stream)
                .temperature(0.2f)
                .topP(0.8f)
                .maxTokens(128)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadLocalYaml() throws Exception {
        Path path = Path.of("src/main/resources/application-local.yml");
        try (InputStream inputStream = Files.newInputStream(path)) {
            return new Yaml().load(inputStream);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
