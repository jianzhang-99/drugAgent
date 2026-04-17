package com.liang.drugagent.shared.rag.service;

import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.entity.RagFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import com.liang.drugagent.shared.rag.model.RagDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 知识库管理业务编排服务。
 *
 * <p>负责知识库管理的业务编排：
 * <ul>
 *   <li>文件导入：上传到 COS → 调用 IngestService 入库 → 保存元数据</li>
 *   <li>文件列表：查询 rag_file 和 oss_file 关联记录</li>
 *   <li>文件删除：删除向量库记录 → 删除 COS 文件 → 更新元数据</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeManagementService {

    private final TencentCosStorageService cosStorageService;
    private final OssFileMapper ossFileMapper;
    private final RagFileService ragFileService;
    private final IngestService ingestService;
    private final TextExtractor textExtractor;

    // 文件大小限制：50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    // 支持的文件格式
    private static final List<String> SUPPORTED_FILE_TYPES = List.of("md", "txt", "pdf");

    /**
     * 导入文档到知识库。
     *
     * <p>流程：上传到 COS → 创建 OssFile 记录 → 调用 IngestService 入库 → 创建 RagFile 记录。</p>
     *
     * @param file 上传的文件
     * @param title 文档标题（可选，默认使用文件名）
     * @param orgId 机构 ID
     * @param docType 文档类型
     * @param scene 业务场景
     * @param subScene 子场景
     * @return 导入结果，包含 ossId, sourceId, fileName, chunkCount
     */
    @Transactional
    public ImportResult importDocument(MultipartFile file, String title, String orgId,
                                       String docType, String scene, String subScene) throws IOException {
        // 1. 参数校验
        validateFile(file);

        String originalFileName = file.getOriginalFilename();
        String docTitle = (title != null && !title.isBlank()) ? title : originalFileName;
        String fileSuffix = getFileSuffix(originalFileName);

        // 2. 生成唯一 ID
        String ossId = UUID.randomUUID().toString();
        String sourceId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 3. 上传到 COS
        String cosPath = uploadToCos(ossId, file, fileSuffix);

        // 4. 创建 OssFile 记录
        OssFile ossFile = OssFile.builder()
                .id(ossId)
                .fileName(originalFileName)
                .fileSuffix(fileSuffix)
                .fileSize(file.getSize())
                .ossUrl(cosPath)
                .fileType(2) // 2-RAG知识库
                .uploadStatus(1) // 1-成功
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ossFileMapper.insert(ossFile);

        // 5. 调用 IngestService 入库
        int chunkCount = ingestDocument(sourceId, cosPath, docTitle, orgId, docType, scene, subScene, fileSuffix);

        // 6. 创建 RagFile 记录
        RagFile ragFile = RagFile.builder()
                .ossId(ossId)
                .sourceId(sourceId)
                .storedName(originalFileName)
                .originalName(originalFileName)
                .fileType(fileSuffix)
                .fileSize(BigDecimal.valueOf(file.getSize()))
                .orgId(orgId)
                .scene(scene)
                .chunkCount(chunkCount)
                .status(1)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ragFileService.save(ragFile);

        log.info("[KnowledgeManagementService] 文档导入成功 - ossId={}, sourceId={}, fileName={}, chunkCount={}",
                ossId, sourceId, originalFileName, chunkCount);

        return ImportResult.builder()
                .ossId(ossId)
                .sourceId(sourceId)
                .fileName(originalFileName)
                .chunkCount(chunkCount)
                .status("SUCCESS")
                .build();
    }

    /**
     * 导入文档（从已上传的 COS 文件）。
     *
     * <p>适用于前端先上传到 COS，获取 ossUrl 后再入库的场景。</p>
     *
     * @param ossUrl COS 文件路径
     * @param fileName 文件名
     * @param fileSuffix 文件后缀
     * @param fileSize 文件大小
     * @param title 文档标题
     * @param orgId 机构 ID
     * @param docType 文档类型
     * @param scene 业务场景
     * @param subScene 子场景
     * @param ossId OSS 文件 ID
     */
    @Transactional
    public ImportResult importFromOss(String ossUrl, String fileName, String fileSuffix,
                                       Long fileSize, String title, String orgId, String docType,
                                       String scene, String subScene, String ossId) throws IOException {
        // 1. 参数校验
        if (orgId == null || orgId.isBlank()) {
            throw new IllegalArgumentException("orgId 不能为空");
        }
        if (ossUrl == null || ossUrl.isBlank()) {
            throw new IllegalArgumentException("ossUrl 不能为空");
        }
        if (ossId == null || ossId.isBlank()) {
            throw new IllegalArgumentException("ossId 不能为空");
        }

        // 幂等检查
        if (ragFileService.isIngested(ossId)) {
            RagFile existingFile = ragFileService.findByOssId(ossId).orElse(null);
            log.info("[KnowledgeManagementService] 文件已入库（幂等跳过）- ossId={}, sourceId={}",
                    ossId, existingFile != null ? existingFile.getSourceId() : null);
            return ImportResult.builder()
                    .ossId(ossId)
                    .sourceId(existingFile != null ? existingFile.getSourceId() : null)
                    .fileName(fileName)
                    .chunkCount(existingFile != null ? existingFile.getChunkCount() : 0)
                    .status("SUCCESS")
                    .message("文件已入库，无需重复处理")
                    .build();
        }

        String docTitle = (title != null && !title.isBlank()) ? title : fileName;
        String sourceId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // 创建 OssFile 记录
        OssFile ossFile = OssFile.builder()
                .id(ossId)
                .fileName(fileName)
                .fileSuffix(fileSuffix)
                .fileSize(fileSize)
                .ossUrl(ossUrl)
                .fileType(2)
                .uploadStatus(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ossFileMapper.insert(ossFile);

        // 调用 IngestService 入库
        int chunkCount = ingestDocument(sourceId, ossUrl, docTitle, orgId, docType, scene, subScene, fileSuffix);

        // 创建 RagFile 记录
        RagFile ragFile = RagFile.builder()
                .ossId(ossId)
                .sourceId(sourceId)
                .storedName(fileName)
                .originalName(fileName)
                .fileType(fileSuffix)
                .fileSize(BigDecimal.valueOf(fileSize))
                .orgId(orgId)
                .scene(scene)
                .chunkCount(chunkCount)
                .status(1)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ragFileService.save(ragFile);

        log.info("[KnowledgeManagementService] COS文件导入成功 - ossId={}, sourceId={}, fileName={}",
                ossId, sourceId, fileName);

        return ImportResult.builder()
                .ossId(ossId)
                .sourceId(sourceId)
                .fileName(fileName)
                .chunkCount(chunkCount)
                .status("SUCCESS")
                .build();
    }

    /**
     * 查询知识库文件列表。
     *
     * @param orgId 机构 ID
     * @param keyword 搜索关键字（可选）
     * @param scene 业务场景（可选）
     * @return 文件列表
     */
    public List<RagFileVO> listDocuments(String orgId, String keyword, String scene) {
        List<RagFile> files;
        if (keyword != null && !keyword.isBlank()) {
            files = ragFileService.searchByFileName(orgId, keyword);
        } else if (scene != null && !scene.isBlank()) {
            files = ragFileService.findByOrgIdAndScene(orgId, scene);
        } else {
            files = ragFileService.findByOrgId(orgId);
        }

        return files.stream()
                .map(this::toRagFileVO)
                .toList();
    }

    /**
     * 删除知识库文件。
     *
     * <p>流程：删除向量库 chunks → 删除 COS 文件 → 更新元数据（软删除）。</p>
     *
     * @param fileId 文件 ID（rag_file.id）
     * @param orgId 机构 ID
     */
    @Transactional
    public void deleteDocument(Long fileId, String orgId) {
        RagFile ragFile = ragFileService.findById(fileId).orElse(null);
        if (ragFile == null || ragFile.getDeleted() == 1) {
            log.warn("[KnowledgeManagementService] 文件不存在或已删除 - fileId={}", fileId);
            return;
        }

        // 1. 删除向量库中的 chunks
        if (ragFile.getSourceId() != null && !ragFile.getSourceId().isBlank()) {
            ingestService.deleteBySourceId(ragFile.getSourceId());
        }

        // 2. 删除 COS 文件
        OssFile ossFile = ossFileMapper.selectById(ragFile.getOssId());
        if (ossFile != null && ossFile.getOssUrl() != null) {
            try {
                cosStorageService.deleteFile(ossFile.getOssUrl());
            } catch (Exception e) {
                log.warn("[KnowledgeManagementService] COS文件删除失败 - ossUrl={}", ossFile.getOssUrl(), e);
            }
        }

        // 3. 更新 rag_file 记录（软删除）
        ragFileService.delete(fileId);

        // 4. 更新 oss_file 记录（软删除）
        if (ossFile != null) {
            ossFile.setUploadStatus(3); // 3-已删除
            ossFileMapper.updateById(ossFile);
        }

        log.info("[KnowledgeManagementService] 文件删除完成 - fileId={}, ossId={}, sourceId={}",
                fileId, ragFile.getOssId(), ragFile.getSourceId());
    }

    /**
     * 删除知识库文件（根据 ossId）。
     */
    @Transactional
    public void deleteDocumentByOssId(String ossId, String orgId) {
        RagFile ragFile = ragFileService.findByOssId(ossId).orElse(null);
        if (ragFile == null) {
            log.warn("[KnowledgeManagementService] 未找到文件记录 - ossId={}", ossId);
            return;
        }
        deleteDocument(ragFile.getId(), orgId);
    }

    // ==================== 私有方法 ====================

    /**
     * 校验文件。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 50MB");
        }
        String suffix = getFileSuffix(file.getOriginalFilename());
        if (!SUPPORTED_FILE_TYPES.contains(suffix.toLowerCase())) {
            throw new IllegalArgumentException("文件格式不支持，仅支持 " + SUPPORTED_FILE_TYPES + " 格式");
        }
    }

    /**
     * 上传文件到 COS。
     */
    private String uploadToCos(String ossId, MultipartFile file, String fileSuffix) throws IOException {
        String cosFileName = "rag/" + ossId + "." + fileSuffix;
        File tempFile = null;
        try {
            // 将 MultipartFile 转换为临时文件
            tempFile = multipartToFile(file);
            return cosStorageService.uploadFile(tempFile, cosFileName);
        } catch (Exception e) {
            throw new IOException("文件上传到 COS 失败: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 将 MultipartFile 转换为临时文件。
     */
    private File multipartToFile(MultipartFile multipart) throws IOException {
        File tempFile = File.createTempFile("cos_upload_", "." + getFileSuffix(multipart.getOriginalFilename()));
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipart.getBytes());
        }
        return tempFile;
    }

    /**
     * 调用 IngestService 入库。
     */
    private int ingestDocument(String sourceId, String cosPath, String docTitle,
                               String orgId, String docType, String scene, String subScene,
                               String fileSuffix) throws IOException {
        // 从 COS 下载临时文件
        File tempFile = File.createTempFile("rag_ingest_", ".tmp");
        try {
            cosStorageService.downloadFile(cosPath, tempFile);

            // 使用 TextExtractor 提取文本
            String text = textExtractor.extract(tempFile.toPath());

            // 构建文档并入库
            RagDocument document = RagDocument.builder()
                    .sourceId(sourceId)
                    .title(docTitle)
                    .rawText(text)
                    .orgId(orgId)
                    .scene(scene)
                    .subScene(subScene)
                    .docType(docType)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 调用入库
            return ingestService.ingestAndReturnChunkCount(document);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 获取文件后缀。
     */
    private String getFileSuffix(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * 转换为 VO。
     */
    private RagFileVO toRagFileVO(RagFile file) {
        return RagFileVO.builder()
                .fileId(file.getId())
                .ossId(file.getOssId())
                .sourceId(file.getSourceId())
                .fileName(file.getOriginalName())
                .fileType(file.getFileType())
                .fileSize(file.getFileSize())
                .scene(file.getScene())
                .docType(file.getDocType())
                .chunkCount(file.getChunkCount())
                .status(file.getStatus())
                .createdAt(file.getCreatedAt())
                .build();
    }

    // ==================== 内部类 ====================

    /**
     * 导入结果。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ImportResult {
        private String ossId;
        private String sourceId;
        private String fileName;
        private Integer chunkCount;
        private String status;
        private String message;
    }

    /**
     * RAG 文件 VO。
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagFileVO {
        private Long fileId;
        private String ossId;
        private String sourceId;
        private String fileName;
        private String fileType;
        private java.math.BigDecimal fileSize;
        private String scene;
        private String docType;
        private Integer chunkCount;
        private Integer status;
        private java.time.LocalDateTime createdAt;
    }
}
