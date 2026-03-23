package com.liang.drugagent.controller.tender;

import com.liang.drugagent.application.tender.TenderApplicationService;
import com.liang.drugagent.domain.req.TenderCaseCreateReq;
import com.liang.drugagent.domain.resp.TenderCaseCreateResp;
import com.liang.drugagent.domain.tenderreview.TenderDocumentParseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标书案例控制器。
 *
 * <p>负责标书文档上传和解析。</p>
 *
 * @author liangjiajian
 */
@RestController
@RequestMapping("/api/tender/cases")
@RequiredArgsConstructor
@Tag(name = "标书案例", description = "标书文档管理")
@CrossOrigin(origins = "*")
public class TenderCaseController {

    private static final Logger log = LoggerFactory.getLogger(TenderCaseController.class);

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

    private final TenderApplicationService tenderApplicationService;

    @Operation(summary = "创建标书审查任务")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "任务创建成功"),
            @ApiResponse(responseCode = "400", description = "文件数量不足或格式不支持")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
            response = tenderApplicationService.createCase(req, null);
        } catch (IllegalArgumentException e) {
            log.warn("Create tender case rejected: reason={}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        List<String> documentIds = response.getDocumentIds();
        for (int i = 0; i < files.length; i++) {
            try {
                tenderApplicationService.storeFileContent(documentIds.get(i), files[i].getBytes());
            } catch (IOException e) {
                log.error("Store uploaded file failed: docId={}, filename={}", documentIds.get(i), files[i].getOriginalFilename(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "文件读取失败: " + files[i].getOriginalFilename()));
            }
        }

        log.info("Tender case created successfully: caseId={}, documentIds={}", response.getCaseId(), response.getDocumentIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "解析指定文档")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解析成功"),
            @ApiResponse(responseCode = "404", description = "docId不存在"),
            @ApiResponse(responseCode = "500", description = "文档解析异常")
    })
    @PostMapping("/{caseId}/parse/{docId}")
    public ResponseEntity<?> parseDocument(
            @Parameter(description = "审查任务ID", required = true) @PathVariable String caseId,
            @Parameter(description = "文档ID", required = true) @PathVariable String docId) {
        log.info("Parse tender document request: caseId={}, docId={}", caseId, docId);

        try {
            TenderDocumentParseResult result = tenderApplicationService.parseDocument(caseId, docId);
            log.info("Parse tender document succeeded: caseId={}, docId={}", caseId, docId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Parse tender document rejected: caseId={}, docId={}, reason={}", caseId, docId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Parse tender document failed: caseId={}, docId={}", caseId, docId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文档解析失败: " + e.getMessage()));
        }
    }
}
