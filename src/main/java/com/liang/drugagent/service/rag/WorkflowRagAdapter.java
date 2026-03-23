package com.liang.drugagent.service.rag;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.domain.req.KnowledgeAskReq;
import com.liang.drugagent.domain.resp.KnowledgeAskResp;
import com.liang.drugagent.domain.workflow.EvidenceItem;
import com.liang.drugagent.domain.workflow.RagOutcome;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 将统一 RAG 结果适配为 Workflow 结果。
 */
@Component
public class WorkflowRagAdapter {

    private final KnowledgeRagService knowledgeRagService;

    public WorkflowRagAdapter(KnowledgeRagService knowledgeRagService) {
        this.knowledgeRagService = knowledgeRagService;
    }

    public RagOutcome ask(AgentContext context, SceneEnum sceneEnum) {
        KnowledgeAskReq req = new KnowledgeAskReq();
        req.setQuestion(context.getQuery());
        req.setScene(sceneEnum.name());
        req.setSessionId(context.getSessionId());
        req.setOrgId(resolveOrgId(context));
        req.setSubScene(readMetadata(context, "subScene"));
        req.setDocType(readMetadata(context, "docType"));

        KnowledgeAskResp resp = knowledgeRagService.ask(req, sceneEnum);

        RagOutcome outcome = new RagOutcome();
        outcome.setAnswer(resp.getAnswer());
        outcome.setDecision(resp.getDecision());
        outcome.setReason(resp.getReason());
        outcome.setRiskLevel(resp.getRiskLevel());
        outcome.setEvidenceList(buildEvidence(resp));
        return outcome;
    }

    private List<EvidenceItem> buildEvidence(KnowledgeAskResp resp) {
        List<EvidenceItem> evidence = new ArrayList<>();
        if (resp.getCitations() != null) {
            for (KnowledgeAskResp.Citation c : resp.getCitations()) {
                String title = "引用-" + Objects.requireNonNullElse(c.getSourceTitle(), c.getSourceId());
                String content = String.format("chunk=%s score=%.4f snippet=%s",
                        c.getChunkId(),
                        c.getScore() == null ? 0D : c.getScore(),
                        c.getSnippet());
                evidence.add(new EvidenceItem(title, content, "rag"));
            }
        }
        evidence.add(new EvidenceItem("RAG决策", resp.getDecision() + " - " + resp.getReason(), "rag"));
        return evidence;
    }

    private String resolveOrgId(AgentContext context) {
        String fromMetadata = readMetadata(context, "orgId");
        if (fromMetadata != null && !fromMetadata.isBlank()) {
            return fromMetadata;
        }
        return context.getUserId() == null || context.getUserId().isBlank()
                ? "default-org"
                : context.getUserId();
    }

    private String readMetadata(AgentContext context, String key) {
        Object v = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return v == null ? null : String.valueOf(v);
    }
}
