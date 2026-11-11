package com.tzfun.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法分布式 ID：1 符号位 + 41 时间戳 + 10 机器号 + 12 序列号。
 * 保证全局唯一、趋势递增，替代「时间戳 + 随机数」的订单号生成方式。
 */
@Component
public class SnowflakeIdGenerator {

    /** 起始时间戳（2024-01-01 00:00:00 UTC），41 位可撑约 69 年。 */
    private static final long EPOCH = 1704067200000L;
    /** 机器号占位。 */
    private static final long WORKER_ID_BITS = 10L;
    /** 序列号占位：同毫秒内最多 4096 个。 */
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 越界: " + workerId);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个 ID。synchronized 保证并发下 sequence 单调；时钟回拨时沿用上次时间戳，
     * 靠 sequence 继续递增仍保证唯一，不阻塞、不抛异常。
     */
    public synchronized long nextId() {
        long ts = System.currentTimeMillis();
        if (ts < lastTimestamp) {
            // 时钟回拨：沿用 lastTimestamp，sequence 递增仍唯一
            ts = lastTimestamp;
        }
        if (ts == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 同毫秒序列耗尽，等待下一毫秒
                ts = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = ts;
        return ((ts - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long last) {
        long ts = System.currentTimeMillis();
        while (ts <= last) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
