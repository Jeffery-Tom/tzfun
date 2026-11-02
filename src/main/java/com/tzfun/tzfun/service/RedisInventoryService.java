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


    //*expand作用:生成每一天的key存入keys数组*
    //生成redis中的key:inventory:storeId:carTypeId:2026-11-01
    private List<String> expand(Long storeId, Long carTypeId, LocalDate start, LocalDate end) {
        List<String> keys = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            keys.add(key(storeId, carTypeId, d));
        }
        //把租期内每一天的 key 全部放进 keys 数组传给 Lua。
        return keys;
    }

    /** 初始化单日槽位（不存在才设，NX）。 */
    //  key 不存在：写入初始库存 = n        key 已经存在：啥也不干
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
        //expand方法获取redis的key,把租期内每一天的 key 全部放进 keys 数组传给 Lua。
        //expand里已循环生成key
        List<String> keys = expand(storeId, carTypeId, start, end);
        //keys为空占用失败
        if (keys.isEmpty()) {
            return false;
        }
        //keys不为空,  实现lua占用:检查+扣减原子 参数 need=1
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
        // 1. 查询：该门店+车型，总共有多少台实体车
        int n = carTypeMapper.countAvailable(storeId, carTypeId);
        //2. 查询【有效占用订单】：未取消、未过期的活跃订单
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
