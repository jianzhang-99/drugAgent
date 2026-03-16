package com.liang.drugagent.agent;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MVP 场景路由器。
 *
 * 判断这次请求属于哪个场景
 *
 * @author liangjiajian
 */
@Component
public class SceneRouter {

    public SceneEnum route(DrugAgentReq req, AgentContext context) {
        // 1. 前端或上游系统显式指定场景时，优先尊重调用方意图。
        SceneEnum hinted = SceneEnum.fromHint(req.getSceneHint());
        if (hinted != null) {
            context.getAttributes().put("routeReason", "sceneHint");
            return hinted;
        }

        List<String> fileIds = req.getFileIds();
        // 2. 带附件时，默认优先进入合同预审链路。
        if (fileIds != null && !fileIds.isEmpty()) {
            context.getAttributes().put("routeReason", "fileIds");
            return SceneEnum.CONTRACT_PRECHECK;
        }

        String query = req.getQuery() == null ? "" : req.getQuery().toLowerCase();
        // 3. 规则路由先保证可解释，后续如果需要再追加 LLM 分类兜底。
        if (containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似")) {
            context.getAttributes().put("routeReason", "tenderKeywords");
            return SceneEnum.TENDER_REVIEW;
        }
        if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
            context.getAttributes().put("routeReason", "contractKeywords");
            return SceneEnum.CONTRACT_PRECHECK;
        }
        if (containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析")) {
            context.getAttributes().put("routeReason", "riskAlertKeywords");
            return SceneEnum.RISK_ALERT;
        }

        context.getAttributes().put("routeReason", "fallback");
        return SceneEnum.UNKNOWN;
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
