package com.tzfun.coupon.entity;

/**
 * 优惠券类型。type 列存枚举名。
 */
public enum CouponType {

    /** 满减：直接减 value 元。 */
    FULL_REDUCTION,
    /** 折扣：value 为折扣率（0.8=8折），抵扣封顶 max_discount。 */
    DISCOUNT,
    /** 立减：直接减 value 元。 */
    DIRECT
}
