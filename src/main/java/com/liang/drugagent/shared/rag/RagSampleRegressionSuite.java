package com.liang.drugagent.shared.rag;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.model.RagDecision;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import lombok.Builder;
import lombok.Value;

import java.util.List;

public final class RagSampleRegressionSuite {

    private RagSampleRegressionSuite() {
    }

    public static List<RagSampleCase> defaultCases() {
        return List.of(
                RagSampleCase.builder()
                        .name("high-risk")
                        .context(RagQueryContext.builder()
                                .scene(SceneEnum.TENDER_REVIEW)
                                .reviewFocus("疑似抄袭与雷同，需严格比对")
                                .queryText("投标文件多段与既往标书高度相似，存在复制痕迹")
                                .topicTags(List.of("抄袭", "模板"))
                                .sessionId("regression-high")
                                .build())
                        .expectedDecision(RagDecision.ANSWERED)
                        .expectedRiskLevel("HIGH")
                        .build(),
                RagSampleCase.builder()
                        .name("gray")
                        .context(RagQueryContext.builder()
                                .scene(SceneEnum.TENDER_REVIEW)
                                .reviewFocus("关注模板同源风险")
                                .queryText("部分章节结构类似，但未发现明确复制段落")
                                .topicTags(List.of("template"))
                                .sessionId("regression-gray")
                                .build())
                        .expectedDecision(RagDecision.NEED_HUMAN_REVIEW)
                        .expectedRiskLevel("MEDIUM")
                        .build(),
                RagSampleCase.builder()
                        .name("compliant")
                        .context(RagQueryContext.builder()
                                .scene(SceneEnum.TENDER_REVIEW)
                                .reviewFocus("常规审核")
                                .queryText("投标文件为新编写内容，未发现明显复用")
                                .topicTags(List.of("normal"))
                                .sessionId("regression-compliant")
                                .build())
                        .expectedDecision(RagDecision.NO_HIT)
                        .expectedRiskLevel("LOW")
                        .build()
        );
    }

    @Value
    @Builder
    public static class RagSampleCase {
        String name;
        RagQueryContext context;
        RagDecision expectedDecision;
        String expectedRiskLevel;
    }
}
