package com.tzfun.tzfun.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车型表实体（对应表 car_type）。
 */
@Data
public class CarType {

    private Long id;
    private String name;
    private String brand;
    private Integer seats;
    private String transmission;
    private BigDecimal dailyPrice;
    private String image;
    private LocalDateTime createTime;
}
