-- voucher id
local voucherId = ARGV[1]
-- user id
local userId = ARGV[2]
-- order id
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local statusKey = 'seckill:order:status:' .. orderId
local reservationKey = 'seckill:reservation:' .. orderId

if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('set', statusKey, 'PROCESSING', 'EX', 1800)
redis.call('set', reservationKey, voucherId .. ':' .. userId, 'EX', 86400)
return 0
