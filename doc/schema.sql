-- ============================================================
-- TzFun 租车平台 · 数据库初始化脚本（汇总版，开箱即用）
-- 适用于 MySQL 8（InnoDB + utf8mb4）。在目标库执行 `mysql -u <user> -p <dbname> < doc/schema.sql` 即可。
-- 表已按业务依赖排序；金额统一 DECIMAL(10,2)，状态/类型用 VARCHAR 枚举。
-- ============================================================

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密',
    role VARCHAR(20) NOT NULL COMMENT 'RENTER/ADMIN',
    name VARCHAR(50) COMMENT '姓名',
    phone VARCHAR(20) COMMENT '手机号',
    license_no VARCHAR(50) COMMENT '驾照号(mock)',
    idcard_no VARCHAR(50) COMMENT '身份证号(mock)',
    avatar VARCHAR(255) COMMENT '头像',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE IF NOT EXISTS store (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '门店名',
    city VARCHAR(50) COMMENT '城市',
    address VARCHAR(255) COMMENT '地址',
    lng DECIMAL(10,7) COMMENT '经度',
    lat DECIMAL(10,7) COMMENT '纬度',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店';

CREATE TABLE IF NOT EXISTS car_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) COMMENT '车型名',
    brand VARCHAR(50) COMMENT '品牌',
    seats INT COMMENT '座位数',
    transmission VARCHAR(20) COMMENT '自动/手动',
    daily_price DECIMAL(10,2) NOT NULL COMMENT '日租价',
    image VARCHAR(255) COMMENT '图片',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车型';

CREATE TABLE IF NOT EXISTS car (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    store_id BIGINT NOT NULL COMMENT '所属门店',
    car_type_id BIGINT NOT NULL COMMENT '车型',
    plate_no VARCHAR(20) COMMENT '车牌',
    status VARCHAR(20) NOT NULL DEFAULT '在库' COMMENT '在库/出租中/维护',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plate_no (plate_no),
    KEY idx_store (store_id),
    KEY idx_car_type (car_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆';

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '订单号(唯一)',
    user_id BIGINT NOT NULL COMMENT '用户',
    store_id BIGINT NOT NULL COMMENT '门店',
    car_type_id BIGINT NOT NULL COMMENT '车型',
    car_id BIGINT COMMENT '分配的具体车辆(取车时填)',
    start_time DATETIME NOT NULL COMMENT '用车开始',
    end_time DATETIME NOT NULL COMMENT '用车结束',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/CONFIRMED/IN_USE/RETURNED/SETTLED/CANCELLED',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总额',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    pay_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    coupon_id BIGINT COMMENT '使用的券',
    pickup_info VARCHAR(500) COMMENT '取车信息',
    return_info VARCHAR(500) COMMENT '还车信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user (user_id),
    KEY idx_store_type (store_id, car_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

CREATE TABLE IF NOT EXISTS coupon_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '券名',
    type VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCTION满减/DISCOUNT折扣/DIRECT立减',
    value DECIMAL(10,2) NOT NULL COMMENT '满减/立减金额,或折扣率(0.8=8折)',
    min_spend DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '最低消费门槛',
    max_discount DECIMAL(10,2) DEFAULT NULL COMMENT '折扣封顶金额(仅折扣用)',
    scope_city VARCHAR(50) DEFAULT NULL COMMENT '适用城市',
    scope_store BIGINT DEFAULT NULL COMMENT '适用门店id',
    scope_type BIGINT DEFAULT NULL COMMENT '适用车型id',
    total_count INT NOT NULL COMMENT '发放总量',
    claimed_count INT NOT NULL DEFAULT 0 COMMENT '已领取数',
    per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每人限领数',
    claim_start DATETIME NOT NULL COMMENT '领取开始',
    claim_end DATETIME NOT NULL COMMENT '领取结束',
    use_start DATETIME NOT NULL COMMENT '可用开始',
    use_end DATETIME NOT NULL COMMENT '可用结束(过期时间)',
    seckill TINYINT NOT NULL DEFAULT 0 COMMENT '0普通 1秒杀',
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

-- 示例基础数据（可选，便于直接体验选车 / 下单 / 优惠券）
INSERT INTO store(name, city, address, lng, lat) VALUES
    ('上海人民广场店', '上海', '黄浦区人民大道 1 号', 121.4737000, 31.2304000),
    ('北京朝阳门店', '北京', '朝阳区朝阳门外大街 1 号', 116.4436000, 39.9219000);

INSERT INTO car_type(name, brand, seats, transmission, daily_price) VALUES
    ('朗逸 1.5L 自动', '大众', 5, '自动', 200.00),
    ('Model 3', '特斯拉', 5, '自动', 500.00);

INSERT INTO car(store_id, car_type_id, plate_no, status) VALUES
    (1, 1, '沪A10001', '在库'),
    (1, 1, '沪A10002', '在库'),
    (1, 1, '沪A10003', '出租中'),
    (1, 2, '沪A20001', '在库'),
    (2, 1, '京A10001', '在库'),
    (2, 1, '京A10002', '维护');

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
