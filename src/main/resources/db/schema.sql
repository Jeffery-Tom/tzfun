-- TzFun 模块1：用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
    `role`        VARCHAR(20)  NOT NULL COMMENT 'RENTER/ADMIN',
    `name`        VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `license_no`  VARCHAR(50)  DEFAULT NULL COMMENT '驾照号,mock',
    `idcard_no`   VARCHAR(50)  DEFAULT NULL COMMENT '身份证号,mock',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- TzFun 模块2：门店表
CREATE TABLE IF NOT EXISTS `store` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100)  NOT NULL COMMENT '门店名称',
    `city`        VARCHAR(50)   NOT NULL COMMENT '城市',
    `address`     VARCHAR(255)  DEFAULT NULL COMMENT '地址',
    `lng`         DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
    `lat`         DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='门店表';

-- TzFun 模块2：车型表
CREATE TABLE IF NOT EXISTS `car_type` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`         VARCHAR(100)  NOT NULL COMMENT '车型名称',
    `brand`        VARCHAR(50)   NOT NULL COMMENT '品牌',
    `seats`        INT           DEFAULT NULL COMMENT '座位数',
    `transmission` VARCHAR(20)   DEFAULT NULL COMMENT '自动/手动',
    `daily_price`  DECIMAL(10,2) NOT NULL COMMENT '日租金',
    `image`        VARCHAR(255)  DEFAULT NULL COMMENT '图片',
    `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='车型表';

-- TzFun 模块2：车辆表
CREATE TABLE IF NOT EXISTS `car` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `store_id`    BIGINT       NOT NULL COMMENT '门店id',
    `car_type_id` BIGINT       NOT NULL COMMENT '车型id',
    `plate_no`    VARCHAR(20)  NOT NULL COMMENT '车牌号',
    `status`      VARCHAR(20)  NOT NULL DEFAULT '在库' COMMENT '在库/出租中/维护',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_store` (`store_id`),
    KEY `idx_car_type` (`car_type_id`),
    UNIQUE KEY `uk_plate_no` (`plate_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='车辆表';

-- 模块2 种子数据（幂等，可重复执行）
INSERT INTO `store`(`id`, `name`, `city`, `address`, `lng`, `lat`, `create_time`) VALUES
(1, '上海人民广场店', '上海', '黄浦区人民大道 1 号', 121.4737000, 31.2304000, NOW()),
(2, '北京朝阳门店', '北京', '朝阳区朝阳门外大街 1 号', 116.4436000, 39.9219000, NOW())
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO `car_type`(`id`, `name`, `brand`, `seats`, `transmission`, `daily_price`, `image`, `create_time`) VALUES
(1, '朗逸 1.5L 自动', '大众', 5, '自动', 200.00, NULL, NOW()),
(2, 'Model 3', '特斯拉', 5, '自动', 500.00, NULL, NOW())
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO `car`(`id`, `store_id`, `car_type_id`, `plate_no`, `status`, `create_time`) VALUES
(1, 1, 1, '沪A10001', '在库', NOW()),
(2, 1, 1, '沪A10002', '在库', NOW()),
(3, 1, 1, '沪A10003', '出租中', NOW()),
(4, 1, 2, '沪A20001', '在库', NOW()),
(5, 2, 1, '京A10001', '在库', NOW()),
(6, 2, 1, '京A10002', '维护', NOW())
ON DUPLICATE KEY UPDATE id = id;
