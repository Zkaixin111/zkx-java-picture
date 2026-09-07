package com.zkxpicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.transfer.TransferManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class CosTransferManagerConfig {
    @Resource
    private COSClient cosClient;

    @Bean
    public TransferManager transferManager() {
        // 这里可以设置线程池大小等参数，默认即可
        return new TransferManager(cosClient);
    }
}