package com.zkxpicturebackend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zkxpicturebackend.model.dto.user.UserQueryRequest;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.model.vo.UserVO;
import com.zkxpicturebackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
public class PagingQueryTest {

//    @Resource
//    private UserService userService;
//
//    @Test
//    public void testPagingQuery() {
//        UserQueryRequest userQueryRequest = new UserQueryRequest();
////        填充完整的的查询条件
//        userQueryRequest.setCurrent(1);
//        userQueryRequest.setPageSize(10);
//        userQueryRequest.setSortField("id");
//        userQueryRequest.setSortOrder("descend");
//
//        userQueryRequest.setUserAccount("测试用户");
//        userQueryRequest.setUserRole("user");
//
////        获取条件
//        int current = userQueryRequest.getCurrent();
//        int pageSize = userQueryRequest.getPageSize();
//
////        构造分页对象
//        Page<User> userPage = new Page<>(current, pageSize);
////        构造查询条件
//        LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
//        userLambdaQueryWrapper
//                .like(User::getUserAccount, userQueryRequest.getUserAccount())
//                .eq(User::getUserRole, userQueryRequest.getUserRole());
////       执行分页查询
//        Page<User> userPageResult = userService.page(userPage, userLambdaQueryWrapper);
//
////      需要封装返回结果
//        List<UserVO> voList = userPageResult.getRecords().stream()
//                .map(user -> {
//                    UserVO vo = new UserVO();
//                    BeanUtils.copyProperties(user, vo); // 复制同名属性
//                    return vo;
//                })
//                .collect(Collectors.toList());
//
//        System.out.println(voList);
//    }
}
