package com.tzfun.car.controller;

import com.tzfun.common.Result;
import com.tzfun.car.entity.Car;
import com.tzfun.car.service.CarService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 车辆接口。
 */
@RestController
@RequestMapping("/car")
public class CarController {

    @Resource
    private CarService carService;

    @GetMapping("/list")
    public Result<List<Car>> list(@RequestParam(required = false) Long storeId,
                                  @RequestParam(required = false) Long carTypeId) {
        return Result.success(carService.list(storeId, carTypeId));
    }
}
