package com.liang.drugagent.controller;

import com.liang.drugagent.agent.chat.AgentChatService;
import com.liang.drugagent.controller.domain.request.agent.AgentChatReq;
import com.liang.drugagent.controller.domain.request.agent.FileChatReq;
import com.liang.drugagent.controller.domain.response.agent.DrugAgentResp;
import com.liang.drugagent.shared.domain.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 统一控制器。
 *
 * <p>聚焦 AI 对话入口能力：
 * <ul>
 *   <li>AI 对话：同步/流式对话、文件上传</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "AI Agent 对话与会话管理")
public class AgentController {

    private final AgentChatService agentChatService;

    @Operation(summary = "同步对话")
    @PostMapping("/chat")
    public Result<DrugAgentResp> chat(@RequestBody AgentChatReq req) {
        return Result.success(agentChatService.chat(req));
    }

    @Operation(summary = "文件上传对话")
    @PostMapping(value = "/fileChat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DrugAgentResp> fileChat(@ModelAttribute FileChatReq req) {
        return Result.success(agentChatService.fileChat(req));
    }

    @Operation(summary = "流式对话")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentChatReq req) {
        return agentChatService.handleStreamChat(req);
    }
}
