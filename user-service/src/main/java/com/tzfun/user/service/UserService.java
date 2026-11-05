package com.tzfun.user.service;

import com.tzfun.common.CustomException;
import com.tzfun.user.dto.LoginDTO;
import com.tzfun.user.dto.RegisterDTO;
import com.tzfun.user.entity.Role;
import com.tzfun.user.entity.User;
import com.tzfun.user.mapper.UserMapper;
import com.tzfun.common.util.JwtUtil;
import com.tzfun.user.vo.LoginVO;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户注册 / 登录。
 */
@Service
public class UserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private BCryptPasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;

    public void register(RegisterDTO dto) {
        if (userMapper.selectByUsername(dto.getUsername()) != null) {
            throw new CustomException("409", "用户名已注册");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.RENTER.name());
        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：用户名已存在
            throw new CustomException("409", "用户名已注册");
        }
    }

    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new CustomException("401", "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        return new LoginVO(token, user.getRole(), user.getName());
    }
}
