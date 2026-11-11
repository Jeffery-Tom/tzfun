package com.tzfun.car.controller;

import com.tzfun.common.Result;
import com.tzfun.car.mapper.CarTypeMapper;
import com.tzfun.car.service.RedisInventoryService;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 时间窗库存内部接口（供 order-service Feign 调用）。
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {

    @Resource
    private RedisInventoryService inventoryService;
    @Resource
    private CarTypeMapper carTypeMapper;

    @PostMapping("/occupy")
    public Result<Boolean> occupy(@RequestParam Long storeId,
                                  @RequestParam Long carTypeId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                  @RequestParam String requestId) {
        int n = carTypeMapper.countAvailable(storeId, carTypeId);
        if (n <= 0) {
            return Result.success(false);
        }
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            inventoryService.initSlot(storeId, carTypeId, d, n);
        }
        return Result.success(inventoryService.occupy(storeId, carTypeId, start, end, requestId));
    }

    @PostMapping("/release")
    public Result<Void> release(@RequestParam Long storeId,
                                @RequestParam Long carTypeId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        inventoryService.release(storeId, carTypeId, start, end);
        return Result.success();
    }
}
