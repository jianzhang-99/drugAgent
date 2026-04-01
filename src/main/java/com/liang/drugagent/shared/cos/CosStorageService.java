package com.liang.drugagent.shared.cos;


import java.io.File;

/**
 * COS存储服务接口。
 *
 * <p>定义对象存储的基本操作，包括文件上传下载、列表查询等。</p>
 * @author liangjiajian
 */
public interface CosStorageService {

    /**
     * 上传文件。
     *
     * @param file      本地文件
     * @param objectKey 对象key（存储路径）
     * @return 对象的ETag
     */
    String uploadFile(File file, String objectKey);

    /**
     * 下载文件到本地文件。
     *
     * @param objectKey 对象key（存储路径）
     * @param localFile 本地目标文件
     * @return 是否下载成功
     */
    boolean downloadFile(String objectKey, File localFile);

    /**
     * 删除文件。
     *
     * @param objectKey 对象key（存储路径）
     * @return 是否删除成功
     */
    boolean deleteFile(String objectKey);


    /**
     * 获取存储桶名称。
     */
    String getBucketName();

}
