package com.tzfun.tzfun.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体（对应表 payment）。
 */
@Data
public class Payment {

    private Long id;
    private Long orderId;
    /** 商户订单号=幂等键。 */
    private String outTradeNo;
    private BigDecimal amount;
    private String channel;
    /** 未支付/已支付/失败/退款。 */
    private String status;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
}
