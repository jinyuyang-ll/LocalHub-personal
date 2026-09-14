-- voucher id
local voucherId = ARGV[1]
-- user id
local userId = ARGV[2]
-- order id
local orderId = ARGV[3]

local stockKey = KEYS[1]
local orderKey = KEYS[2]
local statusKey = KEYS[3]
local reservationKey = KEYS[4]
local ownerKey = KEYS[5]
local reservationAuditKey = KEYS[6]
local reservationAuditHash = KEYS[7]

local stock = tonumber(redis.call('get', stockKey))
if(stock == nil) then
    return 3
end
if(stock <= 0) then
    return 1
end

if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
redis.call('set', statusKey, 'PROCESSING', 'EX', ARGV[4])
redis.call('set', reservationKey, voucherId .. ':' .. userId, 'EX', 86400)
redis.call('set', ownerKey, userId, 'EX', 604800)
redis.call('zadd', reservationAuditKey, redis.call('time')[1], orderId)
redis.call('hset', reservationAuditHash, orderId, voucherId .. ':' .. userId)
return 0
