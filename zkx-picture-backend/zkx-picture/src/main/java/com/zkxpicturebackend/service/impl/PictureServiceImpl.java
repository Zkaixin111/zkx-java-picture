package com.zkxpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zkxpicturebackend.api.aliyunai.AliYunAiApi;
import com.zkxpicturebackend.api.aliyunai.model.ImageRecognitionResult;
import com.zkxpicturebackend.common.ResultUtils;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import com.zkxpicturebackend.manager.CosManager;
import com.zkxpicturebackend.manager.rabbitmq.AiTagProducer;
import com.zkxpicturebackend.manager.upload.FilePictureUpload;
import com.zkxpicturebackend.manager.upload.PictureUploadTemplate;
import com.zkxpicturebackend.manager.upload.UrlPictureUpload;
import com.zkxpicturebackend.model.dto.file.UploadPictureResult;
import com.zkxpicturebackend.model.dto.picature.*;
import com.zkxpicturebackend.model.entity.Picture;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.enums.PictureReviewStatusEnum;
import com.zkxpicturebackend.model.vo.PictureTagCategory;
import com.zkxpicturebackend.model.vo.PictureVO;
import com.zkxpicturebackend.model.vo.UserVO;
import com.zkxpicturebackend.service.PictureService;
import com.zkxpicturebackend.mapper.PictureMapper;
import com.zkxpicturebackend.service.SpaceService;
import com.zkxpicturebackend.service.UserService;
import com.zkxpicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 29486
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-04-11 20:04:47
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    public static final String HOT_TAGS_REDIS_KEY = "picture:hotTags";

    // 批量抓取模式标记：设为 true 时 uploadPicture 跳过 AI 标签触发
    private static final ThreadLocal<Boolean> SKIP_AI_TAG = ThreadLocal.withInitial(() -> false);

    @Resource
    private UserService userService;
    @Autowired
    private FilePictureUpload filePictureUpload;
    @Autowired
    private UrlPictureUpload urlPictureUpload;
    @Autowired
    private CosManager cosManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AiTagProducer aiTagProducer;

    // 自引用代理，用于 @Async / @Transactional 自调用时确保 AOP 生效
    @Lazy
    @Resource
    private PictureService pictureServiceProxy;
//region
    //更新并没有将原来的图片在阿里云对象存储中删除，而是直接增加了一个图片，但是数据库却还是同一个id，只是url指向了另一个图片
