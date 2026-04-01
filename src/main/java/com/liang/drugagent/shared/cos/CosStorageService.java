package com.liang.drugagent.shared.cos;

import java.io.InputStream;

/**
 * COS存储服务接口。
 *
 * <p>定义对象存储的基本操作，包括预签名URL生成、文件上传下载等。</p>
 */
public interface CosStorageService {

    /**
     * 生成预签名上传URL。
     *
     * @param objectKey    对象key（存储路径）
     * @param contentType  文件内容类型
     * @return 预签名上传URL
     */
    String generateUploadUrl(String objectKey, String contentType);

    /**
     * 生成预签名下载URL。
     *
     * @param objectKey 对象key（存储路径）
     * @return 预签名下载URL
     */
    String generateDownloadUrl(String objectKey);

    /**
     * 上传文件。
     *
     * @param inputStream 输入流
     * @param objectKey   对象key（存储路径）
     * @param size        文件大小
     * @param contentType 文件内容类型
     * @return 对象的ETag
     */
    String uploadFile(InputStream inputStream, String objectKey, long size, String contentType);

    /**
     * 上传字节数组。
     *
     * @param data        字节数据
     * @param objectKey   对象key（存储路径）
     * @param contentType 文件内容类型
     * @return 对象的ETag
     */
    String uploadBytes(byte[] data, String objectKey, String contentType);

    /**
     * 下载文件。
     *
     * @param objectKey 对象key（存储路径）
     * @return 文件字节数组
     */
    byte[] downloadFile(String objectKey);

    /**
     * 删除文件。
     *
     * @param objectKey 对象key（存储路径）
     * @return 是否删除成功
     */
    boolean deleteFile(String objectKey);

    /**
     * 判断文件是否存在。
     *
     * @param objectKey 对象key（存储路径）
     * @return 是否存在
     */
    boolean fileExists(String objectKey);

    /**
     * 获取存储桶名称。
     */
    String getBucketName();

    /**
     * 获取CDN域名（如果有配置）。
     */
    String getCdnDomain();
}
