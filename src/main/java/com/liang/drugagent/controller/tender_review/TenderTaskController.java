package com.liang.drugagent.controller.controller.tender_review;

import com.liang.drugagent.controller.request.tender_review.TenderCaseCreateReq;
import com.liang.drugagent.controller.response.tender_review.TenderCaseCreateResp;
import com.liang.drugagent.scene.tender_review.model.TenderCase;
import com.liang.drugagent.scene.tender_review.model.TenderDocument;
import com.liang.drugagent.scene.tender_review.model.TenderDocumentParseResult;
import com.liang.drugagent.scene.tender_review.model.TenderReviewData;
import com.liang.drugagent.scene.tender_review.service.TenderCaseService;
import com.liang.drugagent.scene.tender_review.service.TenderDocumentParseService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 标书任务控制器。
 *
 * 保留一套入口处理标书任务的创建、解析、执行和结果查询。
 */
@RestController
@RequestMapping("/api/tender/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "标书任务", description = "标书审查任务生命周期管理")
@CrossOrigin(origins = "*")
public class TenderTaskController {

    @Schema(name = "CreateCaseForm", description = "创建审查任务 - 上传表单")
    static class CreateCaseForm {
        @ArraySchema(
                arraySchema = @Schema(description = "标书文件，至少2份，支持.doc/.docx/.md",
                        requiredMode = Schema.RequiredMode.REQUIRED),
                schema = @Schema(type = "string", format = "binary"))
        public List<MultipartFile> files;

        @Schema(description = "提交人，默认anonymous", defaultValue = "anonymous")
        public String submittedBy;
    }

    private final TenderCaseService tenderCaseService;
    private final TenderDocumentParseService tenderDocumentParseService;

    @Operation(summary = "创建标书审查任务")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "任务创建成功"),
            @ApiResponse(responseCode = "400", description = "文件数量不足或格式不支持")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createTask(
            @Parameter(hidden = true) @RequestParam("files") MultipartFile[] files,
            @Parameter(hidden = true) @RequestParam(value = "submittedBy", defaultValue = "anonymous") String submittedBy) {
        log.info("Create tender task request: submittedBy={}, fileCount={}", submittedBy, files.length);

        List<String> filenames = new ArrayList<>();
        for (MultipartFile file : files) {
            filenames.add(file.getOriginalFilename());
        }

        TenderCaseCreateReq req = TenderCaseCreateReq.builder()
                .filenames(filenames)
                .submittedBy(submittedBy)
                .build();

        TenderCaseCreateResp response;
        try {
            response = tenderCaseService.createCase(req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        List<String> documentIds = response.getDocumentIds();
        for (int i = 0; i < files.length; i++) {
            try {
                tenderCaseService.storeFileContent(documentIds.get(i), files[i].getBytes());
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "文件读取失败: " + files[i].getOriginalFilename()));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "查询标书审查任务列表")
    @GetMapping
    public ResponseEntity<?> listTasks() {
        return ResponseEntity.ok(tenderCaseService.listCases());
    }

    @Operation(summary = "解析指定文档")
    @PostMapping("/{caseId}/parse/{docId}")
    public ResponseEntity<?> parseDocument(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId,
            @Parameter(description = "文档ID", required = true) @PathVariable String docId) {
        log.info("Parse tender document request: caseId={}, docId={}", caseId, docId);

        Optional<byte[]> bytesOpt = tenderCaseService.getFileContent(docId);
        if (bytesOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到文件内容, docId=" + docId));
        }

        byte[] fileBytes = bytesOpt.get();
        if (fileBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "文档解析失败: 文件内容为空"));
        }

        Optional<TenderDocument> documentOpt = tenderCaseService.getDocument(docId);
        if (documentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到文档元数据, docId=" + docId));
        }

        try (ByteArrayInputStream stream = new ByteArrayInputStream(fileBytes)) {
            TenderDocumentParseResult result = tenderDocumentParseService.parseDocument(
                    docId,
                    documentOpt.get().getFilename(),
                    stream
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文档解析失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "执行审查任务")
    @PostMapping("/{caseId}/execute")
    public ResponseEntity<?> executeReview(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId) {
        try {
            TenderCase result = tenderCaseService.executeReview(caseId, new TenderReviewData());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "审查执行失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "查询审查结果")
    @GetMapping("/{caseId}/result")
    public ResponseEntity<?> getReviewResult(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId) {
        Optional<TenderCase> resultOpt = tenderCaseService.getReviewResult(caseId);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }
        return ResponseEntity.ok(resultOpt.get());
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{caseId}")
    public ResponseEntity<?> getTask(
            @Parameter(description = "任务ID", required = true) @PathVariable String caseId) {
        Optional<TenderCase> resultOpt = tenderCaseService.getReviewResult(caseId);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "未找到任务: " + caseId));
        }
        return ResponseEntity.ok(resultOpt.get());
    }
}
