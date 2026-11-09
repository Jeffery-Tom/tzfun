package com.tzfun.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：纯逻辑，不依赖 Spring 上下文。
 * 覆盖：签发+解析往返、不同密钥互不可解、非法串解析抛异常。
 */
class JwtUtilTest {

    // HS256 要求密钥 >= 32 字节
    private final JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef", 3600_000L);

    @Test
    void generateAndParse_roundTrip_returnsClaims() {
        String token = jwtUtil.generateToken(10L, "RENTER");
        Claims claims = jwtUtil.parseToken(token);
        // 注意：JJWT 把落在 int 范围内的数字反序列化为 Integer，生产代码用
        // Long.valueOf(String.valueOf(...)) 兼容，这里保持同样写法。
        assertEquals(10L, Long.valueOf(String.valueOf(claims.get("userId"))));
        assertEquals("RENTER", claims.get("role"));
    }

    @Test
    void parseToken_wrongSecret_throws() {
        // 用另一把密钥签发，再用本实例解析 —— 签名校验必然失败
        JwtUtil other = new JwtUtil("abcdef0123456789abcdef0123456789", 3600_000L);
        String token = other.generateToken(10L, "RENTER");
        assertThrows(Exception.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    void parseToken_garbage_throws() {
        assertThrows(Exception.class, () -> jwtUtil.parseToken("not-a-valid-token"));
    }
}
