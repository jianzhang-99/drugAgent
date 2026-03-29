package com.liang.drugagent.shared.rag.strategy;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlagiarismRagStrategy extends AbstractRagStrategy {

    public static final String CODE = "PLAGIARISM";

    @Override
    public String strategyCode() {
        return CODE;
    }

    @Override
    public boolean support(RagQueryContext context) {
        return super.support(context) && context.getScene() == SceneEnum.TENDER_REVIEW;
    }

    @Override
    public RagStrategyResult execute(RagQueryContext context) {
        String query = context.getQueryText() == null ? "" : context.getQueryText();
        String focus = context.getReviewFocus() == null ? "" : context.getReviewFocus();
        String text = (query + " " + focus).toLowerCase();

        double score = 30D;
        List<String> evidence = new ArrayList<>();
        if (containsAny(text, "抄袭", "雷同", "重复", "复制", "copy", "plagiarism")) {
            score += 30D;
            evidence.add("命中抄袭相关关键词");
        }
        if (containsAny(text, "相似度", "文本比对", "近似", "语义重复")) {
            score += 20D;
            evidence.add("命中文本相似检测信号");
        }
        if (context.getTopicTags() != null && !context.getTopicTags().isEmpty()) {
            score += 10D;
            evidence.add("存在主题标签辅助检索");
        }
        score = Math.min(score, 100D);
        String risk = score >= 75D ? "HIGH" : score >= 45D ? "MEDIUM" : "LOW";
        String summary = risk.equals("LOW") ? "未发现明显抄袭风险信号" : "检测到抄袭/雷同相关风险信号";
        return buildSimpleResult(score, risk, evidence, summary);
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
