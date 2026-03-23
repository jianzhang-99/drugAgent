package com.liang.drugagent.service;

import com.liang.drugagent.llm.LlmFacadeService;
import com.liang.drugagent.llm.model.LlmRequest;
import com.liang.drugagent.llm.model.LlmResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 千问模型调用服务（改造后使用LlmFacadeService）
 * 兼容百炼和MiniMax多Provider
 */
@Service
public class QwenService {

    private final LlmFacadeService llmFacadeService;

    public QwenService(LlmFacadeService llmFacadeService) {
        this.llmFacadeService = llmFacadeService;
    }

    /**
     * 发送单轮对话请求
     *
     * @param prompt 提示词
     * @return 模型回复文本
     */
    public String chat(String prompt) {
        return chat(prompt, null);
    }

    /**
     * 发送单轮对话请求（带系统提示词）
     *
     * @param prompt 提示词
     * @param systemPrompt 系统提示词（可选）
     * @return 模型回复文本
     */
    public String chat(String prompt, String systemPrompt) {
        try {
            LlmRequest request = LlmRequest.builder()
                    .systemPrompt(systemPrompt)
                    .messages(List.of(LlmRequest.ChatMessage.builder()
                            .role("user")
                            .content(prompt)
                            .build()))
                    .build();
            LlmResponse response = llmFacadeService.chat(request);
            if (Boolean.TRUE.equals(response.getSuccess())) {
                return response.getContent();
            }
            return "调用LLM接口异常: " + response.getErrorMessage();
        } catch (Exception e) {
            return "调用LLM接口异常: " + e.getMessage();
        }
    }
}
