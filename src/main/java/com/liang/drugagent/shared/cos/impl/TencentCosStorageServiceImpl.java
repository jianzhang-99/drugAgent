package com.liang.drugagent.shared.cos.impl;

import com.liang.drugagent.shared.cos.CosClientManager;
import com.liang.drugagent.shared.cos.CosConfigProperties;
import com.liang.drugagent.shared.cos.CosStorageService;
import com.qcloud.cos.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 腾讯云COS存储服务实现。
 *
 * <p>使用腾讯云COS Java SDK v5.x实现私有桶的文件操作。</p>
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
    public String generateUploadUrl(String objectKey, String contentType) {
        // TODO: 根据SDK版本调整预签名URL生成方式
        log.warn("[TencentCosStorage] 预签名URL功能暂未实现，使用服务端上传代替，objectKey: {}", objectKey);
        return null;
    }

    @Override
    public String generateDownloadUrl(String objectKey) {
        // TODO: 根据SDK版本调整预签名URL生成方式
        log.warn("[TencentCosStorage] 预签名URL功能暂未实现，使用服务端下载代替，objectKey: {}", objectKey);
        return null;
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectKey, long size, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(size);
        metadata.setContentType(contentType);

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosConfig.getBucketName(),
                objectKey,
                inputStream,
                metadata
        );

        PutObjectResult result = cosClientManager.getClient().putObject(putObjectRequest);
        String etag = result.getETag();
        log.info("[TencentCosStorage] 文件上传成功，objectKey: {}, ETag: {}", objectKey, etag);
        return etag;
    }

    @Override
    public String uploadBytes(byte[] data, String objectKey, String contentType) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        return uploadFile(inputStream, objectKey, data.length, contentType);
    }

    @Override
    public byte[] downloadFile(String objectKey) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(
                cosConfig.getBucketName(),
                objectKey
        );
        COSObject cosObject = cosClientManager.getClient().getObject(getObjectRequest);
        COSObjectInputStream inputStream = cosObject.getObjectContent();

        try {
            byte[] data = inputStream.readAllBytes();
            log.info("[TencentCosStorage] 文件下载成功，objectKey: {}, size: {} bytes", objectKey, data.length);
            return data;
        } catch (Exception e) {
            log.error("[TencentCosStorage] 文件下载失败，objectKey: {}", objectKey, e);
            throw new RuntimeException("文件下载失败: " + objectKey, e);
        } finally {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean deleteFile(String objectKey) {
        DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(
                cosConfig.getBucketName(),
                objectKey
        );
        cosClientManager.getClient().deleteObject(deleteObjectRequest);
        log.info("[TencentCosStorage] 文件删除成功，objectKey: {}", objectKey);
        return true;
    }

    @Override
    public boolean fileExists(String objectKey) {
        try {
            cosClientManager.getClient().getObjectMetadata(
                    cosConfig.getBucketName(),
                    objectKey
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getBucketName() {
        return cosConfig.getBucketName();
    }

    @Override
    public String getCdnDomain() {
        return cosConfig.getCdnDomain();
    }
}
