-- 滑动窗口限流：ZSET 记录每次请求时间戳，移除窗口外旧记录后计数，超阈值则拒绝
-- KEYS[1]: 限流 key（seckill:rate:{templateId}:{userId}）
-- ARGV[1]: 窗口(秒)；ARGV[2]: 阈值；ARGV[3]: 当前时间戳(ms)；ARGV[4]: 唯一 member
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member = ARGV[4]
-- 先清理窗口外的旧请求，再计数，保证是「滑动窗口」而非「固定窗口」
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window * 1000)
local count = redis.call('ZCARD', KEYS[1])
if count >= limit then
    return 0 -- 超限拒绝
end
redis.call('ZADD', KEYS[1], now, member)
redis.call('PEXPIRE', KEYS[1], window * 1000)
return 1 -- 放行
