package com.liang.drugagent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 千问模型调用服务。
 */
@Service
public class QwenService {

    private final ChatClient chatClient;

    public QwenService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 发送单轮对话请求。
     *
     * @param prompt 提示词
     * @return 模型回复文本
     */
    public String chat(String prompt) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return "调用千问接口异常: " + e.getMessage();
        }
    }
}
