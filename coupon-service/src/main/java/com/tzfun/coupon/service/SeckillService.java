package com.tzfun.coupon.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.tzfun.common.CustomException;
import com.tzfun.common.mq.MqProducer;
import com.tzfun.coupon.entity.CouponTemplate;
import com.tzfun.coupon.mapper.CouponTemplateMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券秒杀：库存分段(10 段) + Redis Lua 预扣(防超卖) + MQ 异步落库(唯一索引兜底)。
 * 分段打散 Redis 单 key 热点；落库失败按段号回滚预扣。
 */
@Slf4j
@Service
public class SeckillService {

    /** 库存分段数。 */
    private static final int SEG = 10;

    /** 秒杀模板缓存 TTL（秒）。 */
    private static final long TEMPLATE_CACHE_SECONDS = 300;
    /** 空值缓存 TTL（秒）：查库不存在时短缓存，防穿透。 */
    private static final long EMPTY_CACHE_SECONDS = 60;
    /** 缓存 TTL 随机抖动上限（秒）：防雪崩。 */
    private static final long TTL_JITTER_SECONDS = 120;
    /** 空值缓存标记字段。 */
    private static final String EMPTY_FIELD = "empty";
    /** 本地缓存 TTL（秒）：秒杀券详情读多写少，短 TTL 控制下架/改库存的感知延迟。 */
    private static final long LOCAL_CACHE_SECONDS = 30;
    /** 延迟双删的第二次删除延迟（毫秒）：兜底并发读在写库与删缓存之间回填旧值。 */
    private static final long DELAY_EVICT_MILLIS = 500;

