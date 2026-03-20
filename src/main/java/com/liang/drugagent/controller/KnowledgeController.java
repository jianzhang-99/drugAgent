package com.liang.drugagent.controller;

import com.liang.drugagent.domain.Result;
import com.liang.drugagent.domain.req.KnowledgeAskReq;
import com.liang.drugagent.domain.req.KnowledgeIngestTextReq;
import com.liang.drugagent.domain.resp.KnowledgeAskResp;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.rag.KnowledgeIngestService;
import com.liang.drugagent.service.rag.KnowledgeRagService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 RAG 能力接口。
 */
@RestController
@RequestMapping("/agent/knowledge")
public class KnowledgeController {

    private final KnowledgeIngestService knowledgeIngestService;
    private final KnowledgeRagService knowledgeRagService;

    public KnowledgeController(KnowledgeIngestService knowledgeIngestService,
                               KnowledgeRagService knowledgeRagService) {
        this.knowledgeIngestService = knowledgeIngestService;
        this.knowledgeRagService = knowledgeRagService;
    }

    @Operation(summary = "知识文本入库")
    @PostMapping("/ingest/text")
    public Result<Map<String, Object>> ingestText(@RequestBody KnowledgeIngestTextReq req) {
        int chunkCount = knowledgeIngestService.ingestText(req);
        Map<String, Object> data = new HashMap<>();
        data.put("chunkCount", chunkCount);
        return Result.success(data);
    }

    @Operation(summary = "多场景统一 RAG 问答")
    @PostMapping("/ask")
    public Result<KnowledgeAskResp> ask(@RequestBody KnowledgeAskReq req) {
        SceneEnum scene = SceneEnum.fromHint(req.getScene());
        if (scene == null) {
            scene = SceneEnum.UNKNOWN;
        }
        return Result.success(knowledgeRagService.ask(req, scene));
    }
}
