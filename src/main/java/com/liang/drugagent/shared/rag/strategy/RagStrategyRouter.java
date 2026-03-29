package com.liang.drugagent.shared.rag.strategy;

import com.liang.drugagent.scene.SceneEnum;
import com.liang.drugagent.shared.rag.model.RagQueryContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RagStrategyRouter {

    public List<String> route(RagQueryContext context) {
        SceneEnum scene = context == null ? null : context.getScene();
        Set<String> selected = new LinkedHashSet<>();

        if (scene == SceneEnum.TENDER_REVIEW) {
            selected.add(PlagiarismRagStrategy.CODE);
            selected.add(TemplateHomologyRagStrategy.CODE);
            appendByFocus(context, selected);
        } else if (scene == SceneEnum.CONTRACT_PRECHECK) {
            selected.add(TemplateHomologyRagStrategy.CODE);
            appendByFocus(context, selected);
        } else if (scene == SceneEnum.RISK_ALERT) {
            selected.add(PlagiarismRagStrategy.CODE);
            appendByFocus(context, selected);
        }

        if (selected.isEmpty()) {
            selected.add(PlagiarismRagStrategy.CODE);
        }
        return new ArrayList<>(selected);
    }

    private void appendByFocus(RagQueryContext context, Set<String> selected) {
        String focus = context == null ? null : context.getReviewFocus();
        if (focus == null || focus.isBlank()) {
            return;
        }
        String text = focus.toLowerCase(Locale.ROOT);
        if (containsAny(text, "抄袭", "雷同", "相似", "copy", "plagiarism")) {
            selected.add(PlagiarismRagStrategy.CODE);
        }
        if (containsAny(text, "模板", "同源", "套版", "范本", "template")) {
            selected.add(TemplateHomologyRagStrategy.CODE);
        }
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
