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
     * 调用千问模型进行对话
     *
     * @param prompt 用户输入的提示词
     * @return 模型返回的文本内容
     */
    public String chat(String prompt) {
        Generation gen = new Generation();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(qwenConfig.getApiKey())  // 直接通过参数传入 API Key
                .model("qwen-turbo")
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .build();

        try {
            GenerationResult result = gen.call(param);
            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("千问调用成功，prompt={}, response={}", prompt, content);
            return content;
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用千问接口异常", e);
            return "调用千问接口异常: " + e.getMessage();
        }
    }
}
