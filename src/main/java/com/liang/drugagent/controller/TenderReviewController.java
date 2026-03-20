package com.liang.drugagent.controller;

import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.TenderCase;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import com.liang.drugagent.service.tenderreview.TenderCaseService;
import com.liang.drugagent.service.tenderreview.TenderDocumentParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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

@Tag(name = "标书审查", description = "7.1 任务接收 / 7.2 文档解析")
@RestController
@RequestMapping("/tender-review")
public class TenderReviewController {

    private static final Logger log = LoggerFactory.getLogger(TenderReviewController.class);

    // ---- Swagger schema for multipart upload ----
    @Schema(name = "CreateCaseForm", description = "创建审查任务 - 上传表单")
    static class CreateCaseForm {
        @ArraySchema(
                arraySchema = @Schema(description = "标书文件，至少 2 份，支持 .doc / .docx / .md",
                        requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "string", format = "binary"))
        public List<MultipartFile> files;

        @Schema(description = "提交人，默认 anonymous", defaultValue = "anonymous")
        public String submittedBy;
    }

    private final TenderCaseService caseService;
    private final TenderDocumentParseService documentParseService;

    public TenderReviewController(TenderCaseService caseService,
                                  TenderDocumentParseService documentParseService) {
        this.caseService = caseService;
        this.documentParseService = documentParseService;
    }

    @Operation(summary = "查询标书审查任务列表",
            description = "返回当前已创建的标书审查任务，按创建时间倒序排列。")
    @GetMapping("/cases")
    public List<TenderCase> listCases() {
        log.info("List tender review cases");
        return caseService.listCases();
    }

