# Playwright E2E 测试

## 安装依赖

```bash
cd frontend
npm install
npx playwright install
```

## 运行测试

当前 E2E 对外部 API 使用 Playwright 路由模拟，覆盖商家显示、SSE 拼接、订单轮询和预约二次确认，因此前端回归不依赖后端数据。完整真实链路由 Testcontainers 集成测试覆盖。

如需同时观察真实后端，可先启动：

```bash
cd ..
mvn spring-boot:run
```

然后运行前端 E2E：

```bash
cd frontend
npm run test:e2e
```

如果前端已经运行在其它地址：

```bash
set PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173
npm run test:e2e
```
