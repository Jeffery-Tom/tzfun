package com.tzfun.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表实体（对应表 user）。
 */
@Data
public class User {

    private Long id;
    private String username;
    private String password;
    private String role;
    private String name;
    private String phone;
    private String licenseNo;
    private String idcardNo;
    private String avatar;
    private LocalDateTime createTime;
}
