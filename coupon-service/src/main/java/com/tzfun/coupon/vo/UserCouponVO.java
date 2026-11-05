package com.tzfun.coupon.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的卡包出参（券 + 模板摘要）。
 */
@Data
public class UserCouponVO {

    private Long id;
    private Long templateId;
    private String templateName;
    private String type;
    private BigDecimal value;
    private BigDecimal minSpend;
    private BigDecimal maxDiscount;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
