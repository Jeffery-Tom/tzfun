-- 一次性原子初始化 10 段秒杀库存：并发下只初始化一次，避免分段配额错乱
-- KEYS[1..10] = 各段 key，KEYS[11] = 全局 init 标记 key
-- ARGV[1..10] = 各段初始值（余数已分摊到前几段）
local n = #KEYS - 1
if redis.call('EXISTS', KEYS[#KEYS]) == 1 then
    return 0
end
for i = 1, n do
    redis.call('SET', KEYS[i], ARGV[i], 'NX')
end
redis.call('SET', KEYS[#KEYS], '1')
return 1
