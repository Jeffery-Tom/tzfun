
CREATE TABLE IF NOT EXISTS coupon_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '券名',
    type VARCHAR(20) NOT NULL COMMENT '类型: FULL_REDUCTION满减 / DISCOUNT折扣 / DIRECT立减',
    value DECIMAL(10,2) NOT NULL COMMENT '满减/立减金额，或折扣率(0.8=8折)',
    min_spend DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '最低消费门槛',
    max_discount DECIMAL(10,2) DEFAULT NULL COMMENT '折扣封顶金额(仅折扣用)',
    scope_city VARCHAR(50) DEFAULT NULL COMMENT '适用城市',
    scope_store BIGINT DEFAULT NULL COMMENT '适用门店id',
    scope_type BIGINT DEFAULT NULL COMMENT '适用车型id',
    total_count INT NOT NULL COMMENT '发放总量',
    claimed_count INT NOT NULL DEFAULT 0 COMMENT '已领取数',
    per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每人限领数',
    claim_start DATETIME NOT NULL COMMENT '领取开始时间',
    claim_end DATETIME NOT NULL COMMENT '领取结束时间',
    use_start DATETIME NOT NULL COMMENT '可用开始时间',
    use_end DATETIME NOT NULL COMMENT '可用结束时间(券过期时间)',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS user_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户id',
    template_id BIGINT NOT NULL COMMENT '模板id',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0未用 1已用 2过期',
    use_order_id BIGINT DEFAULT NULL COMMENT '使用的订单id',
    expire_time DATETIME NOT NULL COMMENT '过期时间(取模板use_end)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_template (user_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券包';

-- 自测用示例数据（可选）
INSERT INTO coupon_template
    (name, type, value, min_spend, max_discount, total_count, claimed_count, per_user_limit,
     claim_start, claim_end, use_start, use_end, status)
VALUES
    ('满减10元', 'FULL_REDUCTION', 10.00, 100.00, NULL, 2, 0, 1,
     '2026-01-01 00:00:00', '2030-01-01 00:00:00', '2026-01-01 00:00:00', '2030-01-01 00:00:00', 1),
    ('8折券', 'DISCOUNT', 0.80, 50.00, 20.00, 100, 0, 1,
     '2026-01-01 00:00:00', '2030-01-01 00:00:00', '2026-01-01 00:00:00', '2030-01-01 00:00:00', 1),
    ('立减5元', 'DIRECT', 5.00, 0.00, NULL, 100, 0, 1,
     '2026-01-01 00:00:00', '2030-01-01 00:00:00', '2026-01-01 00:00:00', '2030-01-01 00:00:00', 1);

-- ============ 阶段2：优惠券秒杀 ============
-- coupon_template 加 seckill 标记（0普通 1秒杀）
ALTER TABLE coupon_template ADD COLUMN seckill TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1秒杀';
-- 说明：user_coupon 的 uk_user_template(user_id, template_id) 唯一索引已存在（见上建表），秒杀兜底复用，无需新增。
