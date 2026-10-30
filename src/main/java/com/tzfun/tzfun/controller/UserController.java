package com.tzfun.tzfun.controller;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.Result;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.LoginDTO;
import com.tzfun.tzfun.dto.RegisterDTO;
import com.tzfun.tzfun.entity.User;
import com.tzfun.tzfun.mapper.UserMapper;
import com.tzfun.tzfun.service.UserService;
import com.tzfun.tzfun.vo.LoginVO;
import com.tzfun.tzfun.vo.UserVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：注册 / 登录 / 当前用户信息。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private UserMapper userMapper;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @GetMapping("/info")
    public Result<UserVO> info() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new CustomException("404", "用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }
}
