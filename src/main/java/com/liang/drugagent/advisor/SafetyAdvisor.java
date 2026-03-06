package com.liang.drugagent.advisor;

import com.alibaba.nacos.shaded.org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import org.springframework.ai.chat.messages.AssistantMessage;
import java.util.List;

/**
 * 内容安全过滤 Advisor
 * 作用：拦截 AI 返回结果中的敏感词汇或不合规表述
 */
public class SafetyAdvisor implements CallAroundAdvisor {

    // 示例停用词库
    private static final List<String> BLACK_LIST = List.of("处方药优惠", "买一送一", "疗效神速", "保证治愈");

    @NotNull
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        // 1. 先执行下游逻辑获取 AI 响应
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);

        // 2. 对响应内容进行安全扫描
        ChatResponse chatResponse = advisedResponse.response();
        if (chatResponse != null && chatResponse.getResult() != null) {
            String content = chatResponse.getResult().getOutput().getText();
            
            boolean hit = BLACK_LIST.stream().anyMatch(content::contains);
            
            if (hit) {
                System.out.println("【安全拦截】检测到 AI 响应包含敏感词汇，执行重置内容。");
                
                String safeMessage = "【系统提示】由于 AI 生成的内容涉及不合规宣传或敏感词汇，该应答已被安全防护系统拦截。请您咨询药学专家获取专业意见。";
                
                Generation safeGeneration = new Generation(new AssistantMessage(safeMessage));
                ChatResponse safeResponse = new ChatResponse(List.of(safeGeneration), chatResponse.getMetadata());
                
                return AdvisedResponse.from(advisedResponse)
                        .response(safeResponse)
                        .build();
            }
        }

        return advisedResponse;
    }

    @Override
    public int getOrder() {
        return 0; // 优先级最高，最后执行 response 检查
    }

    @Override
    public String getName() {
        return "SafetyAdvisor";
    }
}
