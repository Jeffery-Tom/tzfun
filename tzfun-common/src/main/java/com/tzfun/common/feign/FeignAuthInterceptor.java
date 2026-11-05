package com.tzfun.common.feign;

import com.tzfun.common.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：服务间调用时自动把当前登录身份写入 X-User-Id / X-User-Role 头，
 * 避免下游因无登录态丢失 UserContext。仅当 classpath 存在 feign 时才装配。
 */
@Component
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignAuthInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getUserId();
        String role = UserContext.getRole();
        if (userId != null) {
            template.header("X-User-Id", String.valueOf(userId));
        }
        if (role != null) {
            template.header("X-User-Role", role);
        }
    }
}
