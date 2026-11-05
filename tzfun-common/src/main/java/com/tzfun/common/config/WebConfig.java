package com.tzfun.common.config;

import com.tzfun.common.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器，放行注册/登录（下游服务在鉴权已前移到网关后通常不再启用本拦截器）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                // login/register/notify 白名单；internal/inventory 为服务间 Feign 接口（无登录态，信任网关/服务间头）
                .excludePathPatterns("/user/login", "/user/register", "/pay/notify",
                        "/internal/**", "/inventory/**");
    }
}
