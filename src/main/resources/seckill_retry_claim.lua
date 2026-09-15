local retryZset = KEYS[1]
local processingZset = KEYS[2]
local claimHash = KEYS[3]
local now = tonumber(ARGV[1])
local leaseUntil = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local tokenPrefix = ARGV[4]

-- First return abandoned claims to the retry queue. ZREM makes this safe
-- when several application instances execute the script concurrently.
local expired = redis.call('zrangebyscore', processingZset, '-inf', now, 'LIMIT', 0, limit)
for _, orderId in ipairs(expired) do
    if redis.call('zrem', processingZset, orderId) == 1 then
        redis.call('hdel', claimHash, orderId)
        redis.call('zadd', retryZset, now, orderId)
    end
end

local due = redis.call('zrangebyscore', retryZset, '-inf', now, 'LIMIT', 0, limit)
local claimed = {}
for _, orderId in ipairs(due) do
    if redis.call('zrem', retryZset, orderId) == 1 then
        local claimToken = tokenPrefix .. ':' .. orderId
        redis.call('zadd', processingZset, leaseUntil, orderId)
        redis.call('hset', claimHash, orderId, claimToken)
        table.insert(claimed, orderId)
        table.insert(claimed, claimToken)
    end
end
return claimed
