package com.tzfun.tzfun.service;

import com.tzfun.tzfun.common.CustomException;
import com.tzfun.tzfun.common.UserContext;
import com.tzfun.tzfun.dto.StoreDTO;
import com.tzfun.tzfun.entity.Store;
import com.tzfun.tzfun.mapper.StoreMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 门店 CRUD。
 */
@Service
public class StoreService {

    @Resource
    private StoreMapper storeMapper;

    public List<Store> list() {
        return storeMapper.selectAll();
    }

    public void create(StoreDTO dto) {
        UserContext.requireAdmin();
        Store store = new Store();
        store.setName(dto.getName());
        store.setCity(dto.getCity());
        store.setAddress(dto.getAddress());
        store.setLng(dto.getLng());
        store.setLat(dto.getLat());
        storeMapper.insert(store);
    }

    public void update(StoreDTO dto) {
        UserContext.requireAdmin();
        if (dto.getId() == null) {
            throw new CustomException("400", "id 不能为空");
        }
        Store store = new Store();
        store.setId(dto.getId());
        store.setName(dto.getName());
        store.setCity(dto.getCity());
        store.setAddress(dto.getAddress());
        store.setLng(dto.getLng());
        store.setLat(dto.getLat());
        if (storeMapper.updateById(store) != 1) {
            throw new CustomException("404", "门店不存在");
        }
    }
}
