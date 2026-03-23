package com.liang.drugagent.agent.routing;

import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleBasedRouteDecider 单元测试。
 *
 * <p>测试规则路由的各个场景，包括：</p>
 * <ul>
 *   <li>sceneHint 显式指定</li>
 *   <li>多文件上传 -> TENDER_REVIEW</li>
 *   <li>单文件标书 -> TENDER_REVIEW</li>
 *   <li>单文件合同 -> CONTRACT_PRECHECK</li>
 *   <li>文本关键词匹配</li>
 *   <li>无法决策时返回 null</li>
 * </ul>
 *
 * @author liangjiajian
 */
@DisplayName("RuleBasedRouteDecider 测试")
class RuleBasedRouteDeciderTest {

    private RuleBasedRouteDecider decider;

    @BeforeEach
    void setUp() {
        decider = new RuleBasedRouteDecider();
    }

    @Nested
    @DisplayName("sceneHint 显式指定场景")
    class SceneHintTests {

        @ParameterizedTest
        @MethodSource("sceneHintTestCases")
        @DisplayName("应优先采纳 sceneHint 指定场景")
        void shouldUseSceneHintWhenProvided(String sceneHint, SceneEnum expectedScene) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .sceneHint(sceneHint)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(expectedScene, decision.getScene());
            assertEquals("sceneHint", decision.getSource());
            assertEquals(1.0, decision.getConfidence());
        }

        static Stream<Arguments> sceneHintTestCases() {
            return Stream.of(
                    Arguments.of("TENDER_REVIEW", SceneEnum.TENDER_REVIEW),
                    Arguments.of("CONTRACT_PRECHECK", SceneEnum.CONTRACT_PRECHECK),
                    Arguments.of("RISK_ALERT", SceneEnum.RISK_ALERT),
                    Arguments.of("tender_review", SceneEnum.TENDER_REVIEW), // 大小写不敏感
                    Arguments.of("contract_precheck", SceneEnum.CONTRACT_PRECHECK)
            );
        }
    }

    @Nested
    @DisplayName("多文件上传场景")
    class MultiFileTests {

        @Test
        @DisplayName("文件数 >= 2 应返回 TENDER_REVIEW")
        void shouldReturnTenderReviewWhenMultipleFiles() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我看看这些文件")
                    .fileIds(List.of("file1.pdf", "file2.pdf"))
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("rule", decision.getSource());
            assertEquals(0.9, decision.getConfidence());
        }

        @Test
        @DisplayName("三个文件也应返回 TENDER_REVIEW")
        void shouldReturnTenderReviewWhenThreeFiles() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("比较这三个文件")
                    .fileIds(List.of("f1.pdf", "f2.pdf", "f3.pdf"))
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
        }
    }

    @Nested
    @DisplayName("单文件上传场景")
    class SingleFileTests {

        @Test
        @DisplayName("单文件且文件名含标书关键词应返回 TENDER_REVIEW")
        void shouldReturnTenderReviewForTenderFile() {
            Map<String, Object> metadata = Map.of("uploadedFiles",
                    List.of(Map.of("filename", "投标标书.pdf")));

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("审查一下")
                    .fileIds(List.of("file1.pdf"))
                    .metadata(metadata)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("rule", decision.getSource());
        }

        @Test
        @DisplayName("单文件且文件名含招标关键词应返回 TENDER_REVIEW")
        void shouldReturnTenderReviewForBidFile() {
            Map<String, Object> metadata = Map.of("uploadedFiles",
                    List.of(Map.of("filename", "招标公告.doc")));

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我看")
                    .fileIds(List.of("file1.doc"))
                    .metadata(metadata)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
        }

        @Test
        @DisplayName("单文件且无特殊关键词应返回 CONTRACT_PRECHECK")
        void shouldReturnContractPrecheckForGenericFile() {
            Map<String, Object> metadata = Map.of("uploadedFiles",
                    List.of(Map.of("filename", "document.pdf")));

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("审查这份文件")
                    .fileIds(List.of("file1.pdf"))
                    .metadata(metadata)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.CONTRACT_PRECHECK, decision.getScene());
            assertEquals("rule", decision.getSource());
        }
    }

    @Nested
    @DisplayName("文本关键词匹配场景")
    class KeywordMatchingTests {

        @ParameterizedTest
        @MethodSource("tenderKeywords")
        @DisplayName("含标书关键词应返回 TENDER_REVIEW")
        void shouldReturnTenderReviewForKeyword(String keyword) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我" + keyword)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("rule", decision.getSource());
        }

        static Stream<String> tenderKeywords() {
            return Stream.of("标书", "投标", "串标", "围标", "雷同", "查重", "相似");
        }

        @ParameterizedTest
        @MethodSource("contractKeywords")
        @DisplayName("含合同关键词应返回 CONTRACT_PRECHECK")
        void shouldReturnContractPrecheckForKeyword(String keyword) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我" + keyword)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.CONTRACT_PRECHECK, decision.getScene());
            assertEquals("rule", decision.getSource());
        }

        static Stream<String> contractKeywords() {
            return Stream.of("合同", "协议", "条款", "法务", "审核", "预审", "审查");
        }

        @ParameterizedTest
        @MethodSource("riskKeywords")
        @DisplayName("含风险关键词应返回 RISK_ALERT")
        void shouldReturnRiskAlertForKeyword(String keyword) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("分析" + keyword)
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNotNull(decision);
            assertEquals(SceneEnum.RISK_ALERT, decision.getScene());
            assertEquals("rule", decision.getSource());
        }

        static Stream<String> riskKeywords() {
            return Stream.of("药品", "耗材", "预警", "异常", "用量", "趋势", "统计", "分析");
        }
    }

    @Nested
    @DisplayName("无法决策场景")
    class NoDecisionTests {

        @Test
        @DisplayName("无特征查询应返回 null")
        void shouldReturnNullForGenericQuery() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我看看")
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNull(decision);
        }

        @Test
        @DisplayName("空白查询应返回 null")
        void shouldReturnNullForBlankQuery() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("")
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNull(decision);
        }

        @Test
        @DisplayName("闲聊应返回 null")
        void shouldReturnNullForChattyQuery() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("今天天气不错")
                    .build();

            WorkflowRouteDecision decision = decider.decide(req, null);

            assertNull(decision);
        }
    }

    @Nested
    @DisplayName("统计指标")
    class MetricsTests {

        @Test
        @DisplayName("统计规则命中率")
        void shouldTrackRuleHitRate() {
            List<WorkflowRouteDecision> hits = RouteTestCases.getAllCases().stream()
                    .filter(tc -> "rule".equals(tc.getExpectedSource()) || "sceneHint".equals(tc.getExpectedSource()))
                    .map(tc -> decider.decide(RouteTestCases.toRequest(tc), SceneEnum.fromHint(tc.getSceneHint())))
                    .filter(d -> d != null)
                    .toList();

            long totalRuleCases = RouteTestCases.getAllCases().stream()
                    .filter(tc -> "rule".equals(tc.getExpectedSource()) || "sceneHint".equals(tc.getExpectedSource()))
                    .count();

            System.out.printf("[RuleBasedRouteDecider] 规则命中数: %d / %d (%.2f%%)%n",
                    hits.size(), totalRuleCases,
                    (double) hits.size() / totalRuleCases * 100);

            // 断言：规则应该能处理所有标记为 rule 的样例
            assertTrue(hits.size() >= totalRuleCases * 0.9,
                    "规则命中率应 >= 90%");
        }
    }
}
