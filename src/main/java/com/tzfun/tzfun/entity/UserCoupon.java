package com.tzfun.tzfun.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户券包表实体（对应表 user_coupon）。
 */
@Data
public class UserCoupon {

    private Long id;
    private Long userId;
    private Long templateId;
    /** 0未用 1已用 2过期。 */
    private Integer status;
    private Long useOrderId;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
