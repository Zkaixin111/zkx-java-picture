package com.zkxpicturebackend.conroller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zkxpicturebackend.annotation.AuthCheck;
import com.zkxpicturebackend.common.BaseResponse;
import com.zkxpicturebackend.common.DeleteRequest;
import com.zkxpicturebackend.common.ResultUtils;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import com.zkxpicturebackend.model.constant.UserConstant;
import com.zkxpicturebackend.model.dto.user.*;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.vo.LoginUserVO;
import com.zkxpicturebackend.model.vo.UserVO;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.security.PublicKey;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        log.info("用户注册, userAccount={}", userAccount);
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }
    /**
     * 用户登录
     *
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        log.info("用户登录, userAccount={}", userAccount);
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, httpServletRequest);
        return ResultUtils.success(loginUserVO);
    }
    /**
     * 得到用户信息
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }
    /**
     * 用户退出登录
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        log.info("用户注销, userId={}", loginUser.getId());
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }
    /**
     * 创建用户
     *
     * @param userAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        //请求类转为实体类
        BeanUtils.copyProperties(userAddRequest, user);
        //设置默认密码
        final String DEFAULT_PASSWORD = "12345678";
        //加密
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        //存入数据库
        boolean result = userService.save(user);
        //判断操作是否成功
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        //返回用户id
        return ResultUtils.success(user.getId());
    }
    /**
     * 得到用户完整信息（仅管理员使用）
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(user);
    }
//

    /**
     * 得到用户信息（仅用户自己使用）
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        UserVO userVO = userService.getUserVO(user);
        return ResultUtils.success(userVO);
    }
    /**
     * 删除用户
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/remove")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeUser(@RequestBody DeleteRequest deleteRequest) {
        //判断传入参数是否为空
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);
        //得到待删除用户的id
        Long id = deleteRequest.getId();
        //检查数据库中是否存在
        boolean result = userService.removeById(id);
        //判断操作是否成功
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(result);
    }
    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        //判断传入参数是否为空
        ThrowUtils.throwIf(userUpdateRequest == null || userUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        //判断操作是否成功
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }


    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        //输入条件得到包含了用户信息的对象
        // 1. 构建分页对象并执行查询，获取原始用户数据分页结果
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        // 2. 创建用于返回的分页对象，保留总记录数等分页信息
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        //里面的数据需要转化
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }
    /**
     * 编辑用户
     *
     * @param userEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editUser(@RequestBody UserEditRequest userEditRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userEditRequest == null, ErrorCode.PARAMS_ERROR);
        User user = userService.getLoginUser(request);
        userService.editUser(userEditRequest, user);
        return ResultUtils.success(true);
    }
}