//    @Override
//    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
//        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
//        // 用于判断是新增还是更新图片
//        Long pictureId = null;
//        if (pictureUploadRequest != null) {
//            pictureId = pictureUploadRequest.getId();
//        }
//
//        // 如果是更新图片，需要校验图片是否存在
//        if (pictureId != null) {
//            Picture oldPicture = this.getById(pictureId);
//            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//            // 仅本人或管理员可编辑，既不是本人也不是管理员就会报错
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
//        }
//        // 上传图片，得到信息
//        // 按照用户 id 划分目录
//        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
//        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
//        // 构造要入库的图片信息
//        Picture picture = new Picture();
//        picture.setUrl(uploadPictureResult.getUrl());
//        picture.setName(uploadPictureResult.getPicName());
//        picture.setPicSize(uploadPictureResult.getPicSize());
//        picture.setPicWidth(uploadPictureResult.getPicWidth());
//        picture.setPicHeight(uploadPictureResult.getPicHeight());
//        picture.setPicScale(uploadPictureResult.getPicScale());
//        picture.setPicFormat(uploadPictureResult.getPicFormat());
//        picture.setUserId(loginUser.getId());
//        //补充审核参数
//        fillReviewParams(picture,loginUser);
//        // 如果 pictureId 不为空，表示更新，否则是新增
//        if (pictureId != null) {
//            // 如果是更新，需要补充 id 和编辑时间
//            picture.setId(pictureId);
//            picture.setEditTime(new Date());
//        }
//
//        boolean result = this.saveOrUpdate(picture);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
//        return PictureVO.objToVo(picture);
//    }
//endregion

    //更新并没有将原来的图片在阿里云对象存储中删除，而是直接增加了一个图片，但是数据库却还是同一个id，只是url指向了另一个图片
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 空间权限校验
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 必须空间创建人（管理员）才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        if (inputSource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片为空");
        }
        // 用于判断是新增还是更新图片
        Long pictureId = pictureUploadRequest != null ? pictureUploadRequest.getId() : null;
        // 如果是更新图片，需要校验图片是否存在
        Picture oldPicture = null;
        if (pictureId != null) {
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原有图片一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        //根据inputSource类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        picture.setPicColor(uploadPictureResult.getPicColor());
        //补充审核参数
        fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        Picture finalOldPicture = oldPicture;
        try {
            transactionTemplate.execute(status -> {
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
                if (finalSpaceId != null) {
                    if (pictureId == null) {
                        // 新增图片：原子累加额度并校验上限，避免并发上传超限
                        boolean update = spaceService.lambdaUpdate()
                                .eq(Space::getId, finalSpaceId)
                                .apply("totalSize + {0} <= maxSize AND totalCount + 1 <= maxCount", picture.getPicSize())
                                .setSql("totalSize = totalSize + " + picture.getPicSize())
                                .setSql("totalCount = totalCount + 1")
                                .update();
                        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间额度不足或额度更新失败");
                    } else {
                        // 更新图片：额度按新旧图片大小差值调整，条数不重复累加
                        long oldPicSize = finalOldPicture.getPicSize() == null ? 0L : finalOldPicture.getPicSize();
                        long newPicSize = picture.getPicSize() == null ? 0L : picture.getPicSize();
                        long sizeDiff = newPicSize - oldPicSize;
                        if (sizeDiff != 0) {
                            boolean update = spaceService.lambdaUpdate()
                                    .eq(Space::getId, finalSpaceId)
                                    .setSql("totalSize = totalSize + " + sizeDiff)
                                    .update();
                            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                        }
                    }
                }
                return picture;
            });
        } catch (Exception e) {
            // DB 写入失败，补偿删除本次刚上传到 COS 的文件，避免遗留孤儿文件
            log.error("图片入库失败，清理已上传的 COS 文件", e);
            try {
                if (StrUtil.isNotBlank(picture.getUrl())) {
                    cosManager.deleteObject(new URL(picture.getUrl()).getPath());
                }
                if (StrUtil.isNotBlank(picture.getThumbnailUrl())) {
                    cosManager.deleteObject(new URL(picture.getThumbnailUrl()).getPath());
                }
            } catch (Exception cleanEx) {
                log.error("清理孤儿 COS 文件失败", cleanEx);
            }
            throw e;
        }
        // 单张上传不自动触发 AI 标签，由用户手动调用 /generate_tags 接口
        // 批量抓取模式下由 uploadPictureByBatch 统一触发
//        if (!SKIP_AI_TAG.get()) {
//            aiTagProducer.sendAiTagMessage(picture.getId(), picture.getUrl());
//        }
        return PictureVO.objToVo(picture);
    }


    /**
     * 封装请求条件
     *
     * @param pictureQueryRequest 图片请求类
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        if (StrUtil.isNotEmpty(sortField)) {
            queryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
            // 仍需要 id 作为二级排序
            queryWrapper.orderBy(true, true, "id");
        } else {
            // 默认按创建时间倒序，再按 id 升序（或倒序，自行决定）
            queryWrapper.orderByDesc("createTime");
            queryWrapper.orderByAsc("id");
        }
        // 关键：用主键 id 确保分页的绝对有序
        queryWrapper.orderByAsc("id");
        return queryWrapper;
    }

    /**
     * 返回过滤信息
     *
     * @param picture picture 对象
     * @param request request 请求
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            // 图片上传者可能已被删除（逻辑删除），此时不填充用户信息，避免 getUserVO(null) 抛异常
            if (user != null) {
                pictureVO.setUser(userService.getUserVO(user));
            }
        }
        return pictureVO;
    }

    //基于caffeine和redis的多级缓存
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    // 空值缓存，防止缓存穿透
    private final Cache<String, String> NULL_VALUE_CACHE =
            Caffeine.newBuilder().initialCapacity(512)
                    .maximumSize(5000L)
                    .expireAfterWrite(2L, TimeUnit.MINUTES)
                    .build();

    @Override
    public Page<PictureVO> selectFromCache(String cacheKey) {

        // 从本地缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，返回结果（带泛型反序列化，避免 records 变成 Map）
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue,
                    new TypeReference<Page<PictureVO>>() {
                    }, false);
            return cachedPage;
        }
        // 2. 查询分布式缓存（Redis）
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            // 如果命中 Redis，存入本地缓存并返回
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue,
                    new TypeReference<Page<PictureVO>>() {
                    }, false);
            return cachedPage;
        }
        return null;
    }

    @Override
    public void updateCache(Page<PictureVO> pictureVOPage, String cacheKey) {
        // 4. 更新redis缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 更新本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);
        // 5 - 10 分钟随机过期，防止雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 1. 创建空的 VO 分页对象
        Page<PictureVO> pictureVOPage = new Page<>(
                picturePage.getCurrent(),
                picturePage.getSize(),
                picturePage.getTotal()
        );

        List<Picture> pictureList = picturePage.getRecords();
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 2. Picture → PictureVO 基础转换（不含用户信息）
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 3. 批量查询所有图片关联的用户，得到全部用户id
        Set<Long> userIds = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());
//

        // 查询用户列表，并转为 Map<userId, User>，是怎么实现每个id匹配对应的user， userService.listByIds(userIds) 得到一个user对象列表
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 4. 为每个 PictureVO 填充用户信息（脱敏后）
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = userMap.get(userId);
            if (user != null) {
                pictureVO.setUser(userService.getUserVO(user));
            } else {
                pictureVO.setUser(null); // 或设置一个默认空对象
            }
        });

        // 5. 完成分页数据组装
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 校验传入信息
     *
     * @param picture 需要校验的 picture 对象
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求（可以用来审核未审核图片，也可以用来修改审核状态，比如把同意改为拒绝）
     * @param loginUser            当前登录的 user
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        //如果传递的参数是审核中则抛异常
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态，已经通过不能再传递一个通过
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 设置审核参数，是管理员就自动过审，不是就设置待审核
     *
     * @param picture
     * @param loginUser
     */

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，创建或编辑都要改为待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) throws InterruptedException {
        String searchText = pictureUploadByBatchRequest.getSearchText();

        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        //名称前缀
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        // 收集上传成功的图片 ID 和 URL，用于后续批量 AI 标签生成
        Map<Long, String> uploadedPictures = new LinkedHashMap<>();
        // 批量抓取模式：跳过单张上传时的 AI 触发
        SKIP_AI_TAG.set(true);
        try {
            for (Element imgElement : imgElementList) {
                String fileUrl = imgElement.attr("src");
                if (StrUtil.isBlank(fileUrl)) {
                    log.info("当前链接为空，已跳过: {}", fileUrl);
                    continue;
                }
                // 处理图片上传地址，防止出现转义问题
                int questionMarkIndex = fileUrl.indexOf("?");
                if (questionMarkIndex > -1) {
                    fileUrl = fileUrl.substring(0, questionMarkIndex);
                }
                // 上传图片
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                pictureUploadRequest.setFileUrl(fileUrl);
                if (StrUtil.isNotBlank(namePrefix)) {
                    // 设置图片名称，序号连续递增
                    pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
                }
                try {
                    PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                    log.info("图片上传成功, id = {}", pictureVO.getId());
                    uploadedPictures.put(pictureVO.getId(), pictureVO.getUrl());
                    uploadCount++;
                } catch (Exception e) {
                    log.error("图片上传失败", e);
                    continue;
                }
                if (uploadCount >= count) {
                    break;
                }
            }
        } finally {
            // 确保清除 ThreadLocal 标记
            SKIP_AI_TAG.remove();
        }
        // 批量上传完成后，统一异步触发 AI 标签生成（避免并发超限）
        if (CollUtil.isNotEmpty(uploadedPictures)) {
            log.info("批量上传完成，共 {} 张，开始逐张 AI 标签生成", uploadedPictures.size());
            // 直接使用上传时已获取的 URL，逐张触发 AI（带延迟避免 QPS 超限）
            for (Map.Entry<Long, String> entry : uploadedPictures.entrySet()) {
                aiTagProducer.sendAiTagMessage(entry.getKey(), entry.getValue());
                Thread.sleep(500); // 保留间隔，防止瞬间发太多消息到队列
            }
        }
        return uploadCount;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        try {
            // 提取路径部分
            String picturePath = new URL(pictureUrl).getPath();
            cosManager.deleteObject(picturePath);
            // 清理缩略图
            String thumbnailUrl = oldPicture.getThumbnailUrl();
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                String thumbnailPath = new URL(thumbnailUrl).getPath();
                cosManager.deleteObject(thumbnailPath);
            }
        } catch (MalformedURLException e) {
            log.error("处理图片删除时遇到格式错误的 URL。图片 URL: {}", pictureUrl, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "格式错误的 URL");
        }


    }


    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        //已经改为注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 校验权限
        //已经改为注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 释放额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true;
        });
        // 异步清理文件（通过 AOP 代理调用，确保 @Async 生效）
        PictureService pictureServiceProxy = (PictureService) AopContext.currentProxy();
        pictureServiceProxy.clearPictureFile(oldPicture);
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 校验标签数量（最多 3 个）
        if (CollUtil.isNotEmpty(pictureEditRequest.getTags())) {
            ThrowUtils.throwIf(pictureEditRequest.getTags().size() > 3, ErrorCode.PARAMS_ERROR, "标签最多 3 个");
        }
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 校验权限
        //已经改为注解鉴权
