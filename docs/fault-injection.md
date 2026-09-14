# 故障注入矩阵

| 故障 | 注入方式 | 期望不变量 | 自动化证据 |
|---|---|---|---|
| Kafka 首次发送失败 | 返回失败的 `ListenableFuture` | HTTP 不等待；消息进入 Redis durable retry | `SeckillServiceTest` |
| Redis retry worker 中途宕机 | claim 后不 ack，推进 lease 时间 | 其他实例在 lease 到期前不能领取，到期后可重领 | `InfrastructureIntegrationTest.kafkaPublishRecoveryClaimIsAtomicAndLeaseCanExpire` |
| DB 创建订单超时 | `OrderCreateService` 抛异常 | 消息进入 retry/DLT，最终补偿 | `KafkaSeckillOrderConsumerTest` |
| 同一 Kafka 消息重复 10 次 | 向真实 Testcontainers Kafka 连续发送 | 一条订单、一次 DB 扣减、一条 CREATED Outbox | `SeckillHttpKafkaIntegrationTest` |
| 消费者处理后提交 offset 前宕机 | 重投相同业务键 | PK 和 `(user_id,voucher_id)` 阻止重复落库 | 同上 |
| Outbox 发送后未 ACK | 保留 PROCESSING 且不 markSent | lease 到期后事件可被其他 Relay 重领 | `OutboxEventServiceTest` + `locked_until` SQL |
| 取消/关单事件重复 | 重放 CANCELLED/CLOSED 事件 | `tb_order_release` 与 Lua `SREM` 双重幂等 | `SeckillHttpKafkaIntegrationTest.cancelledOrderRestoresDatabaseStockAndRedisQualification` |
| Redis 在库存释放时重启 | 保留 `redis_released=0` | 对账任务恢复 Redis 后再标记完成 | `OrderReleaseService.retryPendingRedisReleases` |

CI 的 Testcontainers 用例覆盖真实 MySQL、Redis 和 Kafka。涉及进程强杀/网络延迟的破坏性实验应只在隔离环境执行，并保存提交号、容器日志、Prometheus 快照和最终正确性 SQL 结果。
