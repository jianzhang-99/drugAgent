package com.liang.drugagent.shared.cos.impl;

import com.liang.drugagent.shared.cos.CosClientManager;
import com.liang.drugagent.shared.cos.CosConfigProperties;
import com.liang.drugagent.shared.cos.CosStorageService;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 腾讯云COS存储服务实现。
 *
 * <p>使用腾讯云COS Java SDK v5.x实现私有桶的文件操作。</p>
 * @author liangjiajian
 */
@Slf4j
@Service
public class TencentCosStorageServiceImpl implements CosStorageService {

    private final CosClientManager cosClientManager;
    private final CosConfigProperties cosConfig;

    public TencentCosStorageServiceImpl(CosClientManager cosClientManager, CosConfigProperties cosConfig) {
        this.cosClientManager = cosClientManager;
        this.cosConfig = cosConfig;
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public String getBucketName() {
        return cosConfig.getBucketName();
    }

}
