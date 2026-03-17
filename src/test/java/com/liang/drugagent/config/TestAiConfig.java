package com.liang.drugagent.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class TestAiConfig {

    @Bean
    @Primary
    public ChatModel testChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(
                        new Generation(new AssistantMessage("Mock AI response for testing"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("Mock AI response for testing")))));
            }
        };
    }

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                float[] vector = new float[1024];
                Arrays.fill(vector, 0.1f);
                List<Embedding> embeddings = new ArrayList<>();
                for (int i = 0; i < request.getInstructions().size(); i++) {
                    embeddings.add(new Embedding(vector, i));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                float[] vector = new float[1024];
                Arrays.fill(vector, 0.1f);
                return vector;
            }
        };
    }
}
