package com.tzfun.tzfun.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参，不含 password。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String role;
    private String name;
    private String phone;
    private String licenseNo;
    private String idcardNo;
    private String avatar;
    private LocalDateTime createTime;
}
