package com.tzfun.tzfun.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 选车出参：某门店某车型在该时段的可用情况。
 */
@Data
public class AvailableVO {

    private Long storeId;
    private Long carTypeId;
    private String name;
    private String brand;
    private BigDecimal dailyPrice;
    private Integer available;
}
