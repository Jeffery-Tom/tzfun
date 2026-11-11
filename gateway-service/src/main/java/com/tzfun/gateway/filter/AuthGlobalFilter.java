package com.tzfun.gateway.filter;

import com.tzfun.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一鉴权：校验 Bearer JWT，通过后把身份覆盖写入 X-User-Id / X-User-Role 头下传，
 * 下游 LoginInterceptor 优先读头恢复 UserContext。白名单直接放行。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private ReactiveStringRedisTemplate redis;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // 网关层路径带 /xxx-service 前缀，用 endsWith 匹配白名单
        if (path.endsWith("/user/login") || path.endsWith("/user/register") || path.endsWith("/pay/notify")) {
            return chain.filter(exchange);
        }
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        try {
            Claims claims = jwtUtil.parseToken(auth.substring(7));
            Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
            String role = String.valueOf(claims.get("role"));
            String jti = claims.getId();
            // 黑名单校验：登出后 jti 已入黑名单，命中则拒绝
            if (jti == null) {
                return forward(exchange, chain, userId, role);
            }
            return redis.opsForValue().get("jwt:blacklist:" + jti)
                    .hasElement()
                    .flatMap(hit -> hit ? unauthorized(exchange) : forward(exchange, chain, userId, role));
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    /** 身份头覆盖写入（防外部伪造）并放行。 */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain, Long userId, String role) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        byte[] bytes = "{\"code\":\"401\",\"msg\":\"未登录或 token 无效\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
