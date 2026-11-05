package com.tzfun.car.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店入参：id 为空=新增，有值=修改。
 */
@Data
public class StoreDTO {

    private Long id;

    @NotBlank(message = "门店名称不能为空")
    private String name;

    @NotBlank(message = "城市不能为空")
    private String city;

    private String address;

    private BigDecimal lng;

    private BigDecimal lat;
}
