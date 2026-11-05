package com.tzfun.order.feign;

import com.tzfun.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * 调 car-service：时间窗库存原子占/释。
 */
@FeignClient(name = "car-service")
public interface CarServiceClient {

    @PostMapping("/inventory/occupy")
    Result<Boolean> occupy(@RequestParam("storeId") Long storeId,
                           @RequestParam("carTypeId") Long carTypeId,
                           @RequestParam("start") LocalDate start,
                           @RequestParam("end") LocalDate end);

    @PostMapping("/inventory/release")
    Result<Void> release(@RequestParam("storeId") Long storeId,
                         @RequestParam("carTypeId") Long carTypeId,
                         @RequestParam("start") LocalDate start,
                         @RequestParam("end") LocalDate end);
}
