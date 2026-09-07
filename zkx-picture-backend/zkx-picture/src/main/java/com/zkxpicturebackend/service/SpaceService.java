package com.zkxpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zkxpicturebackend.common.DeleteRequest;
import com.zkxpicturebackend.model.dto.space.SpaceAddRequest;
import com.zkxpicturebackend.model.dto.space.SpaceQueryRequest;
import com.zkxpicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.vo.SpaceVO;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
* @author 29486
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-04-15 21:32:19
*/


public interface SpaceService extends IService<Space> {

     void deleteSpaceById(DeleteRequest deleteRequest, User loginUser);

    void validSpace(Space space, boolean add);

    void fillSpaceBySpaceLevel(Space space);

    /**
     * 获取查询对象
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 返回封装类
     * @param space

     * @return
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 获取图片包装类（分页）
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 创建空间
     * @param spaceAddRequest
     * @param userlogin
     * @return
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User userlogin);

    void checkSpaceAuth(User loginUser, Space space);
}
