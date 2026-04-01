package com.liang.drugagent.controller;

import com.liang.drugagent.agent.common.entity.OssFile;
import com.liang.drugagent.agent.common.mapper.OssFileMapper;
import com.liang.drugagent.shared.cos.CosStorageService;
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
import java.util.stream.Collectors;

/**
 * 文件存储控制器。
 *
 * <p>提供腾讯云COS文件上传、下载、删除、列表等操作。</p>
 */
@Slf4j
@RestController
@RequestMapping("/oss")
public class OssController {

    private final CosStorageService cosStorageService;
    private final OssFileMapper ossFileMapper;

    public OssController(CosStorageService cosStorageService, OssFileMapper ossFileMapper) {
        this.cosStorageService = cosStorageService;
        this.ossFileMapper = ossFileMapper;
    }

    /**
     * 上传文件到COS并记录元信息。
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", defaultValue = "KNOWLEDGE") String bizType,
            @RequestParam(value = "bizId", required = false) String bizId,
            @RequestParam(value = "uploadBy", required = false) String uploadBy
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            String suffix = getFileSuffix(file.getOriginalFilename());
            String objectKey = "knowledge/" + LocalDate.now() + "/" + UUID.randomUUID() + "." + suffix;

            File tempFile = multipartToFile(file);
            String etag = cosStorageService.uploadFile(tempFile, objectKey);
            tempFile.delete();

            OssFile ossFile = OssFile.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSuffix(suffix)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .objectKey(objectKey)
                    .bizType(bizType)
                    .bizId(bizId)
                    .uploadBy(uploadBy)
                    .status(1)
                    .accessCount(0)
                    .build();
            ossFileMapper.insert(ossFile);

            result.put("success", true);
            result.put("data", Map.of(
                    "id", ossFile.getId(),
                    "objectKey", objectKey,
                    "etag", etag,
                    "bucket", cosStorageService.getBucketName(),
                    "fileName", file.getOriginalFilename(),
                    "fileSize", file.getSize()
            ));
            log.info("[OssController] 文件上传成功，objectKey: {}, fileId: {}", objectKey, ossFile.getId());
            return result;
        } catch (Exception e) {
            log.error("[OssController] 文件上传失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 获取知识库文件列表。
     */
    @GetMapping("/files")
    public Map<String, Object> listFiles(
            @RequestParam(value = "bizType", defaultValue = "KNOWLEDGE") String bizType,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize
    ) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<OssFile> files = ossFileMapper.selectList(null).stream()
                    .filter(f -> bizType.equals(f.getBizType()))
                    .filter(f -> f.getIsDeleted() == null || f.getIsDeleted() == 0)
                    .collect(Collectors.toList());

            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, files.size());
            List<OssFile> pageData = files.subList(start, end);

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

            cosStorageService.deleteFile(ossFile.getObjectKey());
            ossFile.setStatus(3);
            ossFile.setIsDeleted(1);
            ossFile.setUpdatedAt(LocalDateTime.now());
            ossFileMapper.updateById(ossFile);

            result.put("success", true);
            result.put("message", "删除成功");
            log.info("[OssController] 文件删除成功，id: {}, objectKey: {}", id, ossFile.getObjectKey());
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
    @GetMapping("/download/{objectKey}")
    public Map<String, Object> download(@PathVariable("objectKey") String objectKey) {
        Map<String, Object> result = new HashMap<>();
        try {
            File tempFile = File.createTempFile("cos_download_", ".tmp");
            cosStorageService.downloadFile(objectKey, tempFile);
            byte[] data = java.nio.file.Files.readAllBytes(tempFile.toPath());
            tempFile.delete();
            result.put("success", true);
            result.put("size", data.length);
            result.put("content", new String(data, 0, Math.min(100, data.length)));
            log.info("[OssController] 文件下载成功，objectKey: {}, size: {} bytes", objectKey, data.length);
            return result;
        } catch (Exception e) {
            log.error("[OssController] 文件下载失败，objectKey: {}", objectKey, e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
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
