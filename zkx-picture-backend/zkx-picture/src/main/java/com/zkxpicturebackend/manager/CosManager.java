package com.zkxpicturebackend.manager;


import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.cos.transfer.Upload;
import com.zkxpicturebackend.config.CosClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    @Resource
    private TransferManager transferManager;



    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        log.info("COS上传文件, key={}, size={}B", key, file.length());
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        try {
            return cosClient.putObject(putObjectRequest);
        } catch (CosClientException e) {
            log.error("COS上传失败, key={}", key, e);
            throw e;
        }
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 添加图片处理规则，https://cloud.tencent.com/document/product/436/55377
        //图片上传完成后，对象存储（Cloud Object Storage，COS）会存储原始图片和已处理过的图片
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        compressRule.setFileId(webpKey);
        rules.add(compressRule);
        // 缩略图处理
        PicOperations.Rule thumbnailRule = new PicOperations.Rule();
        thumbnailRule.setBucket(cosClientConfig.getBucket());
        String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
        thumbnailRule.setFileId(thumbnailKey);
        // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
        thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
        rules.add(thumbnailRule);
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 分块上传文件（支持断点续传）
     */
    public UploadResult uploadFileWithResume(String key, File file) throws InterruptedException {
        log.info("COS分块上传开始, key={}", key);
        // 分块上传阈值和分块大小分别设置为 5MB 和 1MB（若不特殊设置，分块上传阈值和分块大小的默认值均为5MB）
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(5*1024*1024);
        transferManagerConfiguration.setMinimumUploadPartSize(1*1024*1024);
        transferManager.setConfiguration(transferManagerConfiguration);

        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);

        try {
            // 高级接口会返回一个异步结果Upload
            // 可同步地调用 waitForUploadResult 方法等待上传完成，成功返回 UploadResult, 失败抛出异常
            Upload upload = transferManager.upload(putObjectRequest);
            UploadResult uploadResult = upload.waitForUploadResult();
            return uploadResult;
        } catch (CosServiceException e) {
            log.error("COS分块上传失败, key={}", key, e);
        } catch (CosClientException e) {
            log.error("COS分块上传失败, key={}", key, e);
        } catch (InterruptedException e) {
            log.error("COS分块上传被中断, key={}", key, e);
        }
        return null;
    }
    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key) throws CosClientException {
        log.info("COS删除文件, key={}", key);
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

}

