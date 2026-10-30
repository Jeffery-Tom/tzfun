package com.tzfun.tzfun.controller;

import com.tzfun.tzfun.common.Result;
import com.tzfun.tzfun.dto.StoreDTO;
import com.tzfun.tzfun.entity.Store;
import com.tzfun.tzfun.service.StoreService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店接口。
 */
@RestController
@RequestMapping("/store")
public class StoreController {

    @Resource
    private StoreService storeService;

    @GetMapping("/list")
    public Result<List<Store>> list() {
        return Result.success(storeService.list());
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody StoreDTO dto) {
        storeService.create(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody StoreDTO dto) {
        storeService.update(dto);
        return Result.success();
    }
}
