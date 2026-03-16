package com.liang.drugagent.controller;

import com.liang.drugagent.service.DrugAgentService;
import com.liang.drugagent.domain.GeneralResponse;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Drug Agent 入口。
 *
 * <p>前端或外部系统只需要调用这一个接口，
 * 后端再在内部完成场景识别和流程编排。</p>
 */
@RestController
@RequestMapping("/agent/drug")
public class DrugAgentController {

    private final DrugAgentService drugAgentService;

    public DrugAgentController(DrugAgentService drugAgentService) {
        this.drugAgentService = drugAgentService;
    }

    @Operation(summary = "Drug Agent 对话入口")
    @PostMapping("/chat")
    public GeneralResponse<DrugAgentResp> chat(@RequestBody DrugAgentReq req) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            return GeneralResponse.error("query 不能为空");
        }
        return GeneralResponse.success(drugAgentService.handle(req));
    }

    @Operation(summary = "Drug Agent 流式对话入口")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody DrugAgentReq req) {
        return drugAgentService.streamHandle(req);
    }
}
