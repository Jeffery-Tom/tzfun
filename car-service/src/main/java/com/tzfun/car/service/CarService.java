package com.tzfun.car.service;

import com.tzfun.common.UserContext;
import com.tzfun.car.entity.Car;
import com.tzfun.car.mapper.CarMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 车辆查询（admin）。
 */
@Service
public class CarService {

    @Resource
    private CarMapper carMapper;

    public List<Car> list(Long storeId, Long carTypeId) {
        UserContext.requireAdmin();
        return carMapper.selectList(storeId, carTypeId);
    }
}