//        checkPictureAuth(loginUser, oldPicture);
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下所有图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                // 取前 12 个
                .limit(12)
                .collect(Collectors.toList());

        // 转换为 PictureVO
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        //得到查询信息
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片，仅选择需要的字段
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        //没查询到对应图片就返回空
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        //判空校验
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }


    /**
     * 获取图片标签和分类（动态从数据库读取，热门标签取 Top10）
     */
    @Override
    public PictureTagCategory listPictureTagCategory() {
        PictureTagCategory result = new PictureTagCategory();

        // 热门标签：先查 Redis 缓存，缓存未命中则从数据库统计
        String cacheKey = HOT_TAGS_REDIS_KEY;
        String cachedTags = stringRedisTemplate.opsForValue().get(cacheKey);
        List<String> tagList;
        if (StrUtil.isNotBlank(cachedTags)) {
            tagList = JSONUtil.toList(cachedTags, String.class);
        } else {
            // 从数据库中读取所有未删除图片的 tags，统计频率取 Top10
            List<Picture> pictures = this.lambdaQuery()
                    .select(Picture::getTags)
                    .eq(Picture::getIsDelete, 0)
                    .isNotNull(Picture::getTags)
                    .ne(Picture::getTags, "[]")
                    .list();
            // 统计标签频率
            Map<String, Long> tagCount = new HashMap<>();
            for (Picture pic : pictures) {
                if (StrUtil.isBlank(pic.getTags())) continue;
                try {
                    List<String> tags = JSONUtil.toList(pic.getTags(), String.class);
                    for (String tag : tags) {
                        if (StrUtil.isNotBlank(tag)) {
                            tagCount.merge(tag, 1L, Long::sum);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            // 按频率降序取 Top10
            tagList = tagCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            // 写入 Redis 缓存，10 分钟过期
            if (CollUtil.isNotEmpty(tagList)) {
                stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(tagList), 10, TimeUnit.MINUTES);
            }
        }
        result.setTagList(tagList);

        // 分类：动态从数据库读取去重分类
        List<String> categoryList = this.lambdaQuery()
                .select(Picture::getCategory)
                .eq(Picture::getIsDelete, 0)
                .isNotNull(Picture::getCategory)
                .ne(Picture::getCategory, "")
                .list().stream()
                .map(Picture::getCategory)
                .distinct()
                .collect(Collectors.toList());
        result.setCategoryList(categoryList);

        return result;
    }


}




