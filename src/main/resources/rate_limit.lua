local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

redis.call('zremrangebyscore', key, 0, now - window)

local count = redis.call('zcard', key)
if count >= limit then
    return 0
end

redis.call('zadd', key, now, member)
redis.call('pexpire', key, window)
return 1
