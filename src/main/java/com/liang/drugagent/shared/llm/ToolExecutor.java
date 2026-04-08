package com.liang.drugagent.shared.llm;

import java.util.Map;

/**
 * 工具执行器接口。
 *
 * <p>用于 Function Calling 场景下，根据 LLM 返回的工具调用请求执行具体工具。
 * 每个工具实现类负责解析参数并执行对应的业务逻辑。</p>
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
public interface ToolExecutor {

    /**
     * 获取该执行器支持的工具名称。
     *
     * @return 工具名称（与 LLM schema 中的 name 对应）
     */
    String getToolName();

    /**
     * 执行工具。
     *
     * @param arguments 工具参数（从 LLM 返回的 JSON 参数解析而来）
     * @param sessionId 会话 ID（用于上下文传递）
     * @return 工具执行结果（JSON 字符串）
     */
    String execute(Map<String, Object> arguments, String sessionId);

    /**
     * 获取工具的描述。
     *
     * @return 工具用途描述
     */
    default String getDescription() {
        return "工具: " + getToolName();
    }
}
