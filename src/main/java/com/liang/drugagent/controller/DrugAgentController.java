package com.liang.drugagent.controller;

import com.liang.drugagent.service.DrugAgentService;
import com.liang.drugagent.domain.Result;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.resp.DrugAgentResp;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Drug Agent 入口。
 *
 * <p>前端或外部系统只需要调用这一个接口，
 * 后端再在内部完成场景识别和流程编排。</p>
 *
 * @author liangjiajian
 */
@RestController
@RequestMapping("/agent/drug")
public class DrugAgentController {

    private static final Logger log = LoggerFactory.getLogger(DrugAgentController.class);

    private final DrugAgentService drugAgentService;

    public DrugAgentController(DrugAgentService drugAgentService) {
        this.drugAgentService = drugAgentService;
    }

    @Operation(summary = "Drug Agent 对话入口")
    @PostMapping("/chat")
    public Result<DrugAgentResp> chat(@RequestBody DrugAgentReq req) {
        if (req == null || ((req.getQuery() == null || req.getQuery().isBlank())
                && (req.getFileIds() == null || req.getFileIds().isEmpty()))) {
            log.warn("Reject empty chat request");
            return Result.error("query 和 fileIds 不能同时为空");
        }
        log.info("Receive sync chat request: sessionId={}, userId={}, queryLength={}",
                req.getSessionId(), req.getUserId(), req.getQuery() == null ? 0 : req.getQuery().length());
        return Result.success(drugAgentService.handle(req));
    }

    @Operation(summary = "Drug Agent 统一文件任务入口")
    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DrugAgentResp> submit(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "sceneHint", required = false) String sceneHint,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy,
            @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.error("请至少上传一个文件");
        }
        return Result.success(drugAgentService.handleUploadedFiles(query, sceneHint, sessionId, userId, submittedBy, files));
    }

    @Operation(summary = "Drug Agent 流式对话入口")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody DrugAgentReq req) {
        log.info("Receive stream chat request: sessionId={}, userId={}, queryLength={}",
                req == null ? null : req.getSessionId(),
                req == null ? null : req.getUserId(),
                req == null || req.getQuery() == null ? 0 : req.getQuery().length());
        return drugAgentService.streamHandle(req);
    }
}
