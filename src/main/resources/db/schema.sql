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
