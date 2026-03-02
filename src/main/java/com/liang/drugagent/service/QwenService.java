package com.liang.drugagent.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.liang.drugagent.config.QwenConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 千问调用服务
 * 封装 DashScope SDK 的调用逻辑，提供简洁的业务方法
 */
@Slf4j
@Service
public class QwenService {

    private final QwenConfig qwenConfig;

    public QwenService(QwenConfig qwenConfig) {
        this.qwenConfig = qwenConfig;
    }

    /**
     * 调用千问模型进行对话 (兼容旧方法)
     *
     * @param prompt 用户输入的提示词
     * @return 模型返回的文本内容
     */
    public String chat(String prompt) {
        return chatWithSystem("You are a helpful assistant.", prompt);
    }
    
    /**
     * 带System Prompt的单轮对话（药品分析专用）
     * 
     * @param systemPrompt 系统角色设定
     * @param userPrompt 用户业务提示词
     * @return 模型返回内容
     */
    public String chatWithSystem(String systemPrompt, String userPrompt) {
        return chatWithMessages(Arrays.asList(
                Message.builder().role(Role.SYSTEM.getValue()).content(systemPrompt).build(),
                Message.builder().role(Role.USER.getValue()).content(userPrompt).build()
        ));
    }
    
    /**
     * 多轮消息对话（合规对话及通用场景使用）
     * 
     * @param messages 历史与当前消息列表
     * @return 模型返回内容
     */
    public String chatWithMessages(java.util.List<Message> messages) {
        Generation gen = new Generation();
        GenerationParam param = GenerationParam.builder()
                .apiKey(qwenConfig.getApiKey())
                .model("qwen-plus")
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .temperature(0.7f)
                .maxTokens(4096)
                .build();
                
        try {
            GenerationResult result = gen.call(param);
            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("千问多轮对话调用成功，返回长度={}", content.length());
            return content;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("千问多轮对话调用异常", e);
            throw new RuntimeException("AI服务暂时不可用，请稍后重试", e);
        }
    }
}
