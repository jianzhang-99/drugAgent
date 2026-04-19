package com.liang.drugagent.scene.tender_review.support.assembler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.drugagent.scene.tender_review.model.Field;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.tool.document.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenderReviewDataAssembler 字段抽取回归测试。
 */
class TenderReviewDataAssemblerTest {

    private final TenderReviewDataAssembler assembler = new TenderReviewDataAssembler(new ObjectMapper());

    @Test
    void shouldNotTreatBoldNumbersAsTypos() {
        String content = """
                # 案例数据

                - 成功实现了院区范围内**8**家分院（含总院及7家卫星院区）的数据互联互通与业务协同。
                - 规范化管理全院**612**个临床及行政科室的排班统筹、绩效核算与运营指标追踪。
                - 我们在关键技术响应中修正了**应急响映**自动触发策略。
                """;

        TenderReviewData data = assembler.resolve(new ParsedDocument[]{
                ParsedDocument.builder()
                        .documentId("doc-1")
                        .filename("doc.md")
                        .fileType("md")
                        .plainText(content)
                        .normalizedText(content)
                        .build()
        }, "trace-1");

        assertNotNull(data);

        List<Field> typoFields = data.getFields().stream()
                .filter(field -> "typo".equals(field.getFieldType()))
                .toList();

        assertEquals(1, typoFields.size());
        assertEquals("应急响映", typoFields.get(0).getNormalizedValue());
    }

    @Test
    void shouldStillExtractKnownRareTypos() {
        String content = """
                # 罕见错误共现

                系统在关键节点上引入了应急响映自动触发策略，并通过高可用堆叠架构保障稳定性。
                系统通过串并口通讯协议联动，避免逻辑漏斗引起的冗余请求。
                """;

        TenderReviewData data = assembler.resolve(new ParsedDocument[]{
                ParsedDocument.builder()
                        .documentId("doc-1")
                        .filename("doc.md")
                        .fileType("md")
                        .plainText(content)
                        .normalizedText(content)
                        .build()
        }, "trace-2");

        assertNotNull(data);

        List<Field> typoFields = data.getFields().stream()
                .filter(field -> "typo".equals(field.getFieldType()))
                .toList();

        assertTrue(typoFields.stream().anyMatch(field -> "应急响映".equals(field.getNormalizedValue())));
        assertTrue(typoFields.stream().anyMatch(field -> "串并口".equals(field.getNormalizedValue())));
        assertTrue(typoFields.stream().anyMatch(field -> "高可用堆叠".equals(field.getNormalizedValue())));
        assertTrue(typoFields.stream().anyMatch(field -> "逻辑漏斗".equals(field.getNormalizedValue())));
    }
}
