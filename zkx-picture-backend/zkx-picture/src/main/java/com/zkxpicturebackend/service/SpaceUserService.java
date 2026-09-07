package com.zkxpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zkxpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.zkxpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.zkxpicturebackend.model.entity.SpaceUser;
import com.zkxpicturebackend.model.vo.SpaceUserVO;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author 29486
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-04-26 11:19:19
*/

public interface SpaceUserService extends IService<SpaceUser> {
    /**
     * 插入某个空间的某个用户，并附带其权限
     * @param spaceUserAddRequest
     * @return
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 通用校验方法，用于校验spaceUser是否合法，
     * @param spaceUser
     * @param add 用于判断是否是新增空间用户
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 得到查询条件
     * @param spaceUserQueryRequest
     * @return
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 得到单个对象的包装类，增加了空间信息，用户信息
     * @param spaceUser
     * @return
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser);

    /**
     * 得到多个空间对象的返回封装类
     * @param spaceUserList
     * @return 一个列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
