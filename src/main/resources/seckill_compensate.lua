local reservationKey = KEYS[1]
local orderKey = KEYS[2]
local stockKey = KEYS[3]
local statusKey = KEYS[4]
local reservationAuditKey = KEYS[5]
local reservationAuditHash = KEYS[6]
local expected = ARGV[1]
local userId = ARGV[2]
local statusTtlSeconds = ARGV[3]
local finalStatus = ARGV[4]
local preserveUserMarker = ARGV[5]

if redis.call('get', reservationKey) ~= expected then
    return 0
end

redis.call('del', reservationKey)
redis.call('zrem', reservationAuditKey, ARGV[6])
redis.call('hdel', reservationAuditHash, ARGV[6])
if preserveUserMarker == '1' then
    redis.call('sadd', orderKey, userId)
else
    redis.call('srem', orderKey, userId)
end
redis.call('incrby', stockKey, 1)
redis.call('set', statusKey, finalStatus, 'EX', statusTtlSeconds)
return 1
