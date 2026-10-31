package com.tzfun.tzfun.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 选车入参：按门店/城市 + 日期段筛选可用车型。
 */
@Data
public class AvailableDTO {

    private String city;
    private Long storeId;
    private Long carTypeId;

    @NotNull(message = "开始日期不能为空")
    private LocalDate start;

    @NotNull(message = "结束日期不能为空")
    private LocalDate end;

    private BigDecimal priceMin;
    private BigDecimal priceMax;
}
