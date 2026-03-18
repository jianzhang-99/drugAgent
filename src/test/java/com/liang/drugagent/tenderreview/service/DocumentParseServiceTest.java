package com.liang.drugagent.tenderreview.service;

import com.liang.drugagent.tenderreview.parser.DocxParser;
import com.liang.drugagent.tenderreview.parser.DocumentParseResult;
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

class DocumentParseServiceTest {

    private static final String SAMPLE_A =
            "D:/Program/tools/drugAgent/doc/场景一 标书审查/02_原始样本素材/测试样本A_疑似围标投标文件.docx";

    private DocumentParseService service;

    @BeforeEach
    void setUp() {
        service = new DocumentParseService(new DocxParser());
    }

    @Test
    void parseDocumentReturnsParagraphsAndTables() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = service.parseDocument("doc-svc-test", is);

            assertThat(result.getDocId()).isEqualTo("doc-svc-test");
            assertThat(result.getParagraphCount()).isGreaterThanOrEqualTo(90);
            assertThat(result.getTableCount()).isEqualTo(5);
            assertThat(result.getSectionTree()).hasSizeGreaterThanOrEqualTo(5);
            assertThat(result.getFields()).isNotNull();
            assertThat(result.getExtractionMeta()).isNotNull();
            assertThat(result.getExtractionMeta().isParseSuccess()).isTrue();
        }
    }

    @Test
    void parseDocumentSectionTreeNotNull() throws IOException {
        Path path = Paths.get(SAMPLE_A);
        assumeTrue(Files.exists(path), "样本文件A不存在，跳过测试");

        try (InputStream is = new FileInputStream(path.toFile())) {
            DocumentParseResult result = service.parseDocument("doc-svc-test2", is);
            assertThat(result.getSectionTree()).isNotNull();
        }
    }
}
