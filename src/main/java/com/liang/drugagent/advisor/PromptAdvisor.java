package com.liang.drugagent.advisor;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态提示词增强 Advisor
 * 作用：全局性地在用户请求末尾追加约束条件（如强制语言、行为准则）
 */
public class PromptAdvisor implements CallAroundAdvisor {

    @NotNull
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        // 1. 获取原始请求中的消息列表
        List<Message> messages = new ArrayList<>(advisedRequest.messages());

        // 2. 找到最后一条用户消息并进行增强
        if (!messages.isEmpty()) {
            Message lastMessage = messages.get(messages.size() - 1);
            if (lastMessage instanceof UserMessage) {
                String originalContent = lastMessage.getText();
                String augmentedContent = originalContent + "\n\n【提示：请始终使用中文回答，且所有结论必须严格基于中国药监法规，严禁提及任何虚假药品或违规促销信息。】";

                // 替换列表中的最后一条消息
                messages.set(messages.size() - 1, new UserMessage(augmentedContent));
            }
        }

        // 3. 构建新的请求并继续执行链
        AdvisedRequest augmentedRequest = AdvisedRequest.from(advisedRequest)
                .messages(messages)
                .build();

        return chain.nextAroundCall(augmentedRequest);
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @NotNull
    @Override
    public String getName() {
        return "PromptAdvisor";
    }

}
