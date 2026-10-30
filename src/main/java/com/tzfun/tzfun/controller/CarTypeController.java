package com.tzfun.tzfun.controller;

import com.tzfun.tzfun.common.Result;
import com.tzfun.tzfun.dto.CarTypeDTO;
import com.tzfun.tzfun.entity.CarType;
import com.tzfun.tzfun.service.CarTypeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 车型接口。
 */
@RestController
@RequestMapping("/carType")
public class CarTypeController {

    @Resource
    private CarTypeService carTypeService;

    @GetMapping("/list")
    public Result<List<CarType>> list() {
        return Result.success(carTypeService.list());
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CarTypeDTO dto) {
        carTypeService.create(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody CarTypeDTO dto) {
        carTypeService.update(dto);
        return Result.success();
    }

    @GetMapping("/availableCount")
    public Result<Integer> availableCount(@RequestParam Long storeId, @RequestParam Long carTypeId) {
        return Result.success(carTypeService.availableCount(storeId, carTypeId));
    }
}
