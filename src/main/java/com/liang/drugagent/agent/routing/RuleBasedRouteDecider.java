package com.liang.drugagent.agent.routing;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于规则的路由决策器。
 *
 * <p>从 SceneRouter 中抽离出来的规则判断逻辑，
 * 负责对有明确特征（文件数、文件名关键词等）的请求进行高置信度路由。</p>
 *
 * @author liangjiajian
 */
@Component
public class RuleBasedRouteDecider {

    /**
     * 基于规则进行路由决策。
     *
     * @param req      请求对象
     * @param sceneHintHint 显式指定的场景提示（可为 null）
     * @return 路由决策结果；如果规则无法决策则返回 null
     */
    public WorkflowRouteDecision decide(DrugAgentReq req, SceneEnum sceneHintHint) {
        // 1. 前端或上游系统显式指定场景时，优先尊重调用方意图。
        if (sceneHintHint != null) {
            return WorkflowRouteDecision.builder()
                    .scene(sceneHintHint)
                    .source("sceneHint")
                    .reason("显式指定的场景")
                    .confidence(1.0)
                    .requiresClarification(false)
                    .build();
        }

        List<String> fileIds = req.getFileIds();

        // 2. 带附件时，根据文件数量和元信息优先识别文档类任务。
        if (fileIds != null && !fileIds.isEmpty()) {
            // 文件数 >= 2 时，高置信判定为标书查重场景
            if (fileIds.size() >= 2) {
                return WorkflowRouteDecision.builder()
                        .scene(SceneEnum.TENDER_REVIEW)
                        .source("rule")
                        .reason("多文件上传（文件数>=2），高置信指向标书查重")
                        .confidence(0.9)
                        .requiresClarification(false)
                        .build();
            }

            // 单文件时，根据文件名特征判断
            if (isTenderFileRequest(req)) {
                return WorkflowRouteDecision.builder()
                        .scene(SceneEnum.TENDER_REVIEW)
                        .source("rule")
                        .reason("文件名含标书相关关键词")
                        .confidence(0.9)
                        .requiresClarification(false)
                        .build();
            }

            // 单文件合同类文件
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.CONTRACT_PRECHECK)
                    .source("rule")
                    .reason("单文件上传，判定为合同文件")
                    .confidence(0.9)
                    .requiresClarification(false)
                    .build();
        }

        // 3. 纯文本查询，根据关键词规则判断
        String query = req.getQuery() == null ? "" : req.getQuery().toLowerCase();

        if (containsAny(query, "标书", "投标", "串标", "围标", "雷同", "查重", "相似")) {
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .source("rule")
                    .reason("查询文本含标书相关关键词")
                    .confidence(0.9)
                    .requiresClarification(false)
                    .build();
        }

        if (containsAny(query, "合同", "协议", "条款", "法务", "审核", "预审", "审查")) {
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.CONTRACT_PRECHECK)
                    .source("rule")
                    .reason("查询文本含合同相关关键词")
                    .confidence(0.9)
                    .requiresClarification(false)
                    .build();
        }

        if (containsAny(query, "药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析")) {
            return WorkflowRouteDecision.builder()
                    .scene(SceneEnum.RISK_ALERT)
                    .source("rule")
                    .reason("查询文本含风险预警相关关键词")
                    .confidence(0.9)
                    .requiresClarification(false)
                    .build();
        }

        // 规则无法决策时返回 null，交给调用方兜底
        return null;
    }

    private boolean containsAny(String query, String... words) {
        for (String word : words) {
            if (query.contains(word)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean isTenderFileRequest(DrugAgentReq req) {
        Map<String, Object> metadata = req.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        Object rawFiles = metadata.get("uploadedFiles");
        if (!(rawFiles instanceof List<?> files)) {
            return false;
        }
        for (Object item : files) {
            if (!(item instanceof Map<?, ?> fileMap)) {
                continue;
            }
            Object name = fileMap.get("filename");
            if (name != null) {
                String lowerName = name.toString().toLowerCase(Locale.ROOT);
                if (containsAny(lowerName, "标书", "投标", "招标", "围标", "串标")) {
                    return true;
                }
            }
        }
        return false;
    }
}
