package com.zkxpicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.zkxpicturebackend.config.CosClientConfig;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.manager.CosManager;
import com.zkxpicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

@Slf4j
public abstract class PictureUploadTemplate {  
  
    @Resource
    protected CosManager cosManager;
  
    @Resource  
    protected CosClientConfig cosClientConfig;
  
    /**  
     * 模板方法，定义上传流程  同时上传了两张图片
     */  
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片
        //一
        validPicture(inputSource);  
  
        // 2. 图片上传地址  
        String uuid = RandomUtil.randomString(16);
        //二，得到文件名
        String originFilename = getOriginFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));

        // 部分 AI 生成或外部图片 URL 可能携带 ? 后缀参数，去除以防止上传失败
        if (uploadFilename.contains("?")){
            uploadFilename = uploadFilename.split("\\?")[0];
        }
        //uploadPathPrefix就是public/space+用户id
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 处理文件来源（本地或 URL）
            processFile(inputSource, file);
            // 上传图片到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            //得到数据万象返回的图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            //如果 objectList 非空（即有处理后的图片），
            // 则调用 buildResult(originFilename, compressedCiObject, thumbnailCiObject, imageInfo) 封装结果，
            // 其中压缩图 URL 设为 url，缩略图 URL 设为 thumbnailUrl。
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject compressedCiObject = objectList.get(0);
                CIObject thumbnailCiObject = objectList.get(1);
                // 封装压缩图返回结果
                return buildResult(originFilename, compressedCiObject, thumbnailCiObject,imageInfo);
            }
            //否则（没有处理结果），调用 buildResult(originFilename, file, uploadPath, imageInfo) 封装原图结果，url 为原图地址。
            // 封装原图返回结果
            return buildResult(originFilename, file, uploadPath, imageInfo);

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }

    }  
  
    /**  
     * 校验输入源（本地文件或 URL）  
     */  
    protected abstract void validPicture(Object inputSource);  
  
    /**  
     * 获取输入源的原始文件名  
     */  
    protected abstract String getOriginFilename(Object inputSource);  
  
    /**  
     * 处理输入源并生成本地临时文件  
     */  
    protected abstract void processFile(Object inputSource, File file) throws Exception;  
  
    /**  
     * 封装返回结果  
     */
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicColor(imageInfo.getAve());
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        return uploadPictureResult;
    }
    //封装返回类，后面用来写入数据库。TODO：目前数据库里是不存在原图地址的，如果用户需要下载原图该怎么办
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnailCiObject,ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicColor(imageInfo.getAve());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        //设置缩略图地址
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        // 设置压缩图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        return uploadPictureResult;
    }


    /**  
     * 删除临时文件  
     */  
    public void deleteTempFile(File file) {  
        if (file == null) {  
            return;  
        }  
        boolean deleteResult = file.delete();  
        if (!deleteResult) {  
            log.error("file delete error, filepath = {}", file.getAbsolutePath());  
        }  
    }  
}
