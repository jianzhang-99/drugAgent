package com.liang.drugagent.tenderreview.controller;

import com.liang.drugagent.tenderreview.domain.CaseCreateRequest;
import com.liang.drugagent.tenderreview.domain.CaseCreateResponse;
import com.liang.drugagent.tenderreview.parser.DocumentParseResult;
import com.liang.drugagent.tenderreview.service.CaseService;
import com.liang.drugagent.tenderreview.service.DocumentParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@Tag(name = "标书审查", description = "7.1 任务接收 / 7.2 文档解析")
@RestController
@RequestMapping("/tender-review")
public class TenderReviewController {

    // ---- Swagger schema for multipart upload ----
    @Schema(name = "CreateCaseForm", description = "创建审查任务 - 上传表单")
    static class CreateCaseForm {
        @ArraySchema(
                arraySchema = @Schema(description = "标书文件，至少 2 份，仅支持 .docx",
                        requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "string", format = "binary"))
        public List<MultipartFile> files;

        @Schema(description = "提交人，默认 anonymous", defaultValue = "anonymous")
        public String submittedBy;
    }

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
    @Operation(summary = "【7.1】上传标书文件，创建审查任务",
            description = "至少上传 2 份 .docx 格式标书文件。返回 caseId 及各文档的 docId，用于后续解析。")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "任务创建成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CaseCreateResponse.class))),
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
    @Operation(summary = "【7.2】解析指定文档",
            description = "对已上传的文档执行解析，返回章节树、段落块、表格块、字段提取结果及 extractionMeta。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析成功",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DocumentParseResult.class))),
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
