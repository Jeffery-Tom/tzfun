package com.tzfun.tzfun.common;

/**
 * 登录用户上下文（ThreadLocal）。由 LoginInterceptor 写入，请求结束清理。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    /**
     * 校验当前登录用户是否为 ADMIN，否则抛 403。
     */
    public static void requireAdmin() {
        if (!"ADMIN".equals(ROLE.get())) {
            throw new CustomException("403", "无权限");
        }
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }
}
