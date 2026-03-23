package com.liang.drugagent.agent.routing;

import com.liang.drugagent.config.RoutingProperties;
import com.liang.drugagent.domain.req.DrugAgentReq;
import com.liang.drugagent.domain.routing.WorkflowRouteDecision;
import com.liang.drugagent.enums.SceneEnum;
import com.liang.drugagent.service.QwenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * WorkflowRouteClassifier 单元测试。
 *
 * <p>测试百炼分类器的各种场景：</p>
 * <ul>
 *   <li>正常分类</li>
 *   <li>超时处理</li>
 *   <li>异常处理</li>
 *   <li>空响应处理</li>
 *   <li>解析失败处理</li>
 * </ul>
 *
 * @author liangjiajian
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowRouteClassifier 测试")
class WorkflowRouteClassifierTest {

    @Mock
    private QwenService qwenService;

    private RoutePromptBuilder promptBuilder;
    private RouteDecisionParser parser;
    private RouteDecisionValidator validator;
    private RoutingProperties routingProperties;
    private WorkflowRouteClassifier classifier;

    @BeforeEach
    void setUp() {
        promptBuilder = new RoutePromptBuilder();
        parser = new RouteDecisionParser();
        validator = new RouteDecisionValidator();
        routingProperties = new RoutingProperties();
        routingProperties.setLlmTimeout(5000);

        classifier = new WorkflowRouteClassifier(
                qwenService,
                promptBuilder,
                parser,
                validator,
                routingProperties
        );
    }

    @Nested
    @DisplayName("正常分类场景")
    class NormalClassificationTests {

        @Test
        @DisplayName("应正确解析 TENDER_REVIEW 场景")
        void shouldClassifyTenderReview() throws RouteClassificationException {
            // 模拟 QwenService 返回有效 JSON
            String mockResponse = """
                {
                    "scene": "TENDER_REVIEW",
                    "confidence": 0.92,
                    "reason": "用户提到标书查重和文件相似度分析"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("帮我查一下这两份标书有没有雷同")
                    .fileIds(List.of("f1.pdf", "f2.pdf"))
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNotNull(decision);
            assertEquals(SceneEnum.TENDER_REVIEW, decision.getScene());
            assertEquals(0.92, decision.getConfidence());
            verify(qwenService, times(1)).chat(anyString());
        }

        @Test
        @DisplayName("应正确解析 CONTRACT_PRECHECK 场景")
        void shouldClassifyContractPrecheck() throws RouteClassificationException {
            String mockResponse = """
                {
                    "scene": "CONTRACT_PRECHECK",
                    "confidence": 0.85,
                    "reason": "用户提到合同条款和法务审核"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("审查这份供应合同有没有风险")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNotNull(decision);
            assertEquals(SceneEnum.CONTRACT_PRECHECK, decision.getScene());
        }

        @Test
        @DisplayName("应正确解析 RISK_ALERT 场景")
        void shouldClassifyRiskAlert() throws RouteClassificationException {
            String mockResponse = """
                {
                    "scene": "RISK_ALERT",
                    "confidence": 0.88,
                    "reason": "用户提到药品异常和预警"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("最近药品使用有什么异常")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNotNull(decision);
            assertEquals(SceneEnum.RISK_ALERT, decision.getScene());
        }
    }

    @Nested
    @DisplayName("异常处理场景")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("QwenService 异常时应抛出 RouteClassificationException")
        void shouldThrowExceptionOnServiceError() {
            when(qwenService.chat(anyString())).thenThrow(new RuntimeException("Network error"));

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            assertThrows(RouteClassificationException.class, () ->
                    classifier.classify(req, List.of()));
        }

        @Test
        @DisplayName("空响应应返回 null")
        void shouldReturnNullOnEmptyResponse() throws RouteClassificationException {
            when(qwenService.chat(anyString())).thenReturn("");

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }

        @Test
        @DisplayName("空白响应应返回 null")
        void shouldReturnNullOnBlankResponse() throws RouteClassificationException {
            when(qwenService.chat(anyString())).thenReturn("   ");

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }
    }

    @Nested
    @DisplayName("解析失败场景")
    class ParseFailureTests {

        @Test
        @DisplayName("无效 JSON 应返回 null")
        void shouldReturnNullOnInvalidJson() throws RouteClassificationException {
            when(qwenService.chat(anyString())).thenReturn("not a json");

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }

        @Test
        @DisplayName("无效场景值应返回 null")
        void shouldReturnNullOnInvalidScene() throws RouteClassificationException {
            String mockResponse = """
                {
                    "scene": "INVALID_SCENE",
                    "confidence": 0.9,
                    "reason": "测试"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }

        @Test
        @DisplayName("置信度超出范围应返回 null")
        void shouldReturnNullOnInvalidConfidence() throws RouteClassificationException {
            String mockResponse = """
                {
                    "scene": "TENDER_REVIEW",
                    "confidence": 1.5,
                    "reason": "测试"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }

        @Test
        @DisplayName("缺失必需字段应返回 null")
        void shouldReturnNullOnMissingFields() throws RouteClassificationException {
            String mockResponse = """
                {
                    "scene": "TENDER_REVIEW"
                }
                """;
            when(qwenService.chat(anyString())).thenReturn(mockResponse);

            DrugAgentReq req = DrugAgentReq.builder()
                    .query("测试")
                    .build();

            WorkflowRouteDecision decision = classifier.classify(req, List.of());

            assertNull(decision);
        }
    }

    @Nested
    @DisplayName("置信度判断测试")
    class ConfidenceThresholdTests {

        @Test
        @DisplayName("shouldAccept 应正确判断高置信度")
        void shouldAcceptHighConfidence() {
            WorkflowRouteDecision decision = WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.9)
                    .build();

            assertTrue(classifier.shouldAccept(decision, null));
        }

        @Test
        @DisplayName("shouldAccept 应正确判断低置信度")
        void shouldRejectLowConfidence() {
            WorkflowRouteDecision decision = WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.4)
                    .build();

            assertFalse(classifier.shouldAccept(decision, null));
        }

        @Test
        @DisplayName("shouldAccept 在中间区间应参考规则决策")
        void shouldConsiderRuleDecisionInMiddleRange() {
            WorkflowRouteDecision llmDecision = WorkflowRouteDecision.builder()
                    .scene(SceneEnum.TENDER_REVIEW)
                    .confidence(0.6)
                    .build();

            WorkflowRouteDecision ruleDecision = WorkflowRouteDecision.builder()
                    .scene(SceneEnum.CONTRACT_PRECHECK)
                    .confidence(0.95)
                    .build();

            // 规则高置信时应拒绝 LLM
            assertFalse(classifier.shouldAccept(llmDecision, ruleDecision));
        }

        @Test
        @DisplayName("shouldAccept 传入 null 应返回 false")
        void shouldReturnFalseForNullDecision() {
            assertFalse(classifier.shouldAccept(null, null));
        }
    }
}
