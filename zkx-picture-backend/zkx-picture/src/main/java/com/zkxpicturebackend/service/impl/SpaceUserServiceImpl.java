package com.zkxpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import com.zkxpicturebackend.mapper.SpaceMapper;
import com.zkxpicturebackend.mapper.SpaceUserMapper;
import com.zkxpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.zkxpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.entity.SpaceUser;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.enums.SpaceRoleEnum;
import com.zkxpicturebackend.model.vo.SpaceUserVO;
import com.zkxpicturebackend.model.vo.SpaceVO;
import com.zkxpicturebackend.model.vo.UserVO;
import com.zkxpicturebackend.service.SpaceService;
import com.zkxpicturebackend.service.SpaceUserService;

import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 29486
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
 * @createDate 2026-04-26 11:19:19
 */
@Slf4j
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {

    @Resource
    private UserService userService;
    @Resource
    @Lazy
    private SpaceService spaceService;



    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        log.info("添加空间成员, spaceId={}, userId={}, role={}", spaceUser.getSpaceId(), spaceUser.getUserId(), spaceUser.getSpaceRole());
        // 数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联查询用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            // 成员可能已被删除（逻辑删除），此时不填充用户信息，避免 getUserVO(null) 抛异常
            if (user != null) {
                spaceUserVO.setUser(userService.getUserVO(user));
            }
        }
        // 关联查询空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }

    //让列表方法调用单个方法，但是缺点：假设列表有 N 个元素，就会执行 N 次 userService.getById 和 N 次 spaceService.getById
    //所以需要在代码中解决
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        //得到一个封装列表
        List<SpaceUserVO> spaceUserVOS = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        //得到全部的spaceId
        Set<Long> spaceIds = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        //得到全部的UserId
        Set<Long> userIds = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());

        //批量查询user和space
        Map<Long, User> userMap = userService.listByIds(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Space> spaceMap = spaceService.listByIds(spaceIds).stream().collect(Collectors.toMap(Space::getId, space -> space));
        //遍历spaceUserVOS依次注入user，space
        spaceUserVOS.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();

            if (userId != null) {
                User user = userMap.get(userId);
                if (user != null) {
                    spaceUserVO.setUser(userService.getUserVO(user));
                }
            }

            if (spaceId != null) {
                Space space = spaceMap.get(spaceId);
                if (space != null) {
                    spaceUserVO.setSpace(spaceService.getSpaceVO(space));
                }
            }
        });
        return spaceUserVOS;
    }

}




