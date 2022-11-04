-- 秒杀优化lua脚本

-- 1.参数列表
-- 1.1 优惠券ID判断当前优惠券的库存是否充足
local voucherId = ARGV[1]
-- 1.2 用户ID判断当前用户是否已经下过单
local userId = ARGV[2]
-- 1.3 订单ID
local orderId = ARGV[3]

-- 2.数据key
-- 2.1 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1 判断库存是否充足
if(tonumber(redis.call('get',stockKey)) <= 0)
   -- 库存不足，返回1
   then return 1
end
-- 3.2库存充足，判断用户是否重复下单
if(redis.call('sismember',orderKey,userId) == 1)
   -- 用户存在于订单Set集合中，说明重复下单，返回2
   then return 2
end
-- 3.3 脚本运行到这里表示可以正常下单
-- 3.4 扣减库存
redis.call('incrby',stockKey, -1)
-- 3.5 下单（将用户存入订单Set集合）
redis.call('sadd',orderKey,userId)
return 0