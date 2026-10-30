package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.CarTypeDTO;
import com.tzfun.tzfun.entity.CarType;
import com.tzfun.tzfun.mapper.CarTypeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 车型 CRUD + 在库车辆数。
 */
@Service
public class CarTypeService {

    @Resource
    private CarTypeMapper carTypeMapper;

    public List<CarType> list() {
        return carTypeMapper.selectAll();
    }

    public void create(CarTypeDTO dto) {
        UserContext.requireAdmin();
        CarType ct = new CarType();
        ct.setName(dto.getName());
        ct.setBrand(dto.getBrand());
        ct.setSeats(dto.getSeats());
        ct.setTransmission(dto.getTransmission());
        ct.setDailyPrice(dto.getDailyPrice());
        ct.setImage(dto.getImage());
        carTypeMapper.insert(ct);
    }

    public void update(CarTypeDTO dto) {
        UserContext.requireAdmin();
        if (dto.getId() == null) {
            throw new CustomException("400", "id 不能为空");
        }
        CarType ct = new CarType();
        ct.setId(dto.getId());
        ct.setName(dto.getName());
        ct.setBrand(dto.getBrand());
        ct.setSeats(dto.getSeats());
        ct.setTransmission(dto.getTransmission());
        ct.setDailyPrice(dto.getDailyPrice());
        ct.setImage(dto.getImage());
        if (carTypeMapper.updateById(ct) != 1) {
            throw new CustomException("404", "车型不存在");
        }
    }

    public int availableCount(Long storeId, Long carTypeId) {
        return carTypeMapper.countAvailable(storeId, carTypeId);
    }
}
