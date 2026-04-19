package com.liang.drugagent.shared.llm;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashScopeLlmClientTest {

    @Test
    void shouldDetectThinkingCapableModels() throws Exception {
        DashScopeLlmClient client = new DashScopeLlmClient("", "qwen3.5-plus");
        Method method = DashScopeLlmClient.class.getDeclaredMethod("supportsThinkingMode", String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(client, "qwen3.5-plus"));
        assertTrue((Boolean) method.invoke(client, "qwen3-235b-a22b"));
        assertTrue((Boolean) method.invoke(client, "qwq-32b"));
        assertFalse((Boolean) method.invoke(client, "qwen-plus-2025-07-28"));
    }
}
