package com.tzfun.tzfun.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付回调入参。
 */
@Data
public class PayNotifyDTO {

    @NotNull(message = "商户订单号不能为空")
    private String outTradeNo;

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    /** 可空：支付成功时同时用券。 */
    private Long couponId;
}
