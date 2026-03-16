package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.workflow.SceneType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MVP 场景路由器。
 *
 * <p>当前采用“显式提示优先 + 规则路由兜底”的轻量实现，
 * 目标是先保证统一入口能稳定落到正确工作流，而不是一开始就引入复杂分类模型。</p>
 * @author liangjiajian
 */
@Component
public class SceneRouter {

    public SceneType route(DrugAgentReq req, AgentContext context) {
        // 1. 前端或上游系统显式指定场景时，优先尊重调用方意图。
        SceneType hinted = SceneType.fromHint(req.getSceneHint());
        if (hinted != null) {
            context.getAttributes().put("routeReason", "sceneHint");
            return hinted;
        }

        List<String> fileIds = req.getFileIds();
        // 2. 带附件时，默认优先进入合规审查链路。
        if (fileIds != null && !fileIds.isEmpty()) {
            context.getAttributes().put("routeReason", "fileIds");
            return SceneType.COMPLIANCE_REVIEW;
        }

        String query = req.getQuery() == null ? "" : req.getQuery().toLowerCase();
        // 3. 规则路由先保证可解释，后续如果需要再追加 LLM 分类兜底。
        if (containsAny(query, "合规", "法规", "条款", "依据", "符合")) {
            context.getAttributes().put("routeReason", "regulationKeywords");
            return SceneType.GENERAL_QA;
        }
        if (containsAny(query, "审查", "审核", "材料", "文件")) {
            context.getAttributes().put("routeReason", "reviewKeywords");
            return SceneType.COMPLIANCE_REVIEW;
        }
        if (containsAny(query, "用量", "趋势", "异常", "统计", "分析")) {
            context.getAttributes().put("routeReason", "analysisKeywords");
            return SceneType.DRUG_ANALYSIS;
        }

        context.getAttributes().put("routeReason", "fallback");
        return SceneType.UNKNOWN;
    }

    private boolean containsAny(String query, String... words) {
        for (String word : words) {
            if (query.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
