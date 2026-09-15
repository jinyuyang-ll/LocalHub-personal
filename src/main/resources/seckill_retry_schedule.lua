local payloadHash = KEYS[1]
local retryZset = KEYS[2]
local processingZset = KEYS[3]
local claimHash = KEYS[4]
local orderId = ARGV[1]

-- A generic producer/reconciliation callback must never steal an active claim.
if redis.call('hexists', claimHash, orderId) == 1 then
    return 0
end
redis.call('hset', payloadHash, orderId, ARGV[2])
redis.call('zrem', processingZset, orderId)
redis.call('zadd', retryZset, ARGV[3], orderId)
return 1
