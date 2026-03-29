package com.liang.drugagent.shared.rag;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagToolResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagToolFacade {

    private final MultiStrategyRagOrchestrator orchestrator;

    public RagToolFacade(MultiStrategyRagOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public RagToolResult execute(SceneEnum scene,
                                 String reviewFocus,
                                 String queryText,
                                 List<String> tags,
                                 String sessionId) {
        RagQueryContext context = RagQueryContext.builder()
                .scene(scene)
                .reviewFocus(reviewFocus)
                .queryText(queryText)
                .topicTags(tags)
                .sessionId(sessionId)
                .build();
        return orchestrator.execute(context);
    }
}
