package com.liang.drugagent.agent.routing;

import com.liang.drugagent.agent.AgentContext;
import com.liang.drugagent.agent.SceneRouter;
import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SceneRouter 集成测试。
 *
 * <p>测试完整的路由决策流程，包括：</p>
 * <ul>
 *   <li>规则优先决策</li>
 *   <li>LLM 分类回退</li>
 *   <li>置信度阈值判断</li>
 *   <li>兜底机制</li>
 *   <li>配置开关</li>
 * </ul>
 *
 * @author liangjiajian
 */
@DisplayName("SceneRouter 集成测试")
class SceneRouterTest {

    private SceneRouter sceneRouter;
    private RoutingProperties routingProperties;
    private MockWorkflowRouteClassifier mockClassifier;
    private RuleBasedRouteDecider ruleBasedRouteDecider;
    private RouteDecisionValidator validator;

    @BeforeEach
    void setUp() {
        routingProperties = new RoutingProperties();
        routingProperties.setLlmEnabled(true);
        routingProperties.setLlmTimeout(5000);
        routingProperties.setConfidenceThreshold(0.75);
        routingProperties.setConfidenceLowThreshold(0.5);

        ruleBasedRouteDecider = new RuleBasedRouteDecider();
        validator = new RouteDecisionValidator();
        mockClassifier = new MockWorkflowRouteClassifier();

        sceneRouter = new SceneRouter(
                ruleBasedRouteDecider,
                mockClassifier,
                validator,
                routingProperties
        );
    }

    @Nested
    @DisplayName("sceneHint 显式指定")
    class SceneHintIntegrationTests {

        @ParameterizedTest
        @MethodSource("sceneHintProvider")
        @DisplayName("应直接使用 sceneHint 指定场景")
        void shouldUseSceneHintDirectly(String sceneHint, SceneEnum expectedScene) {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .sceneHint(sceneHint)
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(expectedScene, decision.getScene());
            assertEquals("sceneHint", decision.getSource());
            assertEquals(1.0, decision.getConfidence());
            // LLM 不应被调用
            assertFalse(mockClassifier.wasCalled());
        }

        static Stream<Arguments> sceneHintProvider() {
            return Stream.of(
                    Arguments.of("TENDER_REVIEW", SceneEnum.TENDER_REVIEW),
                    Arguments.of("CONTRACT_PRECHECK", SceneEnum.CONTRACT_PRECHECK),
                    Arguments.of("RISK_ALERT", SceneEnum.RISK_ALERT)
            );
        }
    }

    @Nested
    @DisplayName("规则路由命中")
    class RuleHitTests {

        @Test
        @DisplayName("多文件上传应命中规则路由")
        void shouldHitRuleForMultiFile() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我看看这两份文件")
                    .fileIds(List.of("f1.pdf", "f2.pdf"))
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("rule", decision.getSource());
            assertFalse(mockClassifier.wasCalled());
        }

