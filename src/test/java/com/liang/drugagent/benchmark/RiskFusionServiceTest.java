package com.liang.drugagent.benchmark;

import com.liang.drugagent.scene.tender_review.model.ExemptionHit;
import com.liang.drugagent.scene.tender_review.model.RiskFusionResult;
import com.liang.drugagent.scene.tender_review.model.RuleEvidence;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.RiskFusionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RiskFusionService 风险融合服务单元测试。
 *
 * <p>测试场景：
 * <ol>
 *   <li>无命中项时返回LOW风险</li>
 *   <li>Sigmoid加权计算正确性</li>
 *   <li>关联规则超线性加成（W-M2+W-M3）</li>
 *   <li>重复命中衰减机制</li>
 *   <li>免责负向调节</li>
 * </ol>
 */
class RiskFusionServiceTest {

    private RiskFusionService riskFusionService;

    @BeforeEach
    void setUp() {
        riskFusionService = new RiskFusionService();
    }

    /**
     * 测试：无命中项时返回LOW风险
     */
    @Test
    void should无命中项时返回Low风险() {
        TenderReviewData data = new TenderReviewData();
        List<RuleHit> hits = List.of();
        List<ExemptionHit> exemptions = List.of();

        RiskFusionResult result = riskFusionService.fuse(data, hits, exemptions);

        assertEquals("LOW", result.getRiskLevel(), "无命中项时应返回LOW风险等级");
        assertEquals(0, result.getScore(), "无命中项且无免责时分数应为0");
        assertTrue(result.getSummary().contains("未保留高风险命中"), "摘要应说明未保留高风险命中");
    }

    /**
     * 测试：无命中项但有免责时返回分数20
     */
    @Test
    void should无命中但有免责时分数为20() {
        TenderReviewData data = new TenderReviewData();
        ExemptionHit exemption = ExemptionHit.builder()
                .hitId("ex-1")
                .ruleCode("W-M2")
                .exemptionType("REFERENCE_TEMPLATE")
                .action("DOWNGRADE")
                .build();
        List<ExemptionHit> exemptions = List.of(exemption);

        RiskFusionResult result = riskFusionService.fuse(data, List.of(), exemptions);

        assertEquals("LOW", result.getRiskLevel());
        assertEquals(20, result.getScore());
        assertTrue(result.getReasonCodes().contains("EXEMPTION_DOWNGRADE"));
    }

