package com.tzfun.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下单入参。
 */
@Data
public class OrderDTO {

    @NotNull(message = "门店不能为空")
    private Long storeId;

    @NotNull(message = "车型不能为空")
    private Long carTypeId;

    @NotNull(message = "取车时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "还车时间不能为空")
    private LocalDateTime endTime;
}
