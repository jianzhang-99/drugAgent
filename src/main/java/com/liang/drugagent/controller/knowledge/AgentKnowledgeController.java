package com.liang.drugagent.controller.knowledge;

import com.liang.drugagent.shared.domain.response.Result;
import com.liang.drugagent.controller.request.knowledge.KnowledgeAskReq;
import com.liang.drugagent.controller.request.knowledge.KnowledgeIngestTextReq;
import com.liang.drugagent.controller.response.knowledge.KnowledgeAskResp;
import com.liang.drugagent.agent.SceneEnum;
import com.liang.drugagent.shared.rag.KnowledgeIngestService;
import com.liang.drugagent.shared.rag.KnowledgeRagService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一 RAG 能力接口。
 *
 * <p>提供知识库的统一问答和入库能力：
 * <ul>
 *   <li>知识文本入库：将文本切分、索引后存入向量库</li>
 *   <li>多场景 RAG 问答：支持按场景定制的检索增强问答</li>
 * </ul>
 *
 * @author drug-agent
 */
@RestController
@RequestMapping("/agent/knowledge")
@CrossOrigin(origins = "*")
public class AgentKnowledgeController {

    private final KnowledgeIngestService knowledgeIngestService;
    private final KnowledgeRagService knowledgeRagService;

    public AgentKnowledgeController(KnowledgeIngestService knowledgeIngestService,
                                    KnowledgeRagService knowledgeRagService) {
        this.knowledgeIngestService = knowledgeIngestService;
        this.knowledgeRagService = knowledgeRagService;
    }

    /**
     * 知识文本入库。
     *
     * <p>将文本内容切分为 chunks，建立索引后存入向量库，
     * 供后续问答检索使用。</p>
     *
     * @param req 入库请求（包含文本内容、来源等）
     * @return 入库结果（包含 chunk 数量）
     */
    @Operation(summary = "知识文本入库")
    @PostMapping("/ingest/text")
    public Result<Map<String, Object>> ingestText(@RequestBody KnowledgeIngestTextReq req) {
        int chunkCount = knowledgeIngestService.ingestText(req);
        Map<String, Object> data = new HashMap<>();
        data.put("chunkCount", chunkCount);
        return Result.success(data);
    }

    /**
     * 多场景统一 RAG 问答。
     *
     * <p>根据请求中指定的场景，使用对应的 RAG 策略进行问答。
     * 支持混合检索（BM25 + 向量）+ RRF 融合排序。</p>
     *
     * @param req 问答请求（包含问题、场景等）
     * @return 问答结果
     */
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
