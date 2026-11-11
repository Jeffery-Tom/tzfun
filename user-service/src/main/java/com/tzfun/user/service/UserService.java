package com.tzfun.user.service;

import com.tzfun.common.CustomException;
import com.tzfun.user.dto.LoginDTO;
import com.tzfun.user.dto.RegisterDTO;
import com.tzfun.user.entity.Role;
import com.tzfun.user.entity.User;
import com.tzfun.user.mapper.UserMapper;
import com.tzfun.common.util.JwtUtil;
import com.tzfun.user.vo.LoginVO;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
    @Resource
    private StringRedisTemplate redis;

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

    /** 登出：把 token 的 jti 写入 Redis 黑名单，TTL = 剩余有效期，网关据此拒绝已登出的 token。 */
    public void logout(String token) {
        Claims claims = jwtUtil.parseToken(token);
        String jti = claims.getId();
        long remainMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (jti != null && remainMs > 0) {
            redis.opsForValue().set("jwt:blacklist:" + jti, "1", Duration.ofMillis(remainMs));
        }
    }
}
