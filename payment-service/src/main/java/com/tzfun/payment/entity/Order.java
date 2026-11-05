package com.tzfun.payment.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表实体（对应表 orders，模块4 完整字段）。
 */
@Data
public class Order {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long storeId;
    private Long carTypeId;
    private Long carId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payAmount;
    private Long couponId;
    private String pickupInfo;
    private String returnInfo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
