
CREATE TABLE IF NOT EXISTS payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '订单id',
    out_trade_no VARCHAR(64) NOT NULL COMMENT '商户订单号=幂等键',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    channel VARCHAR(20) DEFAULT 'mock' COMMENT '支付渠道',
    status VARCHAR(20) NOT NULL COMMENT '未支付/已支付/失败/退款',
    pay_time DATETIME COMMENT '支付时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_out_trade_no (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';
