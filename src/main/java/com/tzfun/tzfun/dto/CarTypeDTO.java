package com.tzfun.tzfun.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 车型入参：id 为空=新增，有值=修改。
 */
@Data
public class CarTypeDTO {

    private Long id;

    @NotBlank(message = "车型名称不能为空")
    private String name;

    @NotBlank(message = "品牌不能为空")
    private String brand;

    @NotNull(message = "座位数不能为空")
    private Integer seats;

    @NotBlank(message = "变速箱不能为空")
    private String transmission;

    @NotNull(message = "日租金不能为空")
    @DecimalMin(value = "0.01", message = "日租金必须大于0")
    private BigDecimal dailyPrice;

    private String image;
}
