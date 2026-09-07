package com.zkxpicturebackend.conroller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zkxpicturebackend.annotation.AuthCheck;
import com.zkxpicturebackend.common.BaseResponse;
import com.zkxpicturebackend.common.DeleteRequest;
import com.zkxpicturebackend.common.ResultUtils;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import com.zkxpicturebackend.manager.auth.SpaceUserAuthManager;
import com.zkxpicturebackend.model.constant.UserConstant;
import com.zkxpicturebackend.model.dto.picature.PictureEditRequest;
import com.zkxpicturebackend.model.dto.picature.PictureQueryRequest;
import com.zkxpicturebackend.model.dto.space.*;
import com.zkxpicturebackend.model.entity.Picture;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.enums.PictureReviewStatusEnum;
import com.zkxpicturebackend.model.enums.SpaceLevelEnum;
import com.zkxpicturebackend.model.vo.PictureVO;
import com.zkxpicturebackend.model.vo.SpaceVO;
import com.zkxpicturebackend.service.SpaceService;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest,HttpServletRequest request){
        ThrowUtils.throwIf(spaceAddRequest == null,ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        log.info("创建空间, userId={}, spaceName={}", user.getId(), spaceAddRequest.getSpaceName());
        long id = spaceService.addSpace(spaceAddRequest, user);
        return ResultUtils.success(id);
    }

    /**
     * 更新空间信息，主要用于更新空间的等级（管理员使用）
     * @param spaceUpdateRequest 请求类
     * @return 返回是否成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验，空间信息合法（不为空，名字不能过长，传递的级别必须存在）
        spaceService.validSpace(space, false);
        // 判断原空间是否存在，TODO：是否两个校验合并
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        log.info("管理员更新空间, spaceId={}", id);
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除空间,仅本人或者管理员可以操作
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        log.info("删除空间, spaceId={}, userId={}", deleteRequest.getId(), loginUser.getId());
        spaceService.deleteSpaceById(deleteRequest,loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        User loginUser = userService.getLoginUser(request);
        //添加权限列表
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        SpaceVO spaceVO = spaceService.getSpaceVO(space);
        spaceVO.setPermissionList(permissionList);
        // 获取封装类
        return ResultUtils.success(spaceVO);
    }


    /**
     * 根据 id 获取空间（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space Space = spaceService.getById(id);
        ThrowUtils.throwIf(Space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        // 获取封装类
        return ResultUtils.success(Space);
    }
    /**
     * 分页获取空间列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest SpaceQueryRequest) {
        long current = SpaceQueryRequest.getCurrent();
        long size = SpaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> SpacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(SpaceQueryRequest));
        return ResultUtils.success(SpacePage);
    }


    /**
     * 编辑空间（给用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space,false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        spaceService.checkSpaceAuth(loginUser, space);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取空间列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                             HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 权限校验：普通用户仅能查看自己的空间，防止遍历他人私有空间；管理员可查看全部
        User loginUser = userService.getLoginUser(request);
        if (!userService.isAdmin(loginUser)) {
            spaceQueryRequest.setUserId(loginUser.getId());
        }
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));

        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

}
