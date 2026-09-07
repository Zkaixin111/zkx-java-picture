package com.zkxpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zkxpicturebackend.model.dto.user.UserEditRequest;
import com.zkxpicturebackend.model.dto.user.UserQueryRequest;
import com.zkxpicturebackend.model.dto.user.UserRegisterRequest;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.vo.LoginUserVO;
import com.zkxpicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


/**
* @author 29486
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-04-09 21:30:29
*/
public interface UserService extends IService<User> {

    //用户注册功能
    Long userRegister(String userAccount,String userPassword, String checkPassword);

    //加密
    String getEncryptPassword(String userPassword);

    //用户登录
    LoginUserVO userLogin(String userAccount, String userPassword , HttpServletRequest request);

    //返回已经脱敏的用户信息

    public LoginUserVO getLoginUserVO(User user);
    /**
     * 获取当前登录用户
     *
     * @param request request
     * @return 当前登录用户
     */
    User getLoginUser(HttpServletRequest request);
    /**
     * 用户注销
     *
     * @param request request
     * @return  注销结果
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取用户脱敏信息
     * @param user 脱敏前的信息
     * @return 脱敏后的信息
     */
    UserVO getUserVO(User user);

    /**
     * 批量获取用户脱敏信息
     * @param userList 脱敏前的信息
     * @return 脱敏后的 List 列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    void editUser(UserEditRequest userEditRequest, User loginUser);



}
