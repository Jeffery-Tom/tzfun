-- 秒杀预扣库存：cur >= 1 才 DECRBY，返回 1 成功 / 0 失败（防超卖，与占车同一套路）
-- KEYS[1]: seckill:stock:{templateId}
local cur = tonumber(redis.call('GET', KEYS[1]) or 0)
if cur < 1 then
    return 0
end
redis.call('DECRBY', KEYS[1], 1)
return 1
