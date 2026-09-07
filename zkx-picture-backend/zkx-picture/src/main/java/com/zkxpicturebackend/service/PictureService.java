package com.zkxpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zkxpicturebackend.model.dto.picature.*;
import com.zkxpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.vo.PictureVO;
import com.zkxpicturebackend.model.vo.PictureTagCategory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 29486
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2026-04-11 20:04:47
*/

public interface PictureService extends IService<Picture> {


    /**
     * 上传图片
     *
     * @param multipartFile 传入的图片
     * @param pictureUploadRequest id
     * @param loginUser 用户
     * @return vo类
     */
//    PictureVO uploadPicture(MultipartFile multipartFile,
//                            PictureUploadRequest pictureUploadRequest,
//                            User loginUser);

    /**
     *
     * @param
     * @param pictureUploadRequest id
     * @param loginUser 用户
     * @return vo类
     *///更新并没有将原来的图片在阿里云对象存储中删除，而是直接增加了一个图片，但是数据库却还是同一个id，只是url指向了另一个图片
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 获取查询的 queryWrapper
     * @param pictureQueryRequest 图片请求类
     * @return 可用来查询的 queryWrapper
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);



    /**
     * 获取单个图片的 VO 对象
     * @param picture picture 对象
     * @param request request 请求
     * @return 对应图片的 VO
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);


    /**
     * 从缓存中获取图片
     * @param cacheKey 缓存键
     * @return 图片 VO
     */
    Page<PictureVO> selectFromCache(String cacheKey);

    /**
     * 更新reids和Cache缓存
     * @param pictureVOPage 图片查询请求
     */
    void updateCache(Page<PictureVO> pictureVOPage, String cacheKey);
    /**
     * 分页获取图片 VO 对象
     * @param picturePage  page 对象
     * @param request request 请求
     * @return 分页的 VO
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验参数
     * @param picture 需要校验的 picture 对象
     */
    void validPicture(Picture picture);

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求（可以用来审核未审核图片，也可以用来修改审核状态，比如把同意改为拒绝）
     * @param loginUser            当前登录的 user
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核信息
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);
    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 传入的关键词和数量
     * @param loginUser 当前用户
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

    @Async
    void clearPictureFile(Picture oldPicture);

    /**
     * 校验图片操作权限，后改为注解鉴权
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    void deletePicture(long pictureId, User loginUser);

    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    @Transactional(rollbackFor = Exception.class)
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 异步生成图片智能标签并更新数据库
     * @param pictureId 图片ID
     * @param imageUrl 图片URL
     */
    @Async
    void generateAndSaveTags(Long pictureId, String imageUrl);

    /**
     * 获取图片标签和分类（动态从数据库读取，热门标签 Top10）
     * @return 标签和分类列表
     */
    PictureTagCategory listPictureTagCategory();

}
