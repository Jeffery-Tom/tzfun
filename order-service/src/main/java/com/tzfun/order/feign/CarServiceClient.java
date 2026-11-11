package com.tzfun.order.feign;

import com.tzfun.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 调 car-service：时间窗库存原子占/释。
 */
@FeignClient(name = "car-service", fallbackFactory = CarServiceClientFallbackFactory.class)
public interface CarServiceClient {

    @PostMapping("/inventory/occupy")
    Result<Boolean> occupy(@RequestParam("storeId") Long storeId,
                           @RequestParam("carTypeId") Long carTypeId,
                           @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                           @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end);

    @PostMapping("/inventory/release")
    Result<Void> release(@RequestParam("storeId") Long storeId,
                         @RequestParam("carTypeId") Long carTypeId,
                         @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                         @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end);
}
