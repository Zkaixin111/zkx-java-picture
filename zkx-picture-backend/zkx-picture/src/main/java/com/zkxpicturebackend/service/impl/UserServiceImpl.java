package com.zkxpicturebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import com.zkxpicturebackend.manager.auth.StpKit;
import com.zkxpicturebackend.mapper.UserMapper;
import com.zkxpicturebackend.model.constant.UserConstant;
import com.zkxpicturebackend.model.dto.user.UserEditRequest;
import com.zkxpicturebackend.model.dto.user.UserLoginRequest;
import com.zkxpicturebackend.model.dto.user.UserQueryRequest;
import com.zkxpicturebackend.model.dto.user.UserRegisterRequest;
import com.zkxpicturebackend.model.entity.Picture;
import com.zkxpicturebackend.model.entity.Space;
import com.zkxpicturebackend.model.enums.UserRoleEnum;
import com.zkxpicturebackend.model.vo.LoginUserVO;
import com.zkxpicturebackend.model.vo.UserVO;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.zkxpicturebackend.model.entity.User;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.zkxpicturebackend.model.constant.UserConstant.USER_LOGIN_STATE;

/**
* @author 29486
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2026-04-09 21:30:29
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    /**
     * 用户注册功能
     * @param userAccount 账号
     * @param userPassword 密码
     * @param checkPassword 确认密码
     * @return userId
     */
    @Override
    public Long userRegister(String userAccount,String userPassword, String checkPassword) {
        //对传入的参数进行校验

        //进行非空判断
        if (StrUtil.isBlank(userAccount)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号为空");
        }
        if (StrUtil.isBlank(userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码为空");
        }
        if (StrUtil.isBlank(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"确认密码为空");
        }
        //长度判断
        if (userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码过短");
        }
        //对比密码
        if (!checkPassword.equals(userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入密码不一致");

        }

        //进行数据库查询，是否重名
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getUserAccount,userAccount);
        Long count = this.baseMapper.selectCount(wrapper);
        if (count > 0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号重复");
        }
        //对密码进行加密处理
        String encryptPassword = getEncryptPassword(userPassword);
        //将注册信息插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("游客");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean save = this.save(user);
        if (!save){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"注册失败，数据库异常");
        }
        //返回用户id
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "zkx";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //校验
        if (StrUtil.isBlank(userAccount)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号为空");
        }
        if (StrUtil.isBlank(userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码为空");
        }
        if (userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账号过短");
        }
        if (userPassword.length() < 8 ){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码过短");
        }
        //加密
        String encryptPassword = getEncryptPassword(userPassword);
        //查询数据库
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount,userAccount);
        queryWrapper.eq(User::getUserPassword,encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        //判断是否为空
        if (user == null){
            log.info("userLogin failed,userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"用户不存在");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 4. 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);

    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接返回上述结果）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        // 同步注销 Sa-Token 空间登录态，避免空间鉴权残留
        StpKit.SPACE.logout();
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"待转vo的user为空");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "userList 不能为 null");
        }
        return userList.stream()
                .filter(Objects::nonNull)
                .map(user -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(user, vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"传入参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(Objects.nonNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;

    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public void editUser(UserEditRequest userEditRequest, User loginUser) {
        //得到待修改用户id
        Long userId = userEditRequest.getId();
        //校验请求参数
        ThrowUtils.throwIf(userId <= 0,ErrorCode.PARAMS_ERROR,"用户id不合法");
        //校验待修改的用户是否存在
        User olderUser = this.getById(userId);
        ThrowUtils.throwIf(olderUser == null,ErrorCode.PARAMS_ERROR,"用户不存在");
        // 仅本人或管理员可编辑
        if (!userId.equals(loginUser.getId()) && !this.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //将DTO类转为实体类
        User user = new User();
        BeanUtils.copyProperties(userEditRequest,user);
        //为修改的密码加密
        String olderPassword = userEditRequest.getUserPassword();
        String newPassword = this.getEncryptPassword(olderPassword);
        user.setUserPassword(newPassword);
        //设置编辑时间
        user.setEditTime(new Date());
        boolean result = this.updateById(user);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"数据更新失败");
    }



}




