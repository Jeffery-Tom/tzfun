package com.tzfun.car.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆表实体（对应表 car）。
 */
@Data
public class Car {

    private Long id;
    private Long storeId;
    private Long carTypeId;
    private String plateNo;
    private String status;
    private LocalDateTime createTime;
}
