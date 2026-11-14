package com.tzfun.common.config;

import com.tzfun.common.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册登录拦截器，放行注册/登录、服务间 Feign 内部接口，以及 Swagger/OpenAPI 文档路径。
 * 本配置被各 webmvc 微服务通过依赖 tzfun-common 共用，因此 Swagger 白名单只需在此处维护一处。
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
                // swagger-ui / v3/api-docs / swagger-resources / webjars 为 springdoc-openapi 文档资源，免登录放行
                .excludePathPatterns("/user/login", "/user/register", "/pay/notify",
                        "/internal/**", "/inventory/**",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                        "/swagger-resources/**", "/webjars/**");
    }
}
