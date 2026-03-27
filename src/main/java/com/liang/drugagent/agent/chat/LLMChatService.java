package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.prompt.SystemPrompt;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.advisor.LoggingAdvisor;
import com.liang.drugagent.shared.advisor.PromptAdvisor;
import com.liang.drugagent.shared.advisor.SafetyAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 基础模型对话服务。
 *
 * <p>作为AI对话的统一入口，封装Spring AI ChatClient并附加全局Advisor链：</p>
 * <ul>
 *   <li>{@link PromptAdvisor} - Prompt增强，优化输入提示词</li>
 *   <li>{@link SafetyAdvisor} - 安全审查，过滤敏感内容</li>
 *   <li>{@link LoggingAdvisor} - 请求日志，记录对话轨迹</li>
 * </ul>
 *
 * <p>支持两种对话模式：</p>
 * <ul>
 *   <li>同步对话 ({@code chatWithScene}) - 适用于简单问答场景</li>
 *   <li>流式对话 ({@code streamChatWithScene}) - 适用于SSE打字机效果</li>
 * </ul>
 *
 * <p>会根据{@link SceneEnum}自动选择对应的System Prompt，当前支持：</p>
 * <ul>
 *   <li>{@link SceneEnum#DEFAULT} - 默认医药监管专家角色</li>
 *   <li>{@link SceneEnum#RISK_ALERT} - 医疗耗材与药品合规风险预警</li>
 *   <li>{@link SceneEnum#CONTRACT_PRECHECK} - 合同文件AI预审核</li>
 *   <li>{@link SceneEnum#TENDER_REVIEW} - 标书雷同与语义查重</li>
 * </ul>
 *
 * @author liangjiajian
 * @see ChatClient
 */
@Service
public class LLMChatService {

    /**
     * Spring AI ChatClient 实例，用于与AI模型交互。
     */
    private final ChatClient chatClient;

    /**
     * 场景与 System Prompt 映射表。
     */
    private static final Map<SceneEnum, String> SCENE_PROMPT_MAP = Map.of(
            SceneEnum.TENDER_REVIEW, SystemPrompt.TENDER_REVIEW_PROMPT,
            SceneEnum.CONTRACT_PRECHECK, SystemPrompt.CONTRACT_PRECHECK_PROMPT,
            SceneEnum.RISK_ALERT, SystemPrompt.RISK_ALERT_PROMPT,
            SceneEnum.DEFAULT, SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT
    );

    /**
     * 构造方法，注入ChatClient构建器并配置全局Advisor链。
     *
     * @param chatClientBuilder ChatClient构建器
     */
    public LLMChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(
                        new PromptAdvisor(),
                        new SafetyAdvisor(),
                        new LoggingAdvisor()
                )
                .build();
    }


    /**
     * 根据场景和会话ID执行对话 (支持多轮记忆)
     */
    public String chatWithScene(String userMessage, SceneEnum scene, String sessionId) {
        String systemPromptText = resolveSystemPrompt(scene);

        return chatClient.prompt()
                .system(systemPromptText)
                .user(userMessage)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId)
                               .param("chat_memory_response_size", 10))
                .call()
                .content();
    }

    /**
     * 根据场景和会话ID执行流式对话，适合前端 SSE 打字机效果。
     */
    public Flux<String> streamChatWithScene(String userMessage, SceneEnum scene, String sessionId) {
        String systemPromptText = resolveSystemPrompt(scene);

        return chatClient.prompt()
                .system(systemPromptText)
                .user(userMessage)
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId)
                        .param("chat_memory_response_size", 10))
                .stream()
                .content();
    }

    private String resolveSystemPrompt(SceneEnum scene) {
        if (scene == null) {
            return SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT;
        }
        return SCENE_PROMPT_MAP.getOrDefault(scene, SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT);
    }
}
