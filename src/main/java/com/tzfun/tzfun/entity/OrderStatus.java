package com.tzfun.tzfun.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：合法流转表 + 跳步校验。
 */
public enum OrderStatus {

    PENDING, CONFIRMED, IN_USE, RETURNED, SETTLED, CANCELLED;

    //key = 当前状态，value = 允许跳到的状态集合
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

        //这一步就是拿到：当前状态允许流转到哪些状态的集合
        //举个栗子:this 是 PENDING，拿到的集合就是 `{CONFIRMED, CANCELLED}`
        //`next` 是你想要改成的目标状态
        return ALLOWED.get(this).contains(next);
        //这个ALLOWED是一个map,map.get(key) 这个是拿到map里的值
    }
}
