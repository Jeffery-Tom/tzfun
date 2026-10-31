package com.tzfun.tzfun.service;

import com.tzfun.tzfun.entity.Order;
import com.tzfun.tzfun.mapper.CarTypeMapper;
import com.tzfun.tzfun.mapper.OrderMapper;
import jakarta.annotation.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间窗库存：Redis 按天分槽存剩余车数，Lua 原子占/释。
 * Key = inv:{storeId}:{carTypeId}:{date}（date 为 yyyy-MM-dd）。
 */
@Service
public class RedisInventoryService {

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private CarTypeMapper carTypeMapper;
    @Resource
    private OrderMapper orderMapper;

    private static final DefaultRedisScript<Long> OCCUPY_SCRIPT = load("lua/occupy.lua");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = load("lua/release.lua");

    private static DefaultRedisScript<Long> load(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private String key(Long storeId, Long carTypeId, LocalDate date) {
        return "inv:" + storeId + ":" + carTypeId + ":" + date;
    }

    private List<String> expand(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
        List<String> keys = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            keys.add(key(storeId, carTypeId, d));
        }
        return keys;
    }

    /** 初始化单日槽位（不存在才设，NX）。 */
    public void initSlot(Long storeId, Long carTypeId, LocalDate date, int n) {
        redis.opsForValue().setIfAbsent(key(storeId, carTypeId, date), String.valueOf(n));
    }

    /** 单日剩余（前提：已 initSlot；未初始化返回 0）。 */
    public int getAvailable(Long storeId, Long carTypeId, LocalDate date) {
        String v = redis.opsForValue().get(key(storeId, carTypeId, date));
        return v == null ? 0 : Integer.parseInt(v);
    }

    /** 占用 [start,end] 每天 1 辆，检查+扣减原子。 */
    public boolean occupy(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
        List<String> keys = expand(storeId, carTypeId, start, end);
        if (keys.isEmpty()) {
            return false;
        }
        Long r = redis.execute(OCCUPY_SCRIPT, keys, "1");
        return r != null && r == 1L;
    }

    /** 释放 [start,end] 每天 1 辆。 */
    public void release(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
        List<String> keys = expand(storeId, carTypeId, start, end);
        if (keys.isEmpty()) {
            return;
        }
        redis.execute(RELEASE_SCRIPT, keys, "1");
    }

    /** 从 order 表聚合真实占用，重算并覆盖 Redis 槽位（DB 为真相源，兜底重建）。 */
    public void rebuildFromDb(Long storeId, Long carTypeId) {
        int n = carTypeMapper.countAvailable(storeId, carTypeId);
        List<Order> active = orderMapper.selectActive(storeId, carTypeId);
        Map<LocalDate, Integer> occupied = new HashMap<>();
        for (Order o : active) {
            for (LocalDate d = o.getStartTime().toLocalDate(); !d.isAfter(o.getEndTime().toLocalDate()); d = d.plusDays(1)) {
                occupied.merge(d, 1, Integer::sum);
            }
        }
        for (Map.Entry<LocalDate, Integer> e : occupied.entrySet()) {
            int remaining = Math.max(0, n - e.getValue());
            redis.opsForValue().set(key(storeId, carTypeId, e.getKey()), String.valueOf(remaining));
        }
    }
}
