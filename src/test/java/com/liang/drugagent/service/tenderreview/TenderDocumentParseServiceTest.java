package com.liang.drugagent.service.tenderreview;

import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TenderDocumentParseServiceTest {

    private static final String SAMPLE_A =
            "D:/Program/tools/drugAgent/doc/场景一 标书审查/02_原始样本素材/测试样本A_疑似围标投标文件.docx";

    private TenderDocumentParseService service;

    @BeforeEach
    void setUp() {
        TenderTextStructureSupport support = new TenderTextStructureSupport();
        service = new TenderDocumentParseService(
                new TenderDocxParser(support),
                new TenderDocParser(support),
                new TenderMarkdownParser(support)
        );
    }

    @Test
    void parseDocumentReturnsParagraphsAndTables() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            TenderDocumentParseResult result = service.parseDocument("doc-svc-test", "sample.docx", is);

            assertThat(result.getDocId()).isEqualTo("doc-svc-test");
            assertThat(result.getParagraphCount()).isGreaterThanOrEqualTo(90);
            assertThat(result.getTableCount()).isEqualTo(5);
            assertThat(result.getSectionTree()).hasSizeGreaterThanOrEqualTo(5);
            assertThat(result.getFields()).isNotNull();
            assertThat(result.getExtractionMeta()).isNotNull();
            assertThat(result.getExtractionMeta().getParseSuccess()).isTrue();
        }
    }

    @Test
    void parseDocumentSectionTreeNotNull() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            TenderDocumentParseResult result = service.parseDocument("doc-svc-test2", "sample.docx", is);
            assertThat(result.getSectionTree()).isNotNull();
        }
    }
}
