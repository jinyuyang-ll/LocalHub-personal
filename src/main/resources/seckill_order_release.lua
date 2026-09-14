local orderKey = KEYS[1]
local stockKey = KEYS[2]
local statusKey = KEYS[3]
local reservationKey = KEYS[4]
local auditKey = KEYS[5]
local auditHash = KEYS[6]
local userId = ARGV[1]
local statusTtl = ARGV[2]
local orderId = ARGV[3]

-- SREM is the Redis-side idempotency gate: only the first release restores stock.
if redis.call('srem', orderKey, userId) == 1 then
    redis.call('incrby', stockKey, 1)
end
redis.call('del', reservationKey)
redis.call('zrem', auditKey, orderId)
redis.call('hdel', auditHash, orderId)
redis.call('set', statusKey, 'CLOSED', 'EX', statusTtl)
return 1
