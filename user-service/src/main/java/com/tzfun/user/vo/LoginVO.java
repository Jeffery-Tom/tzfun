package com.tzfun.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录出参：token + 角色 + 昵称。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private String role;
    private String name;
}
