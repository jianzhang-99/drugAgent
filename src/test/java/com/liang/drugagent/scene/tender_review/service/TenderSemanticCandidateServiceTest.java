package com.liang.drugagent.scene.tender_review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.scene.tender_review.model.Block;
import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.model.semantic.TenderSemanticJudgeReq;
import com.liang.drugagent.scene.tender_review.support.assembler.TenderReviewDataAssembler;
import com.liang.drugagent.tool.document.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenderSemanticCandidateService 语义候选召回测试。
 */
class TenderSemanticCandidateServiceTest {

    private static final Path SAMPLE_ROOT = Paths.get(
            "doc",
            "场景一 标书审查",
            "03_整理后样本数据",
            "2_高危样本对比组"
    );

    private final TenderSemanticCandidateService service = new TenderSemanticCandidateService();
    private final TenderReviewDataAssembler assembler = new TenderReviewDataAssembler(new ObjectMapper());

    @Test
    void shouldPreferStructuredFieldSnippetsForAllSemanticTopics() {
        List<Scenario> scenarios = List.of(
                new Scenario("技术方案", "W-P1", "proposal_segment", "risk_identification"),
                new Scenario("核心团队", "W-M3", "team_member", "proposal_segment"),
                new Scenario("实施方法", "W-P2", "implementation_method", "service_commitment"),
                new Scenario("服务承诺", "W-P3", "service_commitment", "risk_identification"),
                new Scenario("风险识别", "W-P4", "risk_identification", "team_member"),
                new Scenario("案例数据", "W-P6", "case_data", "proposal_segment"),
                new Scenario("商务条款", "W-M6", "text_segment", "risk_identification")
        );

        for (Scenario scenario : scenarios) {
            TenderReviewData data = buildSyntheticData(scenario.topic, scenario.correctFieldType, scenario.wrongFieldType);
            List<TenderSemanticJudgeReq> requests = service.recallCandidates(
                    data,
                    scenario.ruleCode,
                    scenario.topic,
                    "doc-a",
                    "doc-b"
            );

            assertFalse(requests.isEmpty(), "expected candidate request for topic: " + scenario.topic);
            TenderSemanticJudgeReq req = requests.get(0);
            assertNotNull(req.getLeftSnippets());
            assertNotNull(req.getRightSnippets());
            assertEquals(1, req.getLeftSnippets().size(), "left snippets should stay field-focused: " + scenario.topic);
            assertEquals(1, req.getRightSnippets().size(), "right snippets should stay field-focused: " + scenario.topic);
            assertTrue(req.getLeftSnippets().get(0).contains("[" + scenario.correctFieldType + "]"),
                    "left snippet should use expected field type: " + scenario.topic);
            assertTrue(req.getRightSnippets().get(0).contains("[" + scenario.correctFieldType + "]"),
                    "right snippet should use expected field type: " + scenario.topic);
            assertFalse(req.getLeftSnippets().get(0).contains("[" + scenario.wrongFieldType + "]"),
                    "left snippet should not leak wrong field type: " + scenario.topic);
            assertFalse(req.getRightSnippets().get(0).contains("[" + scenario.wrongFieldType + "]"),
                    "right snippet should not leak wrong field type: " + scenario.topic);
        }
    }

    @Test
    void shouldPreferStructuredFieldSnippetsOnRealSampleFiles() throws IOException {
        List<FileScenario> scenarios = List.of(
                new FileScenario("技术方案", "W-P1", "proposal_segment"),
                new FileScenario("核心团队", "W-M3", "team_member"),
                new FileScenario("实施方法", "W-P2", "implementation_method"),
                new FileScenario("服务承诺", "W-P3", "service_commitment"),
                new FileScenario("风险识别", "W-P4", "risk_identification"),
                new FileScenario("案例数据", "W-P6", "case_data"),
                new FileScenario("商务条款", "W-M6", "text_segment"),
                new FileScenario("商务条款", "W-M8", "text_segment")
        );

        for (FileScenario scenario : scenarios) {
            TenderReviewData data = loadFromRealSampleFiles(scenario.ruleCode, "trace-" + scenario.ruleCode);
            List<TenderSemanticJudgeReq> requests = service.recallCandidates(
                    data,
                    scenario.ruleCode,
                    scenario.topic,
                    "doc-a",
                    "doc-b"
            );

            assertFalse(requests.isEmpty(), "expected candidate request for real sample: " + scenario.ruleCode);
            TenderSemanticJudgeReq req = requests.get(0);
            boolean hasExpectedFieldType = data.getFields().stream()
                    .anyMatch(field -> scenario.expectedFieldType.equals(field.getFieldType()));

            if (hasExpectedFieldType) {
                assertTrue(req.getLeftSnippets().stream().anyMatch(snippet -> snippet.contains("[" + scenario.expectedFieldType + "]")),
                        "left snippets should use expected field type: " + scenario.ruleCode + ", actual=" + req.getLeftSnippets());
                assertTrue(req.getRightSnippets().stream().anyMatch(snippet -> snippet.contains("[" + scenario.expectedFieldType + "]")),
                        "right snippets should use expected field type: " + scenario.ruleCode + ", actual=" + req.getRightSnippets());
            } else {
                assertFalse(req.getLeftSnippets().isEmpty(), "left snippets should not be empty: " + scenario.ruleCode);
                assertFalse(req.getRightSnippets().isEmpty(), "right snippets should not be empty: " + scenario.ruleCode);
            }
        }
    }

