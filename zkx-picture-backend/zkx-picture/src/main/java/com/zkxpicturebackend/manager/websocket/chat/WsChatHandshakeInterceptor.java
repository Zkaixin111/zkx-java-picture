package com.zkxpicturebackend.manager.websocket.chat;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.entity.SpaceUser;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.enums.SpaceTypeEnum;
import com.zkxpicturebackend.service.SpaceService;
import com.zkxpicturebackend.service.SpaceUserService;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 团队空间聊天握手拦截器
 * 校验：带 spaceId + 已登录 + 是空间成员（查 space_user 表）
 */
@Slf4j
@Component
public class WsChatHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 必须带空间 id
            String spaceId = servletRequest.getParameter("spaceId");
            if (StrUtil.isBlank(spaceId)) {
                log.error("缺少空间参数，拒绝握手");
                return false;
            }
            // 必须已登录
            User loginUser = userService.getLoginUser(servletRequest);
            if (loginUser == null) {
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 空间必须存在
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                log.error("空间不存在，拒绝握手");
                return false;
            }
            // 校验：空间创建者、平台管理员、或团队成员（space_user 表有记录）均可进入聊天
            boolean isOwner = space.getUserId() != null && space.getUserId().equals(loginUser.getId());
            boolean isAdmin = userService.isAdmin(loginUser);
            boolean isSpaceMember = false;
            if (space.getSpaceType() != null && space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                Long count = spaceUserService.count(new LambdaQueryWrapper<SpaceUser>()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, loginUser.getId()));
                isSpaceMember = count > 0;
            }
            if (!isOwner && !isAdmin && !isSpaceMember) {
                log.error("不是空间成员，拒绝握手：spaceId={}, userId={}", spaceId, loginUser.getId());
                return false;
            }
            // 校验通过，把身份信息存入 session 属性
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("spaceId", Long.valueOf(spaceId));
        }
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, Exception exception) {
    }
}
