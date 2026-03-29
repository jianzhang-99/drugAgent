package com.liang.drugagent.shared.rag.model;

import com.liang.drugagent.scene.SceneEnum;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagQueryContext {
    SceneEnum scene;
    String reviewFocus;
    String queryText;
    List<String> topicTags;
    String sessionId;
}
