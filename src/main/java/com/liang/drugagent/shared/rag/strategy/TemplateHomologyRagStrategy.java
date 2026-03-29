package com.liang.drugagent.shared.rag.strategy;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import com.liang.drugagent.shared.rag.model.RagStrategyResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TemplateHomologyRagStrategy extends AbstractRagStrategy {

    public static final String CODE = "TEMPLATE_HOMOLOGY";

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

        double score = 25D;
        List<String> evidence = new ArrayList<>();
        if (containsAny(text, "模板", "同源", "套版", "范本", "模板化", "homology")) {
            score += 35D;
            evidence.add("命中模板同源关键词");
        }
        if (containsAny(text, "章节结构", "段落结构", "目录结构", "格式一致")) {
            score += 20D;
            evidence.add("命中结构同源信号");
        }
        if (context.getTopicTags() != null && context.getTopicTags().stream().anyMatch(this::isTemplateRelatedTag)) {
            score += 10D;
            evidence.add("topicTags 命中模板相关标签");
        }
        score = Math.min(score, 100D);
        String risk = score >= 75D ? "HIGH" : score >= 45D ? "MEDIUM" : "LOW";
        String summary = risk.equals("LOW") ? "未发现明显模板同源风险" : "检测到模板同源/套版风险信号";
        return buildSimpleResult(score, risk, evidence, summary);
    }

    private boolean isTemplateRelatedTag(String tag) {
        if (tag == null) {
            return false;
        }
        String value = tag.toLowerCase();
        return value.contains("模板") || value.contains("同源") || value.contains("套版") || value.contains("template");
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
