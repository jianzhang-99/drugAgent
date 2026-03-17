package com.liang.drugagent;

import com.liang.drugagent.service.AgentChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 基础对话服务装配测试。
 */
@SpringBootTest
public class QwenServiceTest {

    @Autowired
    private AgentChatService agentChatService;

    @Test
    public void shouldLoadAgentChatService() {
        assertNotNull(agentChatService);
    }
}
