-- 占用库存：先检查所有日期槽剩余 >= need，全部通过才统一扣减（检查+扣减在同一脚本内，原子）
-- KEYS: 各日期槽 key；ARGV[1]: 占用数量 need
local need = tonumber(ARGV[1])
-- 第一轮：第一轮遍历所有 key get 库存，全部 >=1 ✅
for i, key in ipairs(KEYS) do
    local cur = tonumber(redis.call('GET', key) or 0)
    if cur < need then
        return 0 -- 任意一天库存不够 → 直接失败，后面不扣
    end
end
-- 第二轮：第二轮遍历所有 key，DECRBY -1
for i, key in ipairs(KEYS) do
    redis.call('DECRBY', key, need)
end
return 1 --占用成功
