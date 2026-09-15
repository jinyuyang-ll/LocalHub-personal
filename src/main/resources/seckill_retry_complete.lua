local orderId = ARGV[1]
redis.call('del', KEYS[1])
redis.call('hdel', KEYS[2], orderId)
redis.call('zrem', KEYS[3], orderId)
redis.call('zrem', KEYS[4], orderId)
redis.call('hdel', KEYS[5], orderId)
redis.call('zrem', KEYS[6], orderId)
redis.call('hdel', KEYS[7], orderId)
return 1
