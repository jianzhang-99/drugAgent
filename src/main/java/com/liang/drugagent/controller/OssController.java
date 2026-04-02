package com.liang.drugagent.controller;

import com.liang.drugagent.agent.common.entity.OssFile;
import com.liang.drugagent.agent.common.mapper.OssFileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.shared.cos.TencentCosStorageService;
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
     * @param file     上传的文件
     * @param fileType 文件类型：1-对话附件，2-RAG知识库
     * @param sessionId 关联的业务ID
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", defaultValue = "1") Integer fileType,
            @RequestParam(value = "sessionId", required = false) String sessionId
    ) {
        Map<String, Object> result = new HashMap<>();
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

            result.put("success", true);
            result.put("data", Map.of(
                    "id", ossFile.getId(),
                    "ossUrl", objectKey,
                    "etag", etag,
                    "bucket", cosStorageService.getBucketName(),
                    "fileName", file.getOriginalFilename(),
                    "fileSize", file.getSize()
            ));
            log.info("[OssController] 文件上传成功，ossUrl: {}, fileId: {}", objectKey, ossFile.getId());
            return result;
        } catch (Exception e) {
            log.error("[OssController] 文件上传失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取文件列表。
     *
     * @param fileType 文件类型：1-对话附件，2-RAG知识库
     * @param sessionId 关联的业务ID（可选）
     * @param page     页码
     * @param pageSize 每页数量
     */
    @GetMapping("/files")
    public Map<String, Object> listFiles(
            @RequestParam(value = "fileType", required = false) Integer fileType,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize
    ) {
        Map<String, Object> result = new HashMap<>();
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

            result.put("success", true);
            result.put("data", Map.of(
                    "list", pageData,
                    "total", files.size(),
                    "page", page,
                    "pageSize", pageSize
            ));
            return result;
        } catch (Exception e) {
            log.error("[OssController] 获取文件列表失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 删除文件（软删除COS文件并更新数据库状态）。
     */
    @DeleteMapping("/files/{id}")
    public Map<String, Object> deleteFile(@PathVariable("id") String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            OssFile ossFile = ossFileMapper.selectById(id);
            if (ossFile == null) {
                result.put("success", false);
                result.put("error", "文件不存在");
                return result;
            }

            cosStorageService.deleteFile(ossFile.getOssUrl());
            ossFile.setUploadStatus(3);
            ossFileMapper.updateById(ossFile);

            result.put("success", true);
            result.put("message", "删除成功");
            log.info("[OssController] 文件删除成功，id: {}, ossUrl: {}", id, ossFile.getOssUrl());
            return result;
        } catch (Exception e) {
            log.error("[OssController] 文件删除失败，id: {}", id, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 下载文件。
     */
    @GetMapping("/download/{ossUrl}")
    public Map<String, Object> download(@PathVariable("ossUrl") String ossUrl) {
        Map<String, Object> result = new HashMap<>();
        try {
            File tempFile = File.createTempFile("cos_download_", ".tmp");
            cosStorageService.downloadFile(ossUrl, tempFile);
            byte[] data = java.nio.file.Files.readAllBytes(tempFile.toPath());
            tempFile.delete();
            result.put("success", true);
            result.put("size", data.length);
            result.put("content", new String(data, 0, Math.min(100, data.length)));
            log.info("[OssController] 文件下载成功，ossUrl: {}, size: {} bytes", ossUrl, data.length);
            return result;
        } catch (Exception e) {
            log.error("[OssController] 文件下载失败，ossUrl: {}", ossUrl, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
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
