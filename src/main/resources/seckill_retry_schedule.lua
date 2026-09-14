local payloadHash = KEYS[1]
local retryZset = KEYS[2]
local processingZset = KEYS[3]
local orderId = ARGV[1]

redis.call('hset', payloadHash, orderId, ARGV[2])
redis.call('zrem', processingZset, orderId)
redis.call('zadd', retryZset, ARGV[3], orderId)
return 1
