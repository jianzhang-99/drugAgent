package com.liang.drugagent.tenderreview.controller;

import com.liang.drugagent.tenderreview.domain.CaseCreateRequest;
import com.liang.drugagent.tenderreview.domain.CaseCreateResponse;
import com.liang.drugagent.tenderreview.parser.DocumentParseResult;
import com.liang.drugagent.tenderreview.service.CaseService;
import com.liang.drugagent.tenderreview.service.DocumentParseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/tender-review")
public class TenderReviewController {

    private final CaseService caseService;
    private final DocumentParseService documentParseService;

    public TenderReviewController(CaseService caseService,
                                  DocumentParseService documentParseService) {
        this.caseService = caseService;
        this.documentParseService = documentParseService;
    }

    /**
     * POST /api/tender-review/cases
     * 上传标书文件，创建审查任务。
     */
    @PostMapping("/cases")
    public ResponseEntity<?> createCase(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy) {

        List<String> filenames = new ArrayList<>();
        for (MultipartFile f : files) {
            filenames.add(f.getOriginalFilename());
        }

        CaseCreateRequest req = CaseCreateRequest.builder()
                .filenames(filenames)
                .submittedBy(submittedBy)
                .build();

        CaseCreateResponse response;
        try {
            response = caseService.createCase(req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        // 存储文件内容
        List<String> documentIds = response.getDocumentIds();
        for (int i = 0; i < files.length; i++) {
            try {
                caseService.storeFileContent(documentIds.get(i), files[i].getBytes());
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "文件读取失败: " + files[i].getOriginalFilename()));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/tender-review/cases/{caseId}/parse/{docId}
     * 解析指定文档。
     */
    @PostMapping("/cases/{caseId}/parse/{docId}")
    public ResponseEntity<?> parseDocument(
            @PathVariable String caseId,
            @PathVariable String docId) {

        Optional<byte[]> bytesOpt = caseService.getFileContent(docId);
        if (bytesOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到文件内容, docId=" + docId));
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytesOpt.get())) {
            DocumentParseResult result = documentParseService.parseDocument(docId, stream);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文档解析失败: " + e.getMessage()));
        }
    }
}
