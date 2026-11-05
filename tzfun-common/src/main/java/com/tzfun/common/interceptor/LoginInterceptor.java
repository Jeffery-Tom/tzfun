package com.tzfun.common.interceptor;

import com.tzfun.common.CustomException;
import com.tzfun.common.UserContext;
import com.tzfun.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 token，解析后写入 UserContext。
 * 阶段3 鉴权已前移到网关，本拦截器保留供未走网关直连下游时兜底。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new CustomException("401", "未登录");
        }
        String token = auth.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(String.valueOf(claims.get("userId")));
            String role = String.valueOf(claims.get("role"));
            UserContext.set(userId, role);
            return true;
        } catch (Exception e) {
            throw new CustomException("401", "token 无效或已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