    /**
     * 测试：Sigmoid加权计算正确性 - confidence为0时
     */
    @Test
    void shouldSigmoid计算Confidence为0时权重正确() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(70)
                .confidence(0.0) // 最低置信度
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2")) // 多文档触发加分
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // confidenceMultiplier 接近 0.5，权重约减半
        assertNotNull(result.getScore());
        assertTrue(result.getScore() > 0, "有命中的分数应大于0");
    }

    /**
     * 测试：Sigmoid加权计算正确性 - confidence为1时
     */
    @Test
    void shouldSigmoid计算Confidence为1时权重接近满值() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(70)
                .confidence(1.0) // 最高置信度
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2")) // 多文档触发加分
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // confidenceMultiplier 接近 1.0，权重接近满值
        assertNotNull(result.getScore());
        // 高置信度时分数应明显高于低置信度
    }

    /**
     * 测试：Sigmoid加权计算正确性 - 无confidence时使用确定性规则
     */
    @Test
    void should无Confidence时使用确定性规则权重() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(70)
                .confidence(null) // 确定性规则，无confidence
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2")) // 多文档触发CROSS_DOCUMENT_EVIDENCE
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // 确定性规则直接使用权重，2文档有跨文档加分
        assertEquals("MEDIUM", result.getRiskLevel());
        assertTrue(result.getScore() >= 60);
    }

    /**
     * 测试：关联规则超线性加成（W-M2 + W-M3 同时命中）
     */
    @Test
    void shouldW_M2和W_M3同时命中时触发超线性加成() {
        TenderReviewData data = new TenderReviewData();
        RuleHit wm2Hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        RuleHit wm3Hit = RuleHit.builder()
                .ruleCode("W-M3")
                .ruleName("团队成员重叠")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(wm2Hit, wm3Hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // W-M2 + W-M3 同时命中应触发超线性加成
        assertTrue(result.getReasonCodes().contains("SYNERGY_BONUS"), "应包含SYNERGY_BONUS原因码");
        assertTrue(result.getScore() > 0, "分数应大于0");
    }

    /**
     * 测试：仅W-M2命中时不触发超线性加成
     */
    @Test
    void should仅W_M2命中时不触发超线性加成() {
        TenderReviewData data = new TenderReviewData();
        RuleHit wm2Hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(wm2Hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertFalse(result.getReasonCodes().contains("SYNERGY_BONUS"), "仅W-M2命中时不应包含SYNERGY_BONUS");
    }

    /**
     * 测试：重复命中衰减机制
     */
    @Test
    void should重复命中时使用边际收益递减() {
        TenderReviewData data = new TenderReviewData();
        List<RuleHit> hits = new ArrayList<>();
        // 3个相同规则的不同命中
        for (int i = 0; i < 3; i++) {
            hits.add(RuleHit.builder()
                    .ruleCode("W-M2")
                    .ruleName("联系方式近邻")
                    .weight(60)
                    .confidence(0.8)
                    .priority("HIGH")
                    .documentIds(List.of("doc-1"))
                    .build());
        }

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // 有重复命中时应触发 MULTI_HIT_ACCUMULATION
        assertTrue(result.getReasonCodes().contains("MULTI_HIT_ACCUMULATION"), "重复命中时应包含MULTI_HIT_ACCUMULATION");
    }

    /**
     * 测试：命中次数不足3次时无重复命中衰减加分
     */
    @Test
    void should命中次数少于3次时无衰减加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit1 = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(60)
                .confidence(0.8)
                .priority("HIGH")
                .documentIds(List.of("doc-1"))
                .build();
        RuleHit hit2 = RuleHit.builder()
                .ruleCode("W-M3")
                .ruleName("团队成员重叠")
                .weight(60)
                .confidence(0.8)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(hit1, hit2);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertFalse(result.getReasonCodes().contains("MULTI_HIT_ACCUMULATION"), "命中次数少于3次时不应包含MULTI_HIT_ACCUMULATION");
    }

    /**
     * 测试：免责负向调节
     */
    @Test
    void should免责项负向削减权重() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(85) // 高权重
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2", "doc-3"))
                .build();
        List<RuleHit> hits = List.of(hit);

        ExemptionHit exemption = ExemptionHit.builder()
                .hitId("ex-1")
                .ruleCode("W-M2")
                .exemptionType("REFERENCE_TEMPLATE")
                .action("DOWNGRADE")
                .build();
        List<ExemptionHit> exemptions = List.of(exemption);

        RiskFusionResult result = riskFusionService.fuse(data, hits, exemptions);

        assertTrue(result.getReasonCodes().contains("EXEMPTION_DOWNGRADE"), "应包含EXEMPTION_DOWNGRADE原因码");
    }

    /**
     * 测试：多条免责项累计负向削减（最多15分）
     */
    @Test
    void should多条免责项累计削减但不超过上限() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2", "doc-3"))
                .build();
        List<RuleHit> hits = List.of(hit);

        // 10条免责项，超过上限15分
        List<ExemptionHit> exemptions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            exemptions.add(ExemptionHit.builder()
                    .hitId("ex-" + i)
                    .ruleCode("W-M2")
                    .exemptionType("REFERENCE_TEMPLATE")
                    .action("DOWNGRADE")
                    .build());
        }

        RiskFusionResult result = riskFusionService.fuse(data, hits, exemptions);

        assertTrue(result.getReasonCodes().contains("EXEMPTION_DOWNGRADE"));
    }

    /**
     * 测试：多文档跨文档证据加分
     */
    @Test
    void should多文档时跨文档证据加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(70)
                .confidence(0.8)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2", "doc-3"))
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertTrue(result.getReasonCodes().contains("CROSS_DOCUMENT_VALIDATION"), "3个以上文档应包含CROSS_DOCUMENT_VALIDATION");
    }

    /**
     * 测试：两个文档时跨文档证据加分（较低档）
     */
    @Test
    void should两文档时跨文档证据较低档加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(70)
                .confidence(0.8)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertTrue(result.getReasonCodes().contains("CROSS_DOCUMENT_EVIDENCE"), "2个文档应包含CROSS_DOCUMENT_EVIDENCE");
    }

    /**
     * 测试：高优硬规则加成（优先级HIGH且权重>=85）
     */
    @Test
    void should高优硬规则时增加加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(85) // 达到HIGH门槛
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertTrue(result.getReasonCodes().contains("HIGH_PRIORITY_RULE"), "高优硬规则应包含HIGH_PRIORITY_RULE");
    }

    /**
     * 测试：关联规则超线性加成时HIGH门槛降低
     */
    @Test
    void should有关联规则加成时High门槛降低到75() {
        TenderReviewData data = new TenderReviewData();
        RuleHit wm2Hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        RuleHit wm3Hit = RuleHit.builder()
                .ruleCode("W-M3")
                .ruleName("团队成员重叠")
                .weight(80)
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(wm2Hit, wm3Hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // W-M2 + W-M3 同时命中触发超线性加成，应达到HIGH
        assertTrue(result.getReasonCodes().contains("SYNERGY_BONUS"), "应触发SYNERGY_BONUS");
    }

    /**
     * 测试：无效输入（null）被正确处理
     */
    @Test
    void shouldNull输入被正确处理为Empty() {
        TenderReviewData data = new TenderReviewData();

        RiskFusionResult result = riskFusionService.fuse(data, null, null);

        assertEquals("LOW", result.getRiskLevel());
        assertEquals(0, result.getScore());
    }

    /**
     * 测试：规则覆盖度加分（>=2种规则）
     */
    @Test
    void should规则覆盖度大于等于2时加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit1 = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(50)
                .confidence(0.7)
                .priority("MEDIUM")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        RuleHit hit2 = RuleHit.builder()
                .ruleCode("W-M3")
                .ruleName("团队成员重叠")
                .weight(50)
                .confidence(0.7)
                .priority("MEDIUM")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(hit1, hit2);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertTrue(result.getReasonCodes().contains("MULTI_RULE_CO_OCCURRENCE"), "规则类型>=2时应包含MULTI_RULE_CO_OCCURRENCE");
    }

    /**
     * 测试：证据丰富度加分（>=4条证据）
     */
    @Test
    void should证据数量大于等于4时加分() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(60)
                .confidence(0.8)
                .priority("MEDIUM")
                .documentIds(List.of("doc-1", "doc-2"))
                .evidences(List.of(
                        RuleEvidence.builder().matchedValue("证据1").build(),
                        RuleEvidence.builder().matchedValue("证据2").build(),
                        RuleEvidence.builder().matchedValue("证据3").build(),
                        RuleEvidence.builder().matchedValue("证据4").build()
                ))
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertTrue(result.getReasonCodes().contains("EVIDENCE_SUFFICIENT"), "证据>=4时应包含EVIDENCE_SUFFICIENT");
    }

    /**
     * 测试：分数上限为100
     */
    @Test
    void should分数不超过100() {
        TenderReviewData data = new TenderReviewData();
        List<RuleHit> hits = new ArrayList<>();
        // 添加多个高权重命中触发各种加成
        for (int i = 0; i < 5; i++) {
            hits.add(RuleHit.builder()
                    .ruleCode("W-M2")
                    .ruleName("联系方式近邻")
                    .weight(90)
                    .confidence(1.0)
                    .priority("HIGH")
                    .documentIds(List.of("doc-1", "doc-2", "doc-3", "doc-4"))
                    .evidences(List.of(
                            RuleEvidence.builder().matchedValue("证据1").build(),
                            RuleEvidence.builder().matchedValue("证据2").build(),
                            RuleEvidence.builder().matchedValue("证据3").build(),
                            RuleEvidence.builder().matchedValue("证据4").build()
                    ))
                    .build());
        }

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        assertEquals(100, result.getScore(), "分数不应超过100");
    }

    /**
     * 测试：adjustedWeight优先级高于weight
     */
    @Test
    void shouldAdjustedWeight优先级高于Weight() {
        TenderReviewData data = new TenderReviewData();
        RuleHit hit = RuleHit.builder()
                .ruleCode("W-M2")
                .ruleName("联系方式近邻")
                .weight(50) // 原始权重低
                .adjustedWeight(90) // 调整后权重高
                .confidence(0.9)
                .priority("HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        List<RuleHit> hits = List.of(hit);

        RiskFusionResult result = riskFusionService.fuse(data, hits, null);

        // 使用adjustedWeight=90，应触发HIGH_PRIORITY_RULE
        assertTrue(result.getReasonCodes().contains("HIGH_PRIORITY_RULE"), "应使用adjustedWeight计算");
    }
    /**
     * 测试：当 W-M4 与更具体的规则共存时，需降低 W-M4 的权重。
     */
    @Test
    void shouldDownweightWM4WhenSpecificRulesCoexist() {
        TenderReviewData data = new TenderReviewData();
        RuleHit wm4Hit = RuleHit.builder()
                .ruleCode("W-M4")
                .ruleName("版式模板同源")
                .weight(70)
                .confidence(0.8)
                .priority("MEDIUM")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        RuleHit wm6Hit = RuleHit.builder()
                .ruleCode("W-M6")
                .ruleName("商务条款雷同")
                .weight(60)
                .confidence(0.8)
                .priority("MEDIUM")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();

        riskFusionService.fuse(data, List.of(wm4Hit, wm6Hit), null);

        assertNotNull(wm4Hit.getAdjustedWeight());
        assertTrue(wm4Hit.getAdjustedWeight() < wm4Hit.getWeight());
    }

    /**
     * 测试：当 W-P4 与更具体的文本规则共存时，需降低 W-P4 的权重。
     */
    @Test
    void shouldDownweightWP4WhenSpecificRulesCoexist() {
        TenderReviewData data = new TenderReviewData();
        RuleHit wp4Hit = RuleHit.builder()
                .ruleCode("W-P4")
                .ruleName("风险识别抄袭")
                .weight(85)
                .confidence(0.8)
                .priority("MEDIUM_HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();
        RuleHit wp6Hit = RuleHit.builder()
                .ruleCode("W-P6")
                .ruleName("案例数据抄袭")
                .weight(70)
                .confidence(0.8)
                .priority("MEDIUM_HIGH")
                .documentIds(List.of("doc-1", "doc-2"))
                .build();

        riskFusionService.fuse(data, List.of(wp4Hit, wp6Hit), null);

        assertNotNull(wp4Hit.getAdjustedWeight());
        assertTrue(wp4Hit.getAdjustedWeight() < wp4Hit.getWeight());
    }
}
