# JMeter 压测

当前提供一个 smoke 级别压测脚本：

- `tests/jmeter/localhub-smoke.jmx`

覆盖接口：

- `POST /api/ai/chat`
- `GET /api/shops/search`
- `GET /api/voucher-orders/{orderId}/status`
- `POST /api/seckill-vouchers/{voucherId}/orders`，默认禁用，避免误触发库存扣减

## 前置条件

安装 JMeter 5.6+ 并确认：

```bash
jmeter --version
```

## 运行

后端启动后执行：

```bash
jmeter -n -t tests/jmeter/localhub-smoke.jmx -l target/jmeter-localhub.jtl -e -o target/jmeter-report
```

如果后端不在默认地址：

```bash
jmeter -n -t tests/jmeter/localhub-smoke.jmx -Jhost=127.0.0.1 -Jport=8081 -l target/jmeter-localhub.jtl
```

如果要压测需要登录的接口，请给 `token` 变量赋值，或者在 JMeter GUI 中修改 HTTP Headers 的 `authorization`。