    /**
     * POST /api/tender-review/cases
     * 上传标书文件，创建审查任务。
     */
    @Operation(summary = "【7.1】上传标书文件，创建审查任务",
            description = "至少上传 2 份 .doc / .docx / .md 格式标书文件。返回 caseId 及各文档的 docId，用于后续解析。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "任务创建成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TenderCaseCreateResp.class))),
            @ApiResponse(responseCode = "400", description = "文件数量不足或格式不支持",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(example = "{\"error\":\"至少需要 2 份标书文件进行比对审查\"}")))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(implementation = CreateCaseForm.class))
    )
    @PostMapping(value = "/cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createCase(
            @Parameter(hidden = true) @RequestParam("files") MultipartFile[] files,
            @Parameter(hidden = true) @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy) {
        log.info("Create tender case request: submittedBy={}, fileCount={}", submittedBy, files.length);

        List<String> filenames = new ArrayList<>();
        for (MultipartFile f : files) {
            filenames.add(f.getOriginalFilename());
        }
        log.info("Tender case filenames: {}", filenames);

        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(filenames)
                .submittedBy(submittedBy)
                .build();

        TenderCaseCreateResp response;
        try {
            response = caseService.createCase(req);
        } catch (IllegalArgumentException e) {
            log.warn("Create tender case rejected: reason={}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        // 存储文件内容
        List<String> documentIds = response.getDocumentIds();
        for (int i = 0; i < files.length; i++) {
            try {
                caseService.storeFileContent(documentIds.get(i), files[i].getBytes());
            } catch (IOException e) {
                log.error("Store uploaded file failed: docId={}, filename={}", documentIds.get(i), files[i].getOriginalFilename(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "文件读取失败: " + files[i].getOriginalFilename()));
            }
        }

        log.info("Tender case created successfully: caseId={}, documentIds={}", response.getCaseId(), response.getDocumentIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/tender-review/cases/{caseId}/parse/{docId}
     * 解析指定文档。
     */
    @Operation(summary = "【7.2】解析指定文档",
            description = "对已上传的文档执行解析，返回章节树、段落块、表格块、字段提取结果及 extractionMeta。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TenderDocumentParseResult.class))),
            @ApiResponse(responseCode = "404", description = "docId 不存在",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(example = "{\"error\":\"未找到文件内容, docId=xxx\"}"))),
            @ApiResponse(responseCode = "500", description = "文档解析异常",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(example = "{\"error\":\"文档解析失败: ...\"}")))
    })
    @PostMapping("/cases/{caseId}/parse/{docId}")
    public ResponseEntity<?> parseDocument(
            @Parameter(description = "审查任务 ID", required = true) @PathVariable String caseId,
            @Parameter(description = "文档 ID（来自创建任务的响应）", required = true) @PathVariable String docId) {
        log.info("Parse tender document request: caseId={}, docId={}", caseId, docId);

        Optional<byte[]> bytesOpt = caseService.getFileContent(docId);
        if (bytesOpt.isEmpty()) {
            log.warn("Parse tender document failed, file content missing: caseId={}, docId={}", caseId, docId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到文件内容, docId=" + docId));
        }

        byte[] fileBytes = bytesOpt.get();
        if (fileBytes.length == 0) {
            log.warn("Parse tender document failed, file content empty: caseId={}, docId={}", caseId, docId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "文档解析失败: 文件内容为空，请确认上传文件不是空文件"));
        }
        Optional<com.liang.drugagent.domain.tenderreview.TenderDocument> documentOpt = caseService.getDocument(docId);
        if (documentOpt.isEmpty()) {
            log.warn("Parse tender document failed, document metadata missing: caseId={}, docId={}", caseId, docId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到文档元数据, docId=" + docId));
        }

        String filename = documentOpt.get().getFilename();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(fileBytes)) {
            TenderDocumentParseResult result = documentParseService.parseDocument(docId, filename, stream);
            log.info("Parse tender document succeeded: caseId={}, docId={}, filename={}", caseId, docId, filename);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Parse tender document rejected: caseId={}, docId={}, reason={}", caseId, docId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Parse tender document IO failed: caseId={}, docId={}, filename={}", caseId, docId, filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文档解析失败（IO异常）: " + e.getMessage()));
        } catch (Exception e) {
            // Apache POI 在处理损坏文件（如非DOCX格式、截断文件）时
            // 会抛出 OLE2NotOfficeXmlFileException / POIXMLException 等非 IOException
            log.error("Parse tender document format failed: caseId={}, docId={}, filename={}", caseId, docId, filename, e);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "文档解析失败（文件格式异常）: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
        }
    }

    /**
     * POST /api/tender-review/cases/{caseId}/execute
     * 执行审查任务。
     */
    @Operation(summary = "【7.3】执行审查任务",
            description = "对已解析的文档执行规则审查，返回审查结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "审查执行成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TenderCase.class))),
            @ApiResponse(responseCode = "404", description = "caseId 不存在",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(example = "{\"error\":\"未找到任务: xxx\"}")))
    })
    @PostMapping("/cases/{caseId}/execute")
    public ResponseEntity<?> executeReview(
            @Parameter(description = "审查任务 ID", required = true) @PathVariable String caseId) {
        log.info("Execute review request: caseId={}", caseId);

        try {
            com.liang.drugagent.domain.tenderreview.TenderReviewData reviewData = new com.liang.drugagent.domain.tenderreview.TenderReviewData();
            com.liang.drugagent.domain.tenderreview.TenderCase result = caseService.executeReview(caseId, reviewData);
            log.info("Execute review succeeded: caseId={}", caseId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Execute review rejected: caseId={}, reason={}", caseId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Execute review failed: caseId={}", caseId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "审查执行失败: " + e.getMessage()));
        }
    }

    /**
     * GET /api/tender-review/cases/{caseId}/result
     * 查询审查结果。
     */
    @Operation(summary = "【7.4】查询审查结果",
            description = "查询指定任务的审查结果，包括风险等级、综合评分和详细命中结果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TenderCase.class))),
            @ApiResponse(responseCode = "404", description = "caseId 不存在",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(example = "{\"error\":\"未找到任务: xxx\"}")))
    })
    @GetMapping("/cases/{caseId}/result")
    public ResponseEntity<?> getReviewResult(
            @Parameter(description = "审查任务 ID", required = true) @PathVariable String caseId) {
        log.info("Get review result request: caseId={}", caseId);

        Optional<com.liang.drugagent.domain.tenderreview.TenderCase> resultOpt = caseService.getReviewResult(caseId);
        if (resultOpt.isEmpty()) {
            log.warn("Get review result failed, case not found: caseId={}", caseId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }

        return ResponseEntity.ok(resultOpt.get());
    }
}
