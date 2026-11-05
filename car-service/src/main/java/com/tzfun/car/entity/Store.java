package com.tzfun.car.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店表实体（对应表 store）。
 */
@Data
public class Store {

    private Long id;
    private String name;
    private String city;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private LocalDateTime createTime;
}
