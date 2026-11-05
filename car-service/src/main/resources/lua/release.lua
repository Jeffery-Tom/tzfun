-- 释放库存：统一加回（原子）
-- KEYS: 各日期槽 key；ARGV[1]: 释放数量 count

local cnt = tonumber(ARGV[1])
--遍历租期所有日期 key，每一天库存 + cnt
for i, key in ipairs(KEYS) do
    redis.call('INCRBY', key, cnt)
end
return 1
