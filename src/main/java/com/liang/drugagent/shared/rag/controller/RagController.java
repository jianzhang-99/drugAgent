package com.liang.drugagent.shared.rag.controller;

import com.liang.drugagent.shared.model.Result;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.entity.RagFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import com.liang.drugagent.shared.rag.mapper.RagFileMapper;
import com.liang.drugagent.shared.rag.model.RagDocument;
import com.liang.drugagent.shared.rag.model.RagQueryRequest;
import com.liang.drugagent.shared.rag.model.RagQueryResponse;
import com.liang.drugagent.shared.rag.service.IngestService;
import com.liang.drugagent.shared.rag.service.RagService;
import com.liang.drugagent.shared.rag.service.TextExtractor;
import com.liang.drugagent.controller.domain.request.knowledge.KnowledgeAskReq;
import com.liang.drugagent.controller.domain.request.knowledge.KnowledgeIngestTextReq;
import com.liang.drugagent.controller.domain.response.knowledge.KnowledgeAskResp;
import com.liang.drugagent.controller.domain.response.knowledge.KnowledgeIngestResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 知识库 HTTP 接入层。
 *
 * <p>负责接收 HTTP 请求，调用底层 RAG 服务（IngestService / RagService），返回统一响应。</p>
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
@Tag(name = "Knowledge", description = "RAG 知识库管理")
public class RagController {

    private final RagService ragService;
    private final IngestService ingestService;
    private final TextExtractor textExtractor;
    private final TencentCosStorageService cosStorageService;
    private final OssFileMapper ossFileMapper;
    private final RagFileMapper ragFileMapper;

