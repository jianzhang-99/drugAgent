package com.liang.drugagent.shared.rag.controller;

import com.liang.drugagent.shared.model.Result;
import com.liang.drugagent.shared.rag.cos.TencentCosStorageService;
import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件存储控制器。
 *
 * <p>提供腾讯云COS文件上传、下载、删除、列表等操作。</p>
 */
@Slf4j
@RestController
@RequestMapping("/oss")
public class OssController {

    private final TencentCosStorageService cosStorageService;
    private final OssFileMapper ossFileMapper;

    public OssController(TencentCosStorageService cosStorageService, OssFileMapper ossFileMapper) {
        this.cosStorageService = cosStorageService;
        this.ossFileMapper = ossFileMapper;
    }

    /**
     * 上传文件到COS并记录元信息。
     *
     * @param file      上传的文件
     * @param fileType  文件类型：1-对话附件，2-RAG知识库
     * @param sessionId 关联的业务ID
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", defaultValue = "1") Integer fileType,
            @RequestParam(value = "sessionId", required = false) String sessionId
    ) {
        try {
            String suffix = getFileSuffix(file.getOriginalFilename());
            String objectKey = buildObjectKey(fileType, suffix);

            File tempFile = multipartToFile(file);
            String etag = cosStorageService.uploadFile(tempFile, objectKey);
            tempFile.delete();

            LocalDateTime now = LocalDateTime.now();
            OssFile ossFile = OssFile.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSuffix(suffix)
                    .fileSize(file.getSize())
                    .ossUrl(objectKey)
                    .fileType(fileType)
                    .sessionId(sessionId)
                    .uploadStatus(1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            ossFileMapper.insert(ossFile);

            Map<String, Object> data = new HashMap<>();
            data.put("id", ossFile.getId());
            data.put("ossUrl", objectKey);
            data.put("etag", etag);
            data.put("bucket", cosStorageService.getBucketName());
            data.put("fileName", file.getOriginalFilename());
            data.put("fileSize", file.getSize());
            log.info("[OssController] 文件上传成功，ossUrl: {}, fileId: {}", objectKey, ossFile.getId());
            return Result.success(data);
        } catch (Exception e) {
            log.error("[OssController] 文件上传失败", e);
            String msg = e.getMessage() != null ? e.getMessage() : "文件上传失败";
            return Result.error(msg);
        }
    }

    /**
     * 获取文件列表。
     *
     * @param fileType  文件类型：1-对话附件，2-RAG知识库
     * @param sessionId 关联的业务ID（可选）
     * @param page      页码
     * @param pageSize  每页数量
     */
    @GetMapping("/files")
    public Result<Map<String, Object>> listFiles(
            @RequestParam(value = "fileType", required = false) Integer fileType,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize
    ) {
        try {
            LambdaQueryWrapper<OssFile> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(fileType != null, OssFile::getFileType, fileType)
                    .eq(sessionId != null, OssFile::getSessionId, sessionId)
                    .eq(OssFile::getUploadStatus, 1)
                    .orderByDesc(OssFile::getCreatedAt);

            List<OssFile> files = ossFileMapper.selectList(queryWrapper);

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, files.size());
            List<OssFile> pageData = start < files.size() ? files.subList(start, end) : List.of();

            Map<String, Object> data = new HashMap<>();
            data.put("list", pageData);
            data.put("total", files.size());
            data.put("page", page);
            data.put("pageSize", pageSize);
            return Result.success(data);
        } catch (Exception e) {
            log.error("[OssController] 获取文件列表失败", e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "获取文件列表失败");
        }
    }

    /**
     * 删除文件（软删除COS文件并更新数据库状态）。
     */
    @DeleteMapping("/files/{id}")
    public Result<Void> deleteFile(@PathVariable("id") String id) {
        try {
            OssFile ossFile = ossFileMapper.selectById(id);
            if (ossFile == null) {
                return Result.error("文件不存在");
            }

            cosStorageService.deleteFile(ossFile.getOssUrl());
            ossFile.setUploadStatus(3);
            ossFileMapper.updateById(ossFile);

            log.info("[OssController] 文件删除成功，id: {}, ossUrl: {}", id, ossFile.getOssUrl());
            return Result.success(null);
        } catch (Exception e) {
            log.error("[OssController] 文件删除失败，id: {}", id, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "文件删除失败");
        }
    }

    /**
     * 下载文件。
     */
    @GetMapping("/download/{ossUrl}")
    public Result<Map<String, Object>> download(@PathVariable("ossUrl") String ossUrl) {
        try {
            File tempFile = File.createTempFile("cos_download_", ".tmp");
            cosStorageService.downloadFile(ossUrl, tempFile);
            byte[] data = java.nio.file.Files.readAllBytes(tempFile.toPath());
            tempFile.delete();
            Map<String, Object> payload = new HashMap<>();
            payload.put("size", data.length);
            payload.put("content", new String(data, 0, Math.min(100, data.length)));
            log.info("[OssController] 文件下载成功，ossUrl: {}, size: {} bytes", ossUrl, data.length);
            return Result.success(payload);
        } catch (Exception e) {
            log.error("[OssController] 文件下载失败，ossUrl: {}", ossUrl, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "文件下载失败");
        }
    }

    /**
     * 下载 COS 文件到临时文件（供其他服务调用）。
     *
     * @param ossUrl COS 对象路径（URLEncode 后传入）
     * @return 临时文件路径
     */
    @GetMapping("/temp-file")
    public Result<Map<String, Object>> getTempFile(@RequestParam("ossUrl") String ossUrl) {
        try {
            File tempFile = File.createTempFile("cos_temp_", ".tmp");
            cosStorageService.downloadFile(ossUrl, tempFile);
            Map<String, Object> payload = new HashMap<>();
            payload.put("tempPath", tempFile.getAbsolutePath());
            payload.put("size", tempFile.length());
            log.info("[OssController] 临时文件生成成功，ossUrl: {}, tempPath: {}", ossUrl, tempFile.getAbsolutePath());
            return Result.success(payload);
        } catch (Exception e) {
            log.error("[OssController] 临时文件生成失败，ossUrl: {}", ossUrl, e);
            return Result.error(e.getMessage() != null ? e.getMessage() : "临时文件生成失败");
        }
    }

    private String buildObjectKey(Integer fileType, String suffix) {
        String dir = fileType != null && fileType == 2 ? "knowledge" : "attachment";
        return dir + "/" + LocalDate.now() + "/" + UUID.randomUUID() + "." + suffix;
    }

    private File multipartToFile(MultipartFile multipart) throws Exception {
        File tempFile = File.createTempFile("cos_upload_", "." + getFileSuffix(multipart.getOriginalFilename()));
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipart.getBytes());
        }
        return tempFile;
    }

    private String getFileSuffix(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