    private TenderReviewData buildSyntheticData(String topic, String correctFieldType, String wrongFieldType) {
        TenderReviewData data = new TenderReviewData();
        data.setACase(TenderCase.builder()
                .caseId("case-" + topic)
                .scene("tender_review")
                .build());
        data.setDocuments(new ArrayList<>());

        Block leftBlock = Block.builder()
                .blockId("left-" + topic)
                .documentId("doc-a")
                .blockType("PARAGRAPH")
                .chapterPath(chapterPathFor(topic))
                .content(topic + " left content for similarity.")
                .build();
        Block rightBlock = Block.builder()
                .blockId("right-" + topic)
                .documentId("doc-b")
                .blockType("PARAGRAPH")
                .chapterPath(chapterPathFor(topic))
                .content(topic + " right content for similarity.")
                .build();

        data.setBlocks(List.of(leftBlock, rightBlock));

        List<Field> fields = new ArrayList<>();
        fields.add(buildField("f-left-" + topic, "doc-a", leftBlock.getBlockId(), topic,
                correctFieldType, expectedFieldName(correctFieldType), expectedFieldValue(correctFieldType)));
        fields.add(buildField("f-left-wrong-" + topic, "doc-a", leftBlock.getBlockId(), topic,
                wrongFieldType, "wrong", "wrong value"));
        fields.add(buildField("f-right-" + topic, "doc-b", rightBlock.getBlockId(), topic,
                correctFieldType, expectedFieldName(correctFieldType), expectedFieldValue(correctFieldType)));
        fields.add(buildField("f-right-wrong-" + topic, "doc-b", rightBlock.getBlockId(), topic,
                wrongFieldType, "wrong", "wrong value"));
        data.setFields(fields);
        return data;
    }

    private TenderReviewData loadFromRealSampleFiles(String ruleCode, String traceId) throws IOException {
        Path groupDir = findGroupDir(ruleCode);
        List<Path> files;
        try (Stream<Path> stream = Files.list(groupDir)) {
            files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertEquals(2, files.size(), "expected exactly two markdown files for " + ruleCode);

        ParsedDocument left = parseMarkdown(files.get(0), "doc-a");
        ParsedDocument right = parseMarkdown(files.get(1), "doc-b");
        TenderReviewData data = assembler.resolve(new ParsedDocument[]{left, right}, traceId);
        assertNotNull(data, "assembler should build tender review data for " + ruleCode);
        return data;
    }

    private Path findGroupDir(String ruleCode) throws IOException {
        try (Stream<Path> stream = Files.list(SAMPLE_ROOT)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(ruleCode + "_"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing sample group dir for " + ruleCode));
        }
    }

    private ParsedDocument parseMarkdown(Path path, String documentId) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return ParsedDocument.builder()
                .documentId(documentId)
                .filename(path.getFileName().toString())
                .fileType("md")
                .plainText(content)
                .normalizedText(content)
                .build();
    }

    private Field buildField(String fieldId,
                             String documentId,
                             String blockId,
                             String topic,
                             String fieldType,
                             String fieldName,
                             String fieldValue) {
        return Field.builder()
                .fieldId(fieldId)
                .documentId(documentId)
                .blockId(blockId)
                .fieldType(fieldType)
                .fieldName(fieldName)
                .fieldValue(fieldValue)
                .normalizedValue(fieldValue)
                .normalizedKey(fieldType + ":" + topic)
                .chapterPath(chapterPathFor(topic))
                .confidence(0.95)
                .build();
    }

    private String chapterPathFor(String topic) {
        return switch (topic) {
            case "技术方案" -> "三、技术方案";
            case "核心团队" -> "四、核心团队";
            case "实施方法" -> "五、实施方法";
            case "服务承诺" -> "六、服务承诺";
            case "风险识别" -> "七、风险识别";
            case "案例数据" -> "八、案例数据";
            case "商务条款" -> "九、商务条款";
            default -> topic;
        };
    }

    private String expectedFieldName(String fieldType) {
        return switch (fieldType) {
            case "proposal_segment" -> "技术方案";
            case "team_member" -> "核心成员";
            case "implementation_method" -> "实施阶段";
            case "service_commitment" -> "承诺项";
            case "risk_identification" -> "风险项";
            case "case_data" -> "案例数据";
            case "text_segment" -> "商务条款";
            default -> "字段";
        };
    }

    private String expectedFieldValue(String fieldType) {
        return switch (fieldType) {
            case "proposal_segment" -> "这里是技术方案正文";
            case "team_member" -> "张三 | 项目经理 | 10年经验";
            case "implementation_method" -> "需求分析 -> 设计 -> 开发 -> 测试";
            case "service_commitment" -> "2小时响应，24小时到场";
            case "risk_identification" -> "项目延期风险 | 提前预警";
            case "case_data" -> "8家供应商";
            case "text_segment" -> "商务条款正文";
            default -> "字段值";
        };
    }

    private record Scenario(String topic, String ruleCode, String correctFieldType, String wrongFieldType) {
    }

    private record FileScenario(String topic, String ruleCode, String expectedFieldType) {
    }
}
