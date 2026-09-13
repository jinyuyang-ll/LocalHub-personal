local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local statusTtlSeconds = ARGV[4]
local finalStatus = ARGV[5]
local preserveUserMarker = ARGV[6]

local reservationKey = 'seckill:reservation:' .. orderId
local expected = voucherId .. ':' .. userId
if redis.call('get', reservationKey) ~= expected then
    return 0
end

redis.call('del', reservationKey)
if preserveUserMarker == '1' then
    redis.call('sadd', 'seckill:order:' .. voucherId, userId)
else
    redis.call('srem', 'seckill:order:' .. voucherId, userId)
end
redis.call('incrby', 'seckill:stock:' .. voucherId, 1)
redis.call('set', 'seckill:order:status:' .. orderId, finalStatus, 'EX', statusTtlSeconds)
return 1
