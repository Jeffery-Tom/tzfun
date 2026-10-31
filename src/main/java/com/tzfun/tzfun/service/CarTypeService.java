package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.AvailableDTO;
import com.tzfun.tzfun.dto.CarTypeDTO;
import com.tzfun.tzfun.entity.CarType;
import com.tzfun.tzfun.entity.Store;
import com.tzfun.tzfun.mapper.CarTypeMapper;
import com.tzfun.tzfun.mapper.StoreMapper;
import com.tzfun.tzfun.vo.AvailableVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 车型 CRUD + 在库车辆数 + 选车（时间窗库存过滤）。
 */
@Service
public class CarTypeService {

    @Resource
    private CarTypeMapper carTypeMapper;
    @Resource
    private StoreMapper storeMapper;
    @Resource
    private RedisInventoryService inventoryService;

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

    /** 选车：按门店/城市 + 日期段，过滤已满日期，返回每天仍可用的车型。 */
    public List<AvailableVO> available(AvailableDTO dto) {
        if (dto.getStart().isAfter(dto.getEnd())) {
            throw new CustomException("400", "开始日期不能晚于结束日期");
        }
        List<Store> stores = resolveStores(dto);
        List<CarType> types = resolveTypes(dto);
        List<AvailableVO> result = new ArrayList<>();
        for (Store store : stores) {
            for (CarType type : types) {
                if (dto.getPriceMin() != null && type.getDailyPrice().compareTo(dto.getPriceMin()) < 0) {
                    continue;
                }
                if (dto.getPriceMax() != null && type.getDailyPrice().compareTo(dto.getPriceMax()) > 0) {
                    continue;
                }
                int n = carTypeMapper.countAvailable(store.getId(), type.getId());
                if (n <= 0) {
                    continue;
                }
                int min = Integer.MAX_VALUE;
                for (LocalDate d = dto.getStart(); !d.isAfter(dto.getEnd()); d = d.plusDays(1)) {
                    inventoryService.initSlot(store.getId(), type.getId(), d, n);
                    min = Math.min(min, inventoryService.getAvailable(store.getId(), type.getId(), d));
                }
                if (min >= 1) {
                    AvailableVO vo = new AvailableVO();
                    vo.setStoreId(store.getId());
                    vo.setCarTypeId(type.getId());
                    vo.setName(type.getName());
                    vo.setBrand(type.getBrand());
                    vo.setDailyPrice(type.getDailyPrice());
                    vo.setAvailable(min);
                    result.add(vo);
                }
            }
        }
        return result;
    }

    private List<Store> resolveStores(AvailableDTO dto) {
        if (dto.getStoreId() != null) {
            Store s = storeMapper.selectById(dto.getStoreId());
            if (s == null) {
                throw new CustomException("404", "门店不存在");
            }
            return List.of(s);
        }
        if (dto.getCity() != null && !dto.getCity().isBlank()) {
            return storeMapper.selectByCity(dto.getCity());
        }
        throw new CustomException("400", "storeId 或 city 至少填一个");
    }

    private List<CarType> resolveTypes(AvailableDTO dto) {
        if (dto.getCarTypeId() != null) {
            CarType t = carTypeMapper.selectById(dto.getCarTypeId());
            if (t == null) {
                throw new CustomException("404", "车型不存在");
            }
            return List.of(t);
        }
        return carTypeMapper.selectAll();
    }
}
