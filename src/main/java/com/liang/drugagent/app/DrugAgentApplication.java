package com.liang.drugagent.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = AnthropicChatAutoConfiguration.class)
@ComponentScan(basePackages = "com.liang.drugagent")
@MapperScan({"com.liang.drugagent.scene.common.mapper", "com.liang.drugagent.agent.common.mapper", "com.liang.drugagent.shared.rag.mapper", "com.liang.drugagent.scene.tender_review.mapper", "com.liang.drugagent.shared.llm.benchmark"})
public class DrugAgentApplication {

    static {
        // 解决本地开发时系统全局代理导致 DashScope API 请求返回 404 的问题
        String dashscopeHost = "dashscope.aliyuncs.com";
        String currentNoProxy = System.getProperty("http.nonProxyHosts", "");
        if (!currentNoProxy.contains(dashscopeHost)) {
            String newNoProxy = currentNoProxy.isEmpty()
                    ? dashscopeHost
                    : currentNoProxy + "|" + dashscopeHost;
            System.setProperty("http.nonProxyHosts", newNoProxy);
            System.setProperty("https.nonProxyHosts", newNoProxy);
            System.out.println("[DrugAgentApplication] 已将 " + dashscopeHost + " 加入直连列表，绕过系统代理");
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(DrugAgentApplication.class, args);
    }

}
