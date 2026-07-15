# LocalHub 智慧生活平台

LocalHub 是一个 Vue 3 + Spring Boot 的本地生活全栈项目，覆盖商家检索、优惠券、Redis Lua 秒杀、Kafka 异步订单、预约、AI 客服、限流、缓存治理与可观测性。

## 一键启动

要求 Docker Desktop 与 Docker Compose v2。

```powershell
Copy-Item .env.example .env
.\scripts\start.ps1
```

如果 Docker Hub 构建镜像网络不稳定，可使用已验证的本地混合启动方式（MySQL、Redis、Kafka 使用容器，Java/Vue 使用本机进程）：

```powershell
.\scripts\start-local.ps1
```

也可以直接执行：

```bash
docker compose up --build -d
```

启动完成后访问：

- Web：http://localhost:5173
- API：http://localhost:8081
- 健康检查：http://localhost:8081/actuator/health
- Prometheus 指标：http://localhost:8081/actuator/prometheus

停止服务：`docker compose down`。需要清空本地数据时显式执行 `docker compose down -v`。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | `123456` | 本地 MySQL 密码 |
| `LOCALHUB_AI_ENABLED` | `false` | 是否调用真实兼容 OpenAI 协议的模型 |
| `LOCALHUB_AI_MODEL` | `qwen-plus` | 模型名 |
| `LOCALHUB_AI_BASE_URL` | DashScope compatible endpoint | 模型服务地址 |
| `LOCALHUB_AI_API_KEY` | 空 | API Key，不提交到 Git |
| `LOCALHUB_CANAL_ENABLED` | `false` | 是否启用 Canal 消费器 |

真实 AI 示例：编辑 `.env`，设置 `LOCALHUB_AI_ENABLED=true` 和 `LOCALHUB_AI_API_KEY`，然后重新运行 `docker compose up --build -d`。

## 可选组件

```bash
docker compose --profile canal up --build -d
docker compose --profile monitoring up --build -d
```

监控 profile 增加 Prometheus（9090）与 Grafana（3000）。Grafana 首次使用默认 `admin/admin` 登录，Prometheus 数据源和 `LocalHub Overview` 仪表盘会自动配置。

## 构建和测试

本机已有 Java 8、Maven 与 Node 20 时：

```bash
mvn verify
cd frontend
npm ci
npm run build
npm run test:e2e
```

Testcontainers 测试需要 Docker；Docker 不可用时会自动跳过。Windows 也可运行 `.\scripts\verify.ps1`，通过容器执行后端和前端构建。

`verify.ps1` 会校验 Compose、构建并启动全部核心服务、检查健康状态、执行 Playwright；本机安装 Maven 时还会执行包含 Testcontainers 的 `mvn verify`。

## 演示流程

1. 打开登录页，发送验证码并登录；开发环境验证码可从后端日志查看。
2. 首页搜索商家，查看优惠券并提交秒杀。
3. 复制返回的订单号，在订单页点击“自动轮询”，观察 `PROCESSING` 转为最终状态。
4. 在 AI 客服输入规则问题，观察 SSE 分块输出；输入“查询订单 订单号”触发真实订单状态工具。
5. 在 AI 预约助手填写店铺与时间，先预览，再确认创建；确认令牌只能使用一次。
6. 查看 `/actuator/prometheus`，或启用 monitoring profile 后在 Prometheus 查询 `http_server_requests_seconds_count`。

## 压测

JMeter 脚本位于 `tests/jmeter/localhub-smoke.jmx`。运行方法与安全注意事项见 [docs/jmeter.md](docs/jmeter.md)，结果记录模板见 [docs/load-test-report.md](docs/load-test-report.md)。仓库不会声称未经当前环境复现的 QPS/P99 数字。

Windows 可执行：

```powershell
.\scripts\run-load-test.ps1 -Threads 50 -Loops 20 -RampSeconds 20 -Token '登录令牌'
```

脚本会为每次运行建立独立目录，并保存 JTL、HTML 报告以及压测前后的 Prometheus 快照。正确性 SQL 位于 `tests/sql/verify-seckill.sql`。

## 进一步文档

- [Docker Compose](docs/docker-compose.md)
- [Playwright](docs/playwright.md)
- [JMeter](docs/jmeter.md)
