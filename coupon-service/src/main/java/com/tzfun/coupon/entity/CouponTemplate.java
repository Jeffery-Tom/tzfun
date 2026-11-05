package com.tzfun.coupon.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板表实体（对应表 coupon_template）。
 */
@Data
public class CouponTemplate {

    private Long id;
    private String name;
    /** 类型：FULL_REDUCTION / DISCOUNT / DIRECT。 */
    private String type;
    /** 满减/立减金额，或折扣率（0.8=8折）。 */
    private BigDecimal value;
    private BigDecimal minSpend;
    private BigDecimal maxDiscount;
    private String scopeCity;
    private Long scopeStore;
    private Long scopeType;
    private Integer totalCount;
    private Integer claimedCount;
    private Integer perUserLimit;
    private LocalDateTime claimStart;
    private LocalDateTime claimEnd;
    private LocalDateTime useStart;
    private LocalDateTime useEnd;
    /** 1上架 0下架。 */
    private Integer status;
    /** 0普通 1秒杀。 */
    private Integer seckill;
    private LocalDateTime createTime;
}
