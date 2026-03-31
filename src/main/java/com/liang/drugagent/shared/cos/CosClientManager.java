package com.liang.drugagent.shared.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 腾讯云COS客户端管理器。
 *
 * <p>负责管理COSClient实例的创建和销毁。</p>
 */
@Slf4j
@Component
public class CosClientManager {

    private final CosConfigProperties cosConfig;

    private COSClient cosClient;

    public CosClientManager(CosConfigProperties cosConfig) {
        this.cosConfig = cosConfig;
    }

    /**
     * 获取COSClient实例。
     */
    public synchronized COSClient getClient() {
        if (cosClient == null) {
            COSCredentials credentials = new BasicCOSCredentials(
                    cosConfig.getSecretId(),
                    cosConfig.getSecretKey()
            );
            Region region = new Region(cosConfig.getBucketRegion());
            ClientConfig clientConfig = new ClientConfig(region);
            cosClient = new COSClient(credentials, clientConfig);
            log.info("[CosClientManager] COS客户端初始化成功，Bucket: {}, Region: {}",
                    cosConfig.getBucketName(), cosConfig.getBucketRegion());
        }
        return cosClient;
    }

    /**
     * 销毁COSClient实例。
     */
    public synchronized void shutdown() {
        if (cosClient != null) {
            cosClient.shutdown();
            cosClient = null;
            log.info("[CosClientManager] COS客户端已关闭");
        }
    }
}