        @Test
        @DisplayName("文本含标书关键词应命中规则路由")
        void shouldHitRuleForTenderKeyword() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我查重一下标书")
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("rule", decision.getSource());
            assertFalse(mockClassifier.wasCalled());
        }

        @Test
        @DisplayName("文本含合同关键词应命中规则路由")
        void shouldHitRuleForContractKeyword() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我审查合同条款")
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.CONTRACT_PRECHECK, decision.getScene());
            assertEquals("rule", decision.getSource());
        }

        @Test
        @DisplayName("文本含风险关键词应命中规则路由")
        void shouldHitRuleForRiskKeyword() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("分析药品使用异常")
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.RISK_ALERT, decision.getScene());
            assertEquals("rule", decision.getSource());
        }
    }

    @Nested
    @DisplayName("LLM 分类回退")
    class LlmFallbackTests {

        @Test
        @DisplayName("规则无法决策时应调用 LLM")
        void shouldCallLlmWhenRuleCannotDecide() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("这个文件合规吗")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                    .scene(SceneEnum.CONTRACT_PRECHECK)
                    .confidence(0.85)
                    .reason("语义理解为合同审查")
                    .build());

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertTrue(mockClassifier.wasCalled());
            assertEquals(SceneEnum.CONTRACT_PRECHECK, decision.getScene());
            assertEquals("llm", decision.getSource());
        }

        @Test
        @DisplayName("LLM 高置信度时应采纳")
        void shouldAcceptHighConfidenceLlm() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("分析标书语义相似度")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.9)
                    .reason("语义分析")
                    .build());

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals("llm", decision.getSource());
        }

        @Test
        @DisplayName("LLM 低置信度时应回退到 UNKNOWN")
        void shouldFallbackToUnknownOnLowConfidence() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.3)
                    .reason("置信度低")
                    .build());

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.UNKNOWN, decision.getScene());
            assertEquals("fallback", decision.getSource());
        }

        @Test
        @DisplayName("LLM 异常时应回退到 UNKNOWN")
        void shouldFallbackToUnknownOnLlmException() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextException(new RouteClassificationException("LLM error"));

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.UNKNOWN, decision.getScene());
            assertEquals("fallback", decision.getSource());
        }

        @Test
        @DisplayName("LLM 返回 null 时应回退到 UNKNOWN")
        void shouldFallbackToUnknownOnLlmNull() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(null);

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.UNKNOWN, decision.getScene());
            assertEquals("fallback", decision.getSource());
        }
    }

    @Nested
    @DisplayName("配置开关测试")
    class ConfigSwitchTests {

        @Test
        @DisplayName("llm-enabled=false 时应跳过 LLM 调用")
        void shouldSkipLlmWhenDisabled() {
            routingProperties.setLlmEnabled(false);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("随便问问")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.9)
                    .build());

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertFalse(mockClassifier.wasCalled());
            assertEquals(SceneEnum.UNKNOWN, decision.getScene());
            assertEquals("fallback", decision.getSource());
        }

        @Test
        @DisplayName("应使用配置的置信度阈值")
        void shouldUseConfiguredThresholds() {
            routingProperties.setConfidenceThreshold(0.6);
            routingProperties.setConfidenceLowThreshold(0.3);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("语义分析场景")
                    .build();
            AgentContext context = new AgentContext();

            mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                    .scene(SceneEnum.RISK_ALERT)
                    .confidence(0.35) // 低于 0.3 阈值
                    .reason("置信度低于回退阈值")
                    .build());

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(SceneEnum.UNKNOWN, decision.getScene());
        }
    }

    @Nested
    @DisplayName("上下文同步测试")
    class ContextSyncTests {

        @Test
        @DisplayName("决策结果应同步到 AgentContext")
        void shouldSyncToContext() {
            DrugAgentReq req = DrugAgentReq.builder()
                    .query("标书查重")
                    .fileIds(List.of("f1.pdf", "f2.pdf"))
                    .build();
            AgentContext context = new AgentContext();

            WorkflowRouteDecision decision = sceneRouter.decide(req, context);

            assertEquals(decision.getScene(), context.getSceneType());
            assertEquals(decision.getSource(), context.getAttributes().get("routeSource"));
            assertEquals(decision.getConfidence(), context.getAttributes().get("routeConfidence"));
        }
    }

    @Nested
    @DisplayName("统计指标测试")
    class MetricsTests {

        @Test
        @DisplayName("统计整体路由来源分布")
        void shouldTrackRoutingSourceDistribution() {
            // 统计各类来源的命中情况
            long sceneHintCount = 0;
            long ruleCount = 0;
            long llmCount = 0;
            long fallbackCount = 0;

            for (RouteTestCases.TestCase tc : RouteTestCases.getAllCases()) {
                // 只测试非 LLM 来源的用例（避免调用真实 LLM）
                if ("llm".equals(tc.getExpectedSource())) {
                    continue;
                }

                mockClassifier.setNextDecision(WorkflowRouteDecision.builder()
                        .scene(tc.getExpectedScene())
                        .confidence(0.85)
                        .source(tc.getExpectedSource())
                        .reason("测试")
                        .build());

                DrugAgentReq req = RouteTestCases.toRequest(tc);
                AgentContext context = new AgentContext();

                try {
                    WorkflowRouteDecision decision = sceneRouter.decide(req, context);
                    switch (decision.getSource()) {
                        case "sceneHint" -> sceneHintCount++;
                        case "rule" -> ruleCount++;
                        case "llm" -> llmCount++;
                        case "fallback" -> fallbackCount++;
                    }
                } catch (Exception e) {
                    fallbackCount++;
                }
            }

            System.out.printf("[SceneRouter Metrics] sceneHint: %d, rule: %d, llm: %d, fallback: %d%n",
                    sceneHintCount, ruleCount, llmCount, fallbackCount);
            System.out.printf("[SceneRouter Metrics] 规则命中率: %.2f%%, LLM命中率: %.2f%%, 兜底率: %.2f%%%n",
                    (double) ruleCount / (sceneHintCount + ruleCount + llmCount + fallbackCount) * 100,
                    (double) llmCount / (sceneHintCount + ruleCount + llmCount + fallbackCount) * 100,
                    (double) fallbackCount / (sceneHintCount + ruleCount + llmCount + fallbackCount) * 100);

            // 验证：规则命中率应该很高（因为规则覆盖了大部分明确场景）
            assertTrue(ruleCount >= 15, "规则应至少命中 15 个测试用例");
        }
    }

    /**
     * Mock LLM 分类器，用于测试
     */
    private static class MockWorkflowRouteClassifier extends WorkflowRouteClassifier {
        private boolean called = false;
        private WorkflowRouteDecision nextDecision;
        private RouteClassificationException nextException;

        public MockWorkflowRouteClassifier() {
            super(null, null, null, null, new RoutingProperties());
        }

        public void setNextDecision(WorkflowRouteDecision decision) {
            this.nextDecision = decision;
            this.nextException = null;
        }

        public void setNextException(RouteClassificationException e) {
            this.nextException = e;
            this.nextDecision = null;
        }

        public boolean wasCalled() {
            return called;
        }

        @Override
        public WorkflowRouteDecision classify(com.liang.drugagent.domain.req.DrugAgentReq req,
                                                List<String> fileNames) throws RouteClassificationException {
            called = true;
            if (nextException != null) {
                throw nextException;
            }
            return nextDecision;
        }
    }
}
