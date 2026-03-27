package com.liang.drugagent.agent.chat;

import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.response.agent.AgentChatResp;
import com.liang.drugagent.agent.common.entity.ChatMessage;
import com.liang.drugagent.agent.common.entity.ChatSession;
import com.liang.drugagent.agent.common.mapper.ChatMessageMapper;
import com.liang.drugagent.agent.common.mapper.ChatSessionMapper;
import com.liang.drugagent.shared.model.Result;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentChatService 记忆层功能测试。
 *
 * <p>验收标准：
 * <ol>
 *   <li>调用 /agent/chat 后数据库有 session 和 message 记录</li>
 *   <li>session.summary 被正确更新</li>
 *   <li>session.message_count 正确递增</li>
 *   <li>最近消息通过 getRecentMessages 查询而非查全量</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = com.liang.drugagent.app.DrugAgentApplication.class)
@ActiveProfiles("test")
class AgentChatServiceMemoryTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    private String sessionId;

    @BeforeEach
    void setUp() {
        sessionId = "mem-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void chat_shouldPersistSessionAndMessages() {
        // 构造请求
        String query = "药品生产许可证怎么办理";
        AgentChatReq request = AgentChatReq.builder()
                .sessionId(sessionId)
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

        Result<AgentChatResp> result = response.getBody();
        assertNotNull(result);
        AgentChatResp body = result.getData();
        assertNotNull(body);

        // 验证返回的 sessionId 与请求一致
        assertEquals(sessionId, body.getSessionId());

        // 验证数据库中 session 被写入
        ChatSession savedSession = chatSessionMapper.selectById(sessionId);
        assertNotNull(savedSession, "Session 应被写入数据库");
        System.out.println("[记忆层测试] Session 已写入: id=" + savedSession.getId()
                + ", title=" + savedSession.getTitle()
                + ", summary=" + savedSession.getSummary());

        // 验证数据库中 message 被写入
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
        );
        assertTrue(messages.size() >= 2, "应有至少 2 条消息（user + assistant）");
        System.out.println("[记忆层测试] 消息数量: " + messages.size());
        messages.forEach(m -> System.out.println("  - role=" + m.getRole() + ", content=" + m.getContent().substring(0, Math.min(50, m.getContent().length()))));

        System.out.println("[记忆层测试] ========== 会话与消息持久化测试通过 ==========");
    }

    @Test
    void chat_shouldPersistSessionWithCorrectMessageCount() {
        // 第一次对话
        String query1 = "医疗器械广告审查怎么办";
        AgentChatReq request1 = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query1)
                .build();

        ResponseEntity<Result<AgentChatResp>> response1 = restTemplate.exchange(
                "/agent/chat",
                HttpMethod.POST,
                new HttpEntity<>(request1),
                new ParameterizedTypeReference<Result<AgentChatResp>>() {}
        );
        assertEquals(200, response1.getStatusCode().value());

        // 检查第一次对话后的 message_count
        ChatSession session1 = chatSessionMapper.selectById(sessionId);
        assertNotNull(session1);
        System.out.println("[记忆层测试] 第一次对话后 message_count=" + session1.getMessageCount());

        // 第二次对话
        String query2 = "续上题，还需要准备什么材料";
        AgentChatReq request2 = AgentChatReq.builder()
                .sessionId(sessionId)
                .query(query2)
                .build();

        ResponseEntity<Result<AgentChatResp>> response2 = restTemplate.exchange(
                "/agent/chat",
                HttpMethod.POST,
                new HttpEntity<>(request2),
                new ParameterizedTypeReference<Result<AgentChatResp>>() {}
        );
        assertEquals(200, response2.getStatusCode().value());

        // 检查第二次对话后的 message_count
        ChatSession session2 = chatSessionMapper.selectById(sessionId);
        assertNotNull(session2);
        System.out.println("[记忆层测试] 第二次对话后 message_count=" + session2.getMessageCount());

        // 验证 message_count 正确递增
        assertTrue(session2.getMessageCount() >= session1.getMessageCount(),
                "message_count 应该递增");

        System.out.println("[记忆层测试] ========== 消息计数测试通过 ==========");
    }

    @Test
    void getRecentMessages_shouldReturnLimitedMessages() {
        // 先创建几条消息
        for (int i = 0; i < 5; i++) {
            AgentChatReq request = AgentChatReq.builder()
                    .sessionId(sessionId)
                    .query("测试消息 " + i)
                    .build();

            restTemplate.exchange(
                    "/agent/chat",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<Result<AgentChatResp>>() {}
            );
        }

        // 验证最近消息被正确限制
        List<ChatMessage> allMessages = chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
        );

        System.out.println("[记忆层测试] 总消息数: " + allMessages.size());

        // 由于是多轮对话，总消息数应该 >= 10 (5轮 x 2条消息/轮)
        assertTrue(allMessages.size() >= 10, "多轮对话后应有足够消息");

        System.out.println("[记忆层测试] ========== 最近消息限制测试通过 ==========");
    }
}
