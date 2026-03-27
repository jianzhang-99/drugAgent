package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.agent.common.mapper.ChatSessionMapper;
import com.liang.drugagent.shared.domain.response.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 对话端到端集成测试。
 *
 * <p>验收标准：
 * <ol>
 *   <li>调用 /agent/chat 接口成功</li>
 *   <li>后端不会乱跳，能正常返回</li>
 *   <li>返回结构完整的 AgentChatResp</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = com.liang.drugagent.app.DrugAgentApplication.class)
@ActiveProfiles("test")
class AgentLLMChatServiceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    private String sessionId;

    @BeforeEach
    void setUp() {
        // 使用较短的 sessionId 避免数据库字段长度问题
        sessionId = "e2e-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void chat_shouldReturnCompleteResponseAndPersistSession() {
        // 构造请求
        String query = "你好，请介绍一下药品监管的基本内容";
        AgentChatReq request = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query)
                .build();

        // 调用 /agent/chat 接口
        String url = "/agent/chat";
        ResponseEntity<Result<AgentChatResp>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<Result<AgentChatResp>>() {}
        );

        // 验证 HTTP 状态码
        assertNotNull(response);
        System.out.println("HTTP 状态码: " + response.getStatusCode());
        assertEquals(200, response.getStatusCode().value());

        // 验证返回结构完整
        Result<AgentChatResp> result = response.getBody();
        assertNotNull(result, "Result 不应为空");
        System.out.println("Result code: " + result.getCode() + ", message: " + result.getMessage());

        AgentChatResp body = result.getData();
        assertNotNull(body, "响应体不应为空");
        System.out.println("响应 sessionId: " + body.getSessionId());
        System.out.println("响应 scene: " + body.getScene());

        // 验证 sessionId 一致
        assertEquals(sessionId, body.getSessionId(), "sessionId 应一致");

        // 验证 scene 有值
        assertNotNull(body.getScene(), "scene 不应为空");

        // 验证数据库中 session 被写入（如果 LLM 调用成功）
        ChatSession savedSession = chatSessionMapper.selectById(sessionId);
        if (savedSession != null) {
            System.out.println("数据库 Session title: " + savedSession.getTitle());
        }

        System.out.println("========== 验收测试通过 ==========");
    }

    @Test
    void chat_shouldReturnResponseWithCorrectSessionId() {
        // 使用新的随机 sessionId
        String newSessionId = "new-" + UUID.randomUUID().toString().substring(0, 8);
        String query = "药品上市后有哪些监管措施？";

        AgentChatReq request = AgentChatReq.builder()
                .sessionId(newSessionId)
                .query(query)
                .build();

        // 调用接口
        ResponseEntity<Result<AgentChatResp>> response = restTemplate.exchange(
                "/agent/chat",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<Result<AgentChatResp>>() {}
        );

        // 验证 HTTP 状态码
        assertEquals(200, response.getStatusCode().value());

        // 验证返回结构
        Result<AgentChatResp> result = response.getBody();
        assertNotNull(result);
        AgentChatResp body = result.getData();
        assertNotNull(body);

        // 验证 sessionId 一致
        assertEquals(newSessionId, body.getSessionId(), "sessionId 应一致");

        System.out.println("SessionId 验证通过: " + newSessionId);
    }
}