    /**
     * 从 COS URL 入库文件。
     *
     * <p>通过 COS 对象路径下载文件、提取文本、切分、向量化后存入知识库。</p>
     */
    @Operation(summary = "COS文件入库", description = "通过腾讯云COS对象路径入库文件，自动解析文本、向量化后存入知识库")
    @PostMapping("/ingest/oss")
    public Result<KnowledgeIngestResp> ingestFromOss(
            @RequestParam("ossUrl") String ossUrl,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "orgId") String orgId,
            @RequestParam(value = "ossId") String ossId,
            @RequestParam(value = "scene", required = false) String scene) {

        if (orgId == null || orgId.isBlank()) {
            return Result.error("orgId 不能为空");
        }
        if (ossUrl == null || ossUrl.isBlank()) {
            return Result.error("ossUrl 不能为空");
        }

        try {
            String decodedUrl = URLDecoder.decode(ossUrl, StandardCharsets.UTF_8);

            // 幂等检查：该文件是否已入库，是则直接返回成功
            var existing = ragFileMapper.findByOssId(ossId);
            if (existing.isPresent()) {
                RagFile existingFile = existing.get();
                log.info("[RagController] 文件已入库（幂等跳过）- ossId={}, sourceId={}", ossId, existingFile.getSourceId());
                return Result.success(KnowledgeIngestResp.builder()
                        .sourceId(existingFile.getSourceId())
                        .message("文件已入库，无需重复处理")
                        .build());
            }

            String sourceId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // 1. 从 COS 下载临时文件
            File tempFile = File.createTempFile("rag_oss_", ".tmp");
            cosStorageService.downloadFile(decodedUrl, tempFile);

            // 2. 从数据库补全 OSS 元信息
            OssFile ossFile = ossFileMapper.selectById(ossId);

            // 3. 解码文件名用于提取 title
            String fileName = decodedUrl.substring(decodedUrl.lastIndexOf('/') + 1);
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String docTitle = (title != null && !title.isBlank()) ? title : decodedFileName;

            // 4. 提取文本
            String text = textExtractor.extract(tempFile.toPath());

            // 5. 构建文档并入库向量库
            RagDocument document = RagDocument.builder()
                    .sourceId(sourceId)
                    .title(docTitle)
                    .rawText(text)
                    .orgId(orgId)
                    .scene(scene)
                    .createdAt(LocalDateTime.now())
                    .build();

            ingestService.ingest(document);
            tempFile.delete();

            // 6. 建立 OSS 文件与向量库 sourceId 的关联记录，写入 rag_file 表
            RagFile ragFile = RagFile.builder()
                    .ossId(ossId)
                    .sourceId(sourceId)
                    .storedName(ossFile != null ? ossFile.getFileName() : decodedFileName)
                    .originalName(ossFile != null ? ossFile.getFileName() : decodedFileName)
                    .fileType(ossFile != null ? ossFile.getFileSuffix() : getSuffix(decodedFileName))
                    .fileSize(ossFile != null && ossFile.getFileSize() != null
                            ? new java.math.BigDecimal(ossFile.getFileSize()) : null)
                    .scene(scene)
                    .status(1)
                    .deleted(0)
                    .build();
            ragFileMapper.insert(ragFile);
            log.info("[RagController] rag_file 记录已创建 - ossId={}, sourceId={}", ossId, sourceId);

            log.info("[RagController] COS文件入库完成 - ossUrl={}, title={}, sourceId={}", ossUrl, docTitle, sourceId);
            return Result.success(KnowledgeIngestResp.builder()
                    .sourceId(sourceId)
                    .message("入库成功: " + docTitle)
                    .build());
        } catch (Exception e) {
            log.error("[RagController] COS文件入库失败 - ossUrl={}", ossUrl, e);
            String cause = e.getMessage();
            if (cause == null || cause.isBlank()) {
                cause = "入库处理失败";
            }
            if (cause.contains("Duplicate entry")) {
                return Result.error("文件已入库，无需重复上传");
            }
            if (cause.contains("文件下载失败") || cause.contains("CosServiceException")) {
                return Result.error("文件下载失败，请检查COS配置和网络");
            }
            return Result.error("COS文件入库失败: " + cause);
        }
    }

    private String getSuffix(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 知识问答。
     *
     * <p>接收自然语言问题，在知识库中检索相关内容并生成回答。</p>
     */
    @Operation(summary = "知识问答", description = "基于知识库检索的自然语言问答接口")
    @PostMapping("/ask")
    public Result<KnowledgeAskResp> ask(@RequestBody KnowledgeAskReq req) {
        if (req.getOrgId() == null || req.getOrgId().isBlank()) {
            return Result.error("orgId 不能为空");
        }
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            return Result.error("question 不能为空");
        }

        RagQueryRequest ragReq = RagQueryRequest.builder()
                .question(req.getQuestion())
                .orgId(req.getOrgId())
                .scene(req.getScene())
                .subScene(req.getSubScene())
                .docType(req.getDocType())
                .topK(req.getTopK() != null ? req.getTopK() : 5)
                .sessionId(req.getSessionId())
                .topicTags(req.getTopicTags())
                .needGenerateAnswer(true)
                .build();

        RagQueryResponse ragResp = ragService.query(ragReq);
        return Result.success(toKnowledgeAskResp(ragResp));
    }

    /**
     * 仅检索（不生成回答）。
     *
     * <p>返回检索到的相关片段，供上层自行处理。</p>
     */
    @Operation(summary = "知识检索", description = "仅执行向量检索，不生成 LLM 回答")
    @PostMapping("/search")
    public Result<KnowledgeAskResp> search(@RequestBody KnowledgeAskReq req) {
        if (req.getOrgId() == null || req.getOrgId().isBlank()) {
            return Result.error("orgId 不能为空");
        }
        if (req.getQuestion() == null || req.getQuestion().isBlank()) {
            return Result.error("question 不能为空");
        }

        RagQueryRequest ragReq = RagQueryRequest.builder()
                .question(req.getQuestion())
                .orgId(req.getOrgId())
                .scene(req.getScene())
                .subScene(req.getSubScene())
                .docType(req.getDocType())
                .topK(req.getTopK() != null ? req.getTopK() : 5)
                .sessionId(req.getSessionId())
                .topicTags(req.getTopicTags())
                .needGenerateAnswer(false)
                .build();

        RagQueryResponse ragResp = ragService.query(ragReq);
        return Result.success(toKnowledgeAskResp(ragResp));
    }

    /**
     * 文本入库。
     *
     * <p>将纯文本内容切分、向量化后存入知识库。</p>
     */
    @Operation(summary = "文本入库", description = "将纯文本内容切分、向量化后存入知识库")
    @PostMapping("/ingest/text")
    public Result<KnowledgeIngestResp> ingestText(@RequestBody KnowledgeIngestTextReq req) {
        if (req.getOrgId() == null || req.getOrgId().isBlank()) {
            return Result.error("orgId 不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            return Result.error("content 不能为空");
        }

        String sourceId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RagDocument document = RagDocument.builder()
                .sourceId(sourceId)
                .title(req.getTitle() != null ? req.getTitle() : sourceId)
                .rawText(req.getContent())
                .orgId(req.getOrgId())
                .scene(req.getScene())
                .subScene(req.getSubScene())
                .docType(req.getDocType())
                .version(req.getVersion())
                .createdAt(LocalDateTime.now())
                .build();

        ingestService.ingest(document);

        log.info("[RagController] 文本入库完成 - sourceId={}, title={}", sourceId, req.getTitle());
        return Result.success(KnowledgeIngestResp.builder()
                .sourceId(sourceId)
                .message("入库成功")
                .build());
    }

    /**
     * 文件入库。
     *
     * <p>上传文件（.txt/.md/.docx），自动解析文本、切分、向量化后存入知识库。</p>
     */
    @Operation(summary = "文件入库", description = "上传文档文件，自动解析文本、向量化后存入知识库。支持 .txt/.md/.docx")
    @PostMapping(value = "/ingest/file", consumes = "multipart/form-data")
    public Result<KnowledgeIngestResp> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "orgId") String orgId,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "subScene", required = false) String subScene,
            @RequestParam(value = "docType", required = false) String docType) throws IOException {

        if (orgId == null || orgId.isBlank()) {
            return Result.error("orgId 不能为空");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("file 不能为空");
        }

        String fileName = file.getOriginalFilename();
        String docTitle = (title != null && !title.isBlank()) ? title : fileName;

        ingestService.ingest(file, docTitle, orgId, scene, subScene, docType);

        log.info("[RagController] 文件入库完成 - fileName={}, title={}, orgId={}", fileName, docTitle, orgId);
        return Result.success(KnowledgeIngestResp.builder()
                .sourceId(null)
                .message("文件入库成功: " + fileName)
                .build());
    }

    /**
     * 持久化向量库。
     *
     * <p>将内存中的向量数据手动持久化到本地文件。</p>
     */
    @Operation(summary = "持久化向量库", description = "将当前内存向量库持久化到本地文件")
    @PostMapping("/persist")
    public Result<Void> persist() {
        ingestService.save();
        return Result.success(null);
    }

    /**
     * 删除知识库文件。
     *
     * <p>根据 OSS 文件 ID，查询关联的 sourceId，先删除向量库中的 chunks，再删除关联记录。</p>
     */
    @Operation(summary = "删除知识库文件", description = "同时删除向量库 chunks 和关联记录")
    @DeleteMapping("/files/{ossId}")
    public Result<Void> deleteKnowledgeFile(@PathVariable String ossId) {
        if (ossId == null || ossId.isBlank()) {
            return Result.error("ossId 不能为空");
        }

        var rfOpt = ragFileMapper.findByOssId(ossId);
        if (rfOpt.isEmpty()) {
            return Result.error("未找到 rag_file 关联记录，可能该文件未入库");
        }

        RagFile rf = rfOpt.get();

        // 1. 删除向量库中的 chunks
        if (rf.getSourceId() != null && !rf.getSourceId().isBlank()) {
            ingestService.deleteBySourceId(rf.getSourceId());
        }

        // 2. 删除关联记录（软删除）
        rf.setDeleted(1);
        rf.setStatus(2);
        ragFileMapper.updateById(rf);

        log.info("[RagController] 知识库文件删除完成（软删除）- ossId={}, sourceId={}", ossId, rf.getSourceId());
        return Result.success(null);
    }

    /**
     * 批量删除知识库文件（清空当前所有RAG资料）。
     *
     * <p>删除内容：向量库chunks + rag_file关联记录 + COS文件。
     * 由于 rag_file 表暂无 org_id 字段，此接口清空所有未软删除的记录。</p>
     */
    @Operation(summary = "批量删除知识库文件", description = "清空所有RAG资料（向量库、文件记录、COS对象）")
    @DeleteMapping("/cleanup/all")
    public Result<Map<String, Object>> batchDeleteAll() {
        int deletedRagFileCount = 0;
        int deletedChunkCount = 0;
        int deletedCosCount = 0;

        try {
            // 1. 查询所有未软删除的rag_file记录
            List<RagFile> ragFiles = ragFileMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RagFile>()
                            .eq(RagFile::getDeleted, 0)
            );

            if (ragFiles.isEmpty()) {
                log.info("[RagController] 批量删除完成（无数据）");
                return Result.success(Map.of(
                        "message", "无知识库文件可删除",
                        "deletedRagFileCount", 0,
                        "deletedChunkCount", 0,
                        "deletedCosCount", 0
                ));
            }

            // 2. 遍历删除向量库chunks、rag_file记录
            for (RagFile rf : ragFiles) {
                // 2.1 删除向量库chunks
                if (rf.getSourceId() != null && !rf.getSourceId().isBlank()) {
                    ingestService.deleteBySourceId(rf.getSourceId());
                    deletedChunkCount++;
                }

                // 2.2 软删除rag_file记录
                rf.setDeleted(1);
                rf.setStatus(2);
                ragFileMapper.updateById(rf);
                deletedRagFileCount++;

                // 2.3 删除COS文件（根据ossId查OssFile再删）
                if (rf.getOssId() != null && !rf.getOssId().isBlank()) {
                    OssFile ossFile = ossFileMapper.selectById(rf.getOssId());
                    if (ossFile != null) {
                        try {
                            cosStorageService.deleteFile(ossFile.getOssUrl());
                            deletedCosCount++;
                        } catch (Exception e) {
                            log.warn("[RagController] COS文件删除失败（跳过）- ossId={}, error={}",
                                    rf.getOssId(), e.getMessage());
                        }
                        // 软删除oss_file记录
                        ossFile.setUploadStatus(3);
                        ossFileMapper.updateById(ossFile);
                    }
                }
            }

            log.info("[RagController] 批量删除完成 - ragFile={}, chunks={}, cos={}",
                    deletedRagFileCount, deletedChunkCount, deletedCosCount);

            return Result.success(Map.of(
                    "message", "批量删除完成",
                    "deletedRagFileCount", deletedRagFileCount,
                    "deletedChunkCount", deletedChunkCount,
                    "deletedCosCount", deletedCosCount
            ));

        } catch (Exception e) {
            log.error("[RagController] 批量删除失败", e);
            return Result.error("批量删除失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 将 RagQueryResponse 转换为前端响应格式
     */
    private KnowledgeAskResp toKnowledgeAskResp(RagQueryResponse resp) {
        if (resp == null) {
            return KnowledgeAskResp.builder().build();
        }

        return KnowledgeAskResp.builder()
                .answer(resp.getAnswer())
                .decision(resp.getDecision() != null ? resp.getDecision().name() : null)
                .reason(resp.getReason() != null ? resp.getReason().name() : null)
                .riskLevel(resp.getRiskLevel())
                .citations(resp.getCitations() != null ? resp.getCitations().stream()
                        .map(c -> KnowledgeAskResp.Citation.builder()
                                .sourceId(c.getSourceId())
                                .sourceTitle(c.getSourceTitle())
                                .chunkId(c.getChunkId())
                                .snippet(c.getSnippet())
                                .score(c.getScore() != null ? c.getScore().doubleValue() : null)
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }
}
