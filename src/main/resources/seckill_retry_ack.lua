local orderId = ARGV[1]
redis.call('del', KEYS[1])
redis.call('hdel', KEYS[2], orderId)
redis.call('zrem', KEYS[3], orderId)
redis.call('zrem', KEYS[4], orderId)
redis.call('zrem', KEYS[5], orderId)
redis.call('hdel', KEYS[6], orderId)
return 1
