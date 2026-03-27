package com.liang.drugagent.agent.chat;

import com.liang.drugagent.agent.prompt.SystemPrompt;
import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.LlmProviderType;
import com.liang.drugagent.shared.llm.LlmRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 基础模型对话服务。
 *
 * <p>作为AI对话的统一入口，支持多Provider动态路由：
 * <ul>
 *   <li>前端传入 model 参数（如 "minimax", "dashscope"）选择使用的 LLM</li>
 *   <li>默认使用 MiniMax</li>
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
 */
@Slf4j
@Service
public class LLMChatService {

    /**
     * 场景与 System Prompt 映射表。
     */
    private static final Map<SceneEnum, String> SCENE_PROMPT_MAP = Map.of(
            SceneEnum.TENDER_REVIEW, SystemPrompt.TENDER_REVIEW_PROMPT,
            SceneEnum.CONTRACT_PRECHECK, SystemPrompt.CONTRACT_PRECHECK_PROMPT,
            SceneEnum.RISK_ALERT, SystemPrompt.RISK_ALERT_PROMPT,
            SceneEnum.DEFAULT, SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT
    );

    private final List<LlmClient> llmClients;

    public LLMChatService(List<LlmClient> llmClients) {
        this.llmClients = llmClients;
    }

    /**
     * 根据场景、会话ID和模型选择执行对话 (支持多轮记忆)
     *
     * @param userMessage 用户消息
     * @param scene 场景枚举
     * @param sessionId 会话ID
     * @param model 模型标识（minimax/dashscope），为空则使用默认
     * @return AI响应内容
     */
    public String chatWithScene(String userMessage, SceneEnum scene, String sessionId, String model) {
        LlmClient client = selectClient(model);
        String systemPromptText = resolveSystemPrompt(scene);
        String effectiveModel = resolveEffectiveModel(model, client.getProvider());

        LlmRequest request = LlmRequest.builder()
                .provider(client.getProvider())
                .model(effectiveModel)
                .sessionId(sessionId)
                .systemPrompt(systemPromptText)
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(userMessage)
                        .build()))
                .stream(false)
                .build();

        return client.chat(request).getContent();
    }

    /**
     * 根据场景、会话ID和模型执行流式对话，适合前端 SSE 打字机效果。
     */
    public Flux<String> streamChatWithScene(String userMessage, SceneEnum scene, String sessionId, String model) {
        LlmClient client = selectClient(model);
        String systemPromptText = resolveSystemPrompt(scene);
        String effectiveModel = resolveEffectiveModel(model, client.getProvider());

        LlmRequest request = LlmRequest.builder()
                .provider(client.getProvider())
                .model(effectiveModel)
                .sessionId(sessionId)
                .systemPrompt(systemPromptText)
                .messages(List.of(LlmRequest.ChatMessage.builder()
                        .role("user")
                        .content(userMessage)
                        .build()))
                .stream(true)
                .build();

        return client.streamChat(request)
                .map(r -> r.getContent() != null ? r.getContent() : "");
    }

    /**
     * 根据模型标识和provider解析最终使用的模型名称。
     * 当模型标识为空时，使用provider对应的默认模型。
     */
    private String resolveEffectiveModel(String model, LlmProviderType provider) {
        if (model != null && !model.isBlank()) {
            return model;
        }
        return LlmProviderType.MINIMAX.equals(provider) ? "MiniMax-M2.7" : "qwen-plus";
    }

    /**
     * 根据模型标识选择对应的 LLM Client
     */
    private LlmClient selectClient(String model) {
        if (model == null || model.isBlank()) {
            return llmClients.stream()
                    .filter(c -> c.supports(LlmProviderType.MINIMAX))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("未找到可用的 MiniMax LLM Client"));
        }

        LlmProviderType providerType = LlmProviderType.fromConfigKey(model);
        return llmClients.stream()
                .filter(c -> c.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到可用的 LLM Client: " + model));
    }

    private String resolveSystemPrompt(SceneEnum scene) {
        if (scene == null) {
            return SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT;
        }
        return SCENE_PROMPT_MAP.getOrDefault(scene, SystemPrompt.DRUG_REGULATION_EXPERT_PROMPT);
    }
}
