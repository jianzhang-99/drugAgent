package com.liang.drugagent.shared.rag.cos;

import com.liang.drugagent.shared.rag.entity.OssFile;
import com.liang.drugagent.shared.rag.mapper.OssFileMapper;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 腾讯云COS存储服务实现。
 *
 * <p>使用腾讯云COS Java SDK v5.x实现私有桶的文件操作。
 * 提供文件上传、下载、删除、元信息持久化等全部能力。</p>
 *
 * @author liangjiajian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TencentCosStorageService {

    private final CosClientManager cosClientManager;
    private final CosConfigProperties cosConfig;
    private final OssFileMapper ossFileMapper;

    /** 对话附件类型 */
    private static final Integer FILE_TYPE_ATTACHMENT = 1;

    /**
     * 上传文件到COS。
     */
    public String uploadFile(File file, String objectKey) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosConfig.getBucketName(),
                objectKey,
                file
        );

        try {
            PutObjectResult result = cosClientManager.getClient().putObject(putObjectRequest);
            String etag = result.getETag();
            log.info("[TencentCosStorage] 文件上传成功，objectKey: {}, ETag: {}", objectKey, etag);
            return etag;
        } catch (CosServiceException cse) {
            log.error("[TencentCosStorage] 文件上传失败(CosServiceException)，objectKey: {}, error: {}", objectKey, cse.getErrorMessage());
            throw new RuntimeException("文件上传失败: " + objectKey, cse);
        } catch (CosClientException cce) {
            log.error("[TencentCosStorage] 文件上传失败(CosClientException)，objectKey: {}, error: {}", objectKey, cce.getMessage());
            throw new RuntimeException("文件上传失败: " + objectKey, cce);
        }
    }

    /**
     * 保存上传的文件到COS并写入数据库。
     *
     * @param sessionId 关联的会话ID
     * @param files     上传的文件数组
     * @return 保存成功的文件列表
     */
    public List<OssFile> saveUploadedFiles(String sessionId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<OssFile> savedFiles = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                log.warn("[TencentCosStorage] 跳过空文件");
                continue;
            }

            try {
                OssFile ossFile = saveSingleFile(sessionId, file, now);
                savedFiles.add(ossFile);
                log.info("[TencentCosStorage] 文件保存成功，fileId={}, fileName={}, sessionId={}",
                        ossFile.getId(), ossFile.getFileName(), sessionId);
            } catch (Exception e) {
                log.error("[TencentCosStorage] 文件保存失败，fileName={}, sessionId={}",
                        file.getOriginalFilename(), sessionId, e);
                throw new RuntimeException("文件保存失败: " + file.getOriginalFilename(), e);
            }
        }

        return savedFiles;
    }

    /**
     * 保存单个文件。
     */
    private OssFile saveSingleFile(String sessionId, MultipartFile file, LocalDateTime now) throws Exception {
        String suffix = getFileSuffix(file.getOriginalFilename());
        String objectKey = buildObjectKey(sessionId, suffix);

        // 上传 COS
        File tempFile = multipartToFile(file);
        uploadFile(tempFile, objectKey);
        tempFile.delete();

        // 写入数据库
        OssFile ossFile = OssFile.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .fileName(file.getOriginalFilename())
                .fileSuffix(suffix)
                .fileSize(file.getSize())
                .ossUrl(objectKey)
                .fileType(FILE_TYPE_ATTACHMENT)
                .uploadStatus(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ossFileMapper.insert(ossFile);

        return ossFile;
    }

    /**
     * 构建COS对象路径。
     * 格式：attachment/{yyyy-MM-dd}/{sessionId}/{uuid}.{suffix}
     */
    private String buildObjectKey(String sessionId, String suffix) {
        return String.format("attachment/%s/%s/%s.%s",
                LocalDate.now(), sessionId, UUID.randomUUID(), suffix);
    }

    /**
     * 将MultipartFile转为临时文件。
     */
    private File multipartToFile(MultipartFile multipart) throws Exception {
        File tempFile = File.createTempFile("cos_upload_", "." + getFileSuffix(multipart.getOriginalFilename()));
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipart.getBytes());
        }
        return tempFile;
    }

    /**
     * 获取文件后缀。
     */
    private String getFileSuffix(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 下载文件到本地文件。
     */
    public boolean downloadFile(String objectKey, File localFile) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosConfig.getBucketName(),
                objectKey
        );

        try {
            ObjectMetadata metadata = cosClientManager.getClient().getObject(getObjectRequest, localFile);
            log.info("[TencentCosStorage] 文件下载成功，objectKey: {}, localFile: {}, size: {} bytes",
                    objectKey, localFile.getAbsolutePath(), metadata.getContentLength());
            return true;
        } catch (CosServiceException cse) {
            log.error("[TencentCosStorage] 文件下载失败(CosServiceException)，objectKey: {}, error: {}", objectKey, cse.getErrorMessage());
            throw new RuntimeException("文件下载失败: " + objectKey, cse);
        } catch (CosClientException cce) {
            log.error("[TencentCosStorage] 文件下载失败(CosClientException)，objectKey: {}, error: {}", objectKey, cce.getMessage());
            throw new RuntimeException("文件下载失败: " + objectKey, cce);
        }
    }

    /**
     * 删除文件。
     */
    public boolean deleteFile(String objectKey) {
        DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(
                cosConfig.getBucketName(),
                objectKey
        );
        try {
            cosClientManager.getClient().deleteObject(deleteObjectRequest);
            log.info("[TencentCosStorage] 文件删除成功，objectKey: {}", objectKey);
            return true;
        } catch (CosServiceException cse) {
            log.error("[TencentCosStorage] 文件删除失败(CosServiceException)，objectKey: {}, error: {}", objectKey, cse.getErrorMessage());
            throw new RuntimeException("文件删除失败: " + objectKey, cse);
        } catch (CosClientException cce) {
            log.error("[TencentCosStorage] 文件删除失败(CosClientException)，objectKey: {}, error: {}", objectKey, cce.getMessage());
            throw new RuntimeException("文件删除失败: " + objectKey, cce);
        }
    }

    /**
     * 获取存储桶名称。
     */
    public String getBucketName() {
        return cosConfig.getBucketName();
    }
}
