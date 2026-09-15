local orderId = ARGV[1]
local claimToken = ARGV[4]
if redis.call('hget', KEYS[4], orderId) ~= claimToken then
    return 0
end
redis.call('hset', KEYS[1], orderId, ARGV[2])
redis.call('zrem', KEYS[3], orderId)
redis.call('hdel', KEYS[4], orderId)
redis.call('zadd', KEYS[2], ARGV[3], orderId)
return 1
