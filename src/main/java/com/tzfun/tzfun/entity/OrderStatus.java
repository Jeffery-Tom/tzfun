package com.tzfun.tzfun.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：合法流转表 + 跳步校验。
 */
public enum OrderStatus {

    PENDING, CONFIRMED, IN_USE, RETURNED, SETTLED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING,   EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(IN_USE, CANCELLED),
            IN_USE,    EnumSet.of(RETURNED),
            RETURNED,  EnumSet.of(SETTLED),
            SETTLED,   EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    /** 判断 old -> next 是否合法。 */
    public boolean canTransitTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