    /** 秒杀券详情本地缓存：热点券省一次 Redis 往返。 */
    private final Cache<Long, CouponTemplate> localCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(LOCAL_CACHE_SECONDS))
            .build();

    /** 延迟双删的第二次删除用单线程调度器，避免污染公共线程池。 */
    private static final ScheduledExecutorService DELAYED_EVICTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-delayed-evictor");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private StringRedisTemplate redis;
    @Resource
    private CouponTemplateMapper couponTemplateMapper;
    @Resource
    private MqProducer mqProducer;
    @Resource
    private RedissonClient redissonClient;

    @Value("${rocketmq.coupon.seckill-topic}")
    private String seckillTopic;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = load("lua/seckill_stock.lua");
    private static final DefaultRedisScript<Long> INIT_SCRIPT = load("lua/seckill_init.lua");

    private static DefaultRedisScript<Long> load(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private String stockKey(Long templateId, int seg) {
        return "seckill:stock:" + templateId + ":" + seg;
    }

    private String initKey(Long templateId) {
        return "seckill:stock:" + templateId + ":init";
    }

    private String templateKey(Long templateId) {
        return "coupon:template:" + templateId;
    }

    /**
     * 缓存失效（延迟双删）：下架写 DB 后调用，先删 Redis + 失效本地，延迟再删一次。
     * 第二次删兜底「并发读在写库与删缓存之间读到旧值并回填」造成的脏缓存。
     */
    public void evictTemplateCache(Long templateId) {
        String key = templateKey(templateId);
        redis.delete(key);
        localCache.invalidate(templateId);
        DELAYED_EVICTOR.schedule(() -> {
            redis.delete(key);
            localCache.invalidate(templateId);
        }, DELAY_EVICT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** 秒杀入口：校验 -> 初始化分段库存 -> 轮询分段预扣 -> 发 MQ 异步落库。返回成功仅代表已受理。 */
    @SentinelResource(value = "seckill", blockHandler = "seckillBlock")
    public void seckill(Long templateId, Long userId) {
        CouponTemplate t = getTemplate(templateId);
        if (t == null || t.getSeckill() == null || t.getSeckill() != 1
                || t.getStatus() == null || t.getStatus() != 1) {
            throw new CustomException("404", "秒杀券不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(t.getClaimStart()) || now.isAfter(t.getClaimEnd())) {
            throw new CustomException("400", "不在秒杀时间内");
        }
        // 已初始化则跳过 Lua，省掉每次 11-key 脚本往返
        if (!Boolean.TRUE.equals(redis.hasKey(initKey(templateId)))) {
            initStock(templateId, t.getTotalCount());
        }

        // 按用户 hash 选段，段空则轮询下一段，最多 SEG 次；全空才判售罄
        int seg = Math.floorMod(userId.hashCode(), SEG);
        int successSeg = -1;
        for (int i = 0; i < SEG; i++) {
            int cur = (seg + i) % SEG;
            Long r = redis.execute(SECKILL_SCRIPT, List.of(stockKey(templateId, cur)));
            if (r != null && r == 1L) {
                successSeg = cur;
                break;
            }
        }
        if (successSeg < 0) {
            throw new CustomException("400", "该券已被抢完");
        }
        // 预扣成功，发 MQ 异步落库；发送失败按段回滚，避免库存被吞
        if (!mqProducer.send(seckillTopic, userId + ":" + templateId + ":" + successSeg)) {
            rollbackStock(templateId, successSeg);
            throw new CustomException("500", "系统繁忙，请重试");
        }
    }

    /** Sentinel 限流兜底：超 QPS 阈值快速失败。 */
    public void seckillBlock(Long templateId, Long userId, BlockException ex) {
        throw new CustomException("429", "秒杀太火爆，请稍后再试");
    }

    /** 初始化 10 段库存（Lua 原子，余数分摊到前几段，保证总和=总量）。 */
    private void initStock(Long templateId, int totalCount) {
        int base = totalCount / SEG;
        int rem = totalCount % SEG;
        List<String> keys = new ArrayList<>(SEG + 1);
        List<String> args = new ArrayList<>(SEG);
        for (int seg = 0; seg < SEG; seg++) {
            keys.add(stockKey(templateId, seg));
            args.add(String.valueOf(seg < rem ? base + 1 : base));
        }
        keys.add(initKey(templateId));
        redis.execute(INIT_SCRIPT, keys, args.toArray());
    }

    /** 秒杀模板三级缓存：本地 Caffeine(热点) -> Redis(三防护) -> DB。 */
    private CouponTemplate getTemplate(Long templateId) {
        // 1. 本地缓存：热点券详情直接本地命中，省一次 Redis 往返
        CouponTemplate t = localCache.getIfPresent(templateId);
        if (t != null) {
            return t;
        }
        // 2. 未命中走 Redis（穿透/击穿/雪崩三防护），结果非空回填本地
        t = loadTemplateViaRedis(templateId);
        if (t != null) {
            localCache.put(templateId, t);
        }
        return t;
    }

    /** Redis 层模板缓存：Hash 存关键字段（含时间窗），避免每次秒杀查 DB。
     *  三防护：穿透=空值缓存、击穿=Redisson 互斥重建、雪崩=TTL 随机抖动。 */
    private CouponTemplate loadTemplateViaRedis(Long templateId) {
        String key = templateKey(templateId);
        // 1. 读缓存：命中（含空值标记）直接返回，不打 DB
        Map<Object, Object> h = redis.opsForHash().entries(key);
        if (h != null && !h.isEmpty()) {
            return h.containsKey(EMPTY_FIELD) ? null : toTemplate(templateId, h);
        }
        // 2. 未命中：互斥重建防击穿（同一 template 只放一个线程去查库）
        RLock lock = redissonClient.getLock("lock:template:" + templateId);
        boolean locked;
        try {
            locked = lock.tryLock(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }
        if (locked) {
            try {
                // double-check：等锁期间别的线程可能已回填
                h = redis.opsForHash().entries(key);
                if (h != null && !h.isEmpty()) {
                    return h.containsKey(EMPTY_FIELD) ? null : toTemplate(templateId, h);
                }
                return loadAndCache(templateId, key);
            } finally {
                lock.unlock();
            }
        }
        // 3. 抢锁失败：重读一次缓存，仍无则兜底查库（A 方案，接受极小量并发）
        h = redis.opsForHash().entries(key);
        if (h != null && !h.isEmpty()) {
            return h.containsKey(EMPTY_FIELD) ? null : toTemplate(templateId, h);
        }
        return loadAndCache(templateId, key);
    }

    /** 查库并回填缓存：不存在写空值(防穿透)、存在写字段(TTL 抖动防雪崩)。 */
    private CouponTemplate loadAndCache(Long templateId, String key) {
        CouponTemplate t = couponTemplateMapper.selectById(templateId);
        if (t == null) {
            // 穿透：空值缓存短 TTL，拦截不存在的 id 反复打库
            redis.opsForHash().put(key, EMPTY_FIELD, "1");
            redis.expire(key, Duration.ofSeconds(EMPTY_CACHE_SECONDS));
            return null;
        }
        if (t.getSeckill() == null || t.getStatus() == null || t.getTotalCount() == null
                || t.getClaimStart() == null || t.getClaimEnd() == null) {
            // 字段不全属数据异常，不缓存，交由上层校验拦截
            return t;
        }
        Map<String, String> m = new HashMap<>();
        m.put("seckill", String.valueOf(t.getSeckill()));
        m.put("status", String.valueOf(t.getStatus()));
        m.put("totalCount", String.valueOf(t.getTotalCount()));
        m.put("claimStart", t.getClaimStart().toString());
        m.put("claimEnd", t.getClaimEnd().toString());
        redis.opsForHash().putAll(key, m);
        // 雪崩：TTL 随机抖动，避免多 key 同时过期
        redis.expire(key, Duration.ofSeconds(TEMPLATE_CACHE_SECONDS + ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS)));
        return t;
    }

    /** Hash -> CouponTemplate（仅缓存里的字段）。 */
    private CouponTemplate toTemplate(Long templateId, Map<Object, Object> h) {
        CouponTemplate t = new CouponTemplate();
        t.setId(templateId);
        t.setSeckill(Integer.valueOf(String.valueOf(h.get("seckill"))));
        t.setStatus(Integer.valueOf(String.valueOf(h.get("status"))));
        t.setTotalCount(Integer.valueOf(String.valueOf(h.get("totalCount"))));
        t.setClaimStart(LocalDateTime.parse(String.valueOf(h.get("claimStart"))));
        t.setClaimEnd(LocalDateTime.parse(String.valueOf(h.get("claimEnd"))));
        return t;
    }

    /** 回滚预扣：INCRBY +1（供发送失败/落库失败时退回，精确到段）。 */
    public void rollbackStock(Long templateId, int seg) {
        redis.opsForValue().increment(stockKey(templateId, seg), 1);
    }

    /** 预热：秒杀开始前提前初始化分段库存（幂等，已初始化则跳过）。 */
    public void warmupStock(Long templateId, int totalCount) {
        if (!Boolean.TRUE.equals(redis.hasKey(initKey(templateId)))) {
            initStock(templateId, totalCount);
        }
    }

    /** 对账：比对 Redis 已扣 vs DB 已发，Redis 多扣则回补差额。
     *  仅对已结束的券执行（MQ 应已消费完），避免把在途消息误判为丢失。 */
    public void reconcileStock(CouponTemplate t) {
        if (!Boolean.TRUE.equals(redis.hasKey(initKey(t.getId())))) {
            return; // 未初始化过库存，跳过
        }
        long redisLeft = 0;
        for (int seg = 0; seg < SEG; seg++) {
            String v = redis.opsForValue().get(stockKey(t.getId(), seg));
            if (v != null) {
                redisLeft += Long.parseLong(v);
            }
        }
        long redisConsumed = t.getTotalCount() - redisLeft;
        long dbIssued = t.getClaimedCount() == null ? 0 : t.getClaimedCount();
        long gap = redisConsumed - dbIssued;
        if (gap > 0) {
            // 回补到段 0，保证 Redis 剩余与 DB 账实相符
            redis.opsForValue().increment(stockKey(t.getId(), 0), gap);
            log.warn("秒杀对账回补：template={} redis已扣={} db已发={} 回补={}", t.getId(), redisConsumed, dbIssued, gap);
        }
    }
}
