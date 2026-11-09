package com.tzfun.user.service;

import com.tzfun.common.CustomException;
import com.tzfun.common.util.JwtUtil;
import com.tzfun.user.dto.LoginDTO;
import com.tzfun.user.dto.RegisterDTO;
import com.tzfun.user.entity.User;
import com.tzfun.user.mapper.UserMapper;
import com.tzfun.user.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试：Mockito 注入依赖，不启动 Spring 上下文。
 * 覆盖：登录成功返回 token、密码错误抛异常、注册新用户落库且密码被 BCrypt 加密。
 */
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @InjectMocks UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void login_success_returnsToken() {
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setPassword("$2a$encoded");
        user.setRole("RENTER");
        user.setName("小明");
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$encoded")).thenReturn(true);
        when(jwtUtil.generateToken(10L, "RENTER")).thenReturn("token-xyz");

        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("123456");
        LoginVO vo = userService.login(dto);
        assertEquals("token-xyz", vo.getToken());
        assertEquals("RENTER", vo.getRole());
    }

    @Test
    void login_wrongPassword_throws() {
        User user = new User();
        user.setPassword("$2a$encoded");
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$encoded")).thenReturn(false);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword("wrong");
        assertThrows(CustomException.class, () -> userService.login(dto));
    }

    @Test
    void register_newUser_insertsWithEncodedPassword() {
        when(userMapper.selectByUsername("bob")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$new");

        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("bob");
        dto.setPassword("123456");
        dto.setName("小Bob");
        dto.setPhone("13800000000");
        userService.register(dto);

        verify(userMapper).insert(argThat((User u) ->
                "bob".equals(u.getUsername())
                && "$2a$new".equals(u.getPassword())
                && "RENTER".equals(u.getRole())));
    }
}
