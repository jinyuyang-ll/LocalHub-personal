# LocalHub 压测报告

本文件只记录可复现结果，不预填无法验证的 QPS 或 P99。执行压测后，将 JMeter HTML 报告中的数据填入下表，并保留原始 JTL。

推荐使用 `scripts/run-load-test.ps1`。每次运行会在 `target/load-test-时间戳` 下保存原始 JTL、HTML 报告以及前后两份 Prometheus 指标快照。

## 环境

| 项目 | 值 |
|---|---|
| 日期 | 待填写 |
| Git commit | 待填写 |
| CPU / 内存 | 待填写 |
| Java / Docker 版本 | 待填写 |
| MySQL / Redis / Kafka 版本 | 8.0 / 7.2 / 3.7 |
| 并发用户 / 持续时间 | 待填写 |

## 结果

| 场景 | 样本数 | QPS | P50 | P95 | P99 | 错误率 |
|---|---:|---:|---:|---:|---:|---:|
| 商家搜索 | 待测 | 待测 | 待测 | 待测 | 待测 | 待测 |
| AI 客服 | 待测 | 待测 | 待测 | 待测 | 待测 | 待测 |
| 秒杀受理 | 待测 | 待测 | 待测 | 待测 | 待测 | 待测 |
| 订单状态查询 | 待测 | 待测 | 待测 | 待测 | 待测 | 待测 |

## 正确性检查

- `tb_seckill_voucher.stock >= 0`
- `tb_voucher_order` 不存在重复 `(user_id, voucher_id)`
- Redis `PROCESSING` 订单最终进入 `SUCCESS`、`FAILED` 或对账处理
- Kafka consumer lag 在流量停止后回落到 0
- Prometheus 中 HTTP 错误率和 JVM 指标无异常突增

只有完成上述检查后，才能把结果写入 README 或简历。
