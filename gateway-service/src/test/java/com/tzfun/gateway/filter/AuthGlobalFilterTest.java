package com.tzfun.gateway.filter;

import com.tzfun.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthGlobalFilter 单元测试：用 MockServerWebExchange 模拟 WebFlux 请求，
 * 不启动 Spring 上下文（无需 Nacos / Redis）。
 * 覆盖：白名单放行、无 token 返回 401、合法 token 注入身份头、非法 token 返回 401。
 */
class AuthGlobalFilterTest {

    @Mock
    JwtUtil jwtUtil;
    @Mock
    GatewayFilterChain chain;
    @Mock
    ReactiveStringRedisTemplate redis;
    @Mock
    ReactiveValueOperations<String, String> valueOps;
    @InjectMocks
    AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void whitelist_login_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-service/user/login").build());
        filter.filter(exchange, chain).block();
        verify(chain).filter(any());
    }

    @Test
    void noAuthorization_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-service/user/info").build());
        filter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validToken_injectsIdentityHeaders() {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(10L);
        when(claims.get("role")).thenReturn("RENTER");
        when(claims.getId()).thenReturn("jti-123");
        when(jwtUtil.parseToken(anyString())).thenReturn(claims);
        when(valueOps.get("jwt:blacklist:jti-123")).thenReturn(Mono.empty());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-service/user/info")
                        .header("Authorization", "Bearer abc.def.ghi").build());
        filter.filter(exchange, chain).block();

        var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        assertEquals("10", captor.getValue().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("RENTER", captor.getValue().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void blacklistedToken_returns401() {
        Claims claims = mock(Claims.class);
        when(claims.get("userId")).thenReturn(10L);
        when(claims.get("role")).thenReturn("RENTER");
        when(claims.getId()).thenReturn("jti-blacklisted");
        when(jwtUtil.parseToken(anyString())).thenReturn(claims);
        when(valueOps.get("jwt:blacklist:jti-blacklisted")).thenReturn(Mono.just("1"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-service/user/info")
                        .header("Authorization", "Bearer abc.def.ghi").build());
        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void invalidToken_returns401() {
        when(jwtUtil.parseToken(anyString())).thenThrow(new RuntimeException("bad"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/user-service/user/info")
                        .header("Authorization", "Bearer bad.token").build());
        filter.filter(exchange, chain).block();
        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
