package com.liang.drugagent.shared.llm;

import com.liang.drugagent.shared.llm.LlmClient;
import com.liang.drugagent.shared.llm.model.LlmProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM Client 工厂
 * 根据 Provider 类型选择对应的 LLM Client 实现
 *
 * @author liangjiajian
 * @since 2026-03-23
 */
@Component
public class LlmClientFactory {

    private final Map<LlmProviderType, LlmClient> clientMap;

    public LlmClientFactory(List<LlmClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        LlmClient::getProvider,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    /**
     * 根据 Provider 类型获取对应的 Client
     *
     * @param provider Provider 类型
     * @return 对应的 LLM Client
     * @throws IllegalArgumentException 如果找不到对应的 Client
     */
    public LlmClient getClient(LlmProviderType provider) {
        LlmClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("未找到 Provider [" + provider + "] 对应的 LLM Client");
        }
        return client;
    }

    /**
     * 获取默认的 LLM Client（DashScope）
     *
     * @return 默认的 LLM Client
     */
    public LlmClient getDefaultClient() {
        return getClient(LlmProviderType.DASHSCOPE);
    }

    /**
     * 检查是否支持指定 Provider
     *
     * @param provider Provider 类型
     * @return true if supported
     */
    public boolean supports(LlmProviderType provider) {
        return clientMap.containsKey(provider);
    }

    /**
     * 获取所有可用的 Provider
     *
     * @return 可用的 Provider 列表
     */
    public List<LlmProviderType> getAvailableProviders() {
        return clientMap.entrySet().stream()
                .filter(e -> e.getValue().isAvailable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
