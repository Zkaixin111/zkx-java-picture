package com.zkxpicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.zkxpicturebackend.model.entity.Picture;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.entity.SpaceUser;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.enums.SpaceRoleEnum;
import com.zkxpicturebackend.model.enums.SpaceTypeEnum;
import com.zkxpicturebackend.service.PictureService;
import com.zkxpicturebackend.service.SpaceService;
import com.zkxpicturebackend.service.SpaceUserService;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.zkxpicturebackend.model.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Slf4j
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;

    /**
     * 返回一个账号所拥有的权限码集合
     * 最终是要拿到一个SpaceRole，而它只在spaceUser中
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断 loginType，仅对类型为 "space" 进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        // 管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        SpaceUserAuthContext authContext = null;
        try {
        // 获取上下文对象
        authContext = getAuthContextByRequest();
        // 获取当前登录用户
        User loginUser = null;
        if (StpKit.SPACE.isLogin()) {
            loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        }
        // 如果所有字段都为空，表示查询公共图库：默认只授予普通用户图片操作权限（不含成员管理），管理员保留全部权限
        if (isAllFieldsNull(authContext)) {
            if (loginUser == null) {
                return new ArrayList<>();
            }
            return userService.isAdmin(loginUser) ? ADMIN_PERMISSIONS
                    : spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.EDITOR.getValue());
        }
        // 获取 userId
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 优先从上下文中获取 SpaceUser 对象，也就是id字段
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 如果有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 取出当前登录用户对应的 spaceUser，不在空间的当前用户是否没有盗用其他人，如果也在空间里也可以强制校准到自己
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                log.warn("用户无空间权限, userId={}, spaceId={}", userId, spaceUser.getSpaceId());
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        if (spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();
            // 图片 id 也没有，则只授予最小查看权限（默认拒绝写操作）
            if (pictureId == null) {
                return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 公共图库，仅本人或管理员可操作
            if (spaceId == null) {
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 获取 Space 对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                log.warn("用户无空间权限, userId={}, spaceId={}", userId, spaceId);
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                log.warn("用户无空间权限, userId={}, spaceId={}", userId, spaceId);
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        } catch (Exception e) {
            log.error("权限计算失败, loginId={}, spaceId={}", loginId, authContext != null ? authContext.getSpaceId() : null, e);
            return new ArrayList<>();
        }
    }

    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }

    /**
     * 本项目不使用,返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {

        return null;
    }

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 从请求中获取上下文对象，确认传递的是spaceId还是pictureId，然后setId
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        //得到请求
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        //获取请求头中的 Content-Type 字段。例如：
        //JSON 请求：application/json
        //表单提交：application/x-www-form-urlencoded
        //GET 请求：没有 body，但可能有查询参数，此时 contentType 可能为 null。
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 get 和 post 操作
        if (ContentType.JSON.getValue().equals(contentType)) {
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

}
