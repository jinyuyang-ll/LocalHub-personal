# LocalHub 智慧生活平台

LocalHub 是一个 Vue 3 + Spring Boot 的本地生活全栈项目，覆盖商家检索、优惠券、Redis Lua 秒杀、Kafka 异步订单、预约、AI 客服、限流、缓存治理与可观测性。

## 项目来源与原创边界

项目以黑马程序员 `hm-dianping` 教学项目的数据模型和基础商家/博客业务为起点。LocalHub 新增并重构了 Vue 3 前端、订单域服务拆分、Kafka/Redis Stream 双队列、Lua 预扣与补偿、Outbox Relay、订单状态机与库存释放、两级缓存和 Bloom 重建、Canal 缓存失效、可观测性、AI Function Calling/RAG/Agent Workflow，以及 Testcontainers、Playwright、JMeter 和 CI 证据链。保留来源说明是为了明确教学基线与个人工程工作的边界。

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
| `MYSQL_ROOT_PASSWORD` | 无 | MySQL 密码，仅写入本地 `.env` |
| `LOCALHUB_AI_ENABLED` | `false` | 是否调用真实兼容 OpenAI 协议的模型 |
| `LOCALHUB_AI_MODEL` | `qwen-plus` | 模型名 |
| `LOCALHUB_AI_BASE_URL` | DashScope compatible endpoint | 模型服务地址 |
| `LOCALHUB_AI_API_KEY` | 空 | API Key，不提交到 Git |
| `LOCALHUB_AI_EMBEDDING_MODEL` | `text-embedding-v3` | RAG 向量模型 |
| `LOCALHUB_AI_EMBEDDING_BASE_URL` | DashScope compatible endpoint | 向量模型服务地址，可与对话模型不同 |
| `LOCALHUB_AI_EMBEDDING_DIMENSIONS` | `1024` | 向量维度，须与百炼模型配置一致 |
| `LOCALHUB_CANAL_ENABLED` | `false` | 是否启用 Canal 消费器 |
| `SPRING_PROFILES_ACTIVE` | `dev`（Compose 为 `prod`） | 开发/生产日志与组件开关配置 |
| `LOCALHUB_TRUSTED_PROXIES` | 空 | 可被信任的反向代理 IP、CIDR 或主机名；空值时忽略转发头 |

生产环境只应把 Nginx/Ingress 加入 `LOCALHUB_TRUSTED_PROXIES`，并由代理覆盖而非追加客户端传入的 `X-Forwarded-For`。服务直连时后端始终使用 `remoteAddr`，限流返回 HTTP `429 Too Many Requests`。

真实 AI 示例：编辑 `.env`，设置 `LOCALHUB_AI_ENABLED=true` 和 `LOCALHUB_AI_API_KEY`，然后重新运行 `docker compose up --build -d`。子业务空间或非北京地域应同时填写该空间对应的对话与 Embedding Base URL。

已有旧数据库的用户应先备份，再按顺序各执行一次 `upgrade-outbox-v2.sql`、`upgrade-outbox-v3.sql`、`upgrade-order-release-v1.sql` 和 `upgrade-consumer-message-v1.sql`；全新 Docker 数据卷会直接使用最新版 `hmdp.sql`，无需执行升级脚本。

## 核心实现

- Redisson 复用 `spring.redis.*` 配置，容器内连接 Compose 服务名 `redis`，不再硬编码本机地址。
- 首页商家搜索使用 Caffeine + Redis 两级缓存；店铺更新时清理本地缓存并递增 Redis 查询版本，避免通配符删除。
- 订单域拆分为秒杀入口、消息消费、事务建单、状态、支付、取消、关单与补偿组件；HTTP 线程在 Lua 成功后立即返回，不等待 Kafka ACK。异步发送失败写入 Redis 延迟重试集合，Recovery 使用 10 条小批量、有界线程池及 Lua `claimToken` 完成 `claim → publish → token 校验 ack/requeue`；过期 worker 无法删除新 worker 重领的任务。
- 秒杀状态采用 `PENDING → PROCESSING → SUCCESS / FAILED / CLOSED` 生命周期；成功保留 24 小时，失败及原因保留 7 天。
- Outbox 使用全局 `event_id`、聚合 ID、乐观锁版本与发送时间；Relay 使用 MySQL `FOR UPDATE SKIP LOCKED` 和 `locked_by/locked_until` 租约先抢占再投递。消息统一封装为 `eventId/eventType/aggregateId/occurredAt/data`，消费者在同一事务内写入 `tb_consumer_message`，形成通用 eventId 幂等门闩。
- 取消和超时关单通过 Outbox 驱动 `OrderEventConsumer`，`tb_order_release` 唯一订单记录保证 MySQL 只恢复一次库存，Lua 的 `SREM` 结果保证 Redis 只恢复一次资格和库存；未完成的 Redis 恢复由对账任务继续处理。
- reservation 不再只依赖 TTL：Lua 同时写入审计 ZSet 和无 TTL 载荷 Hash；对账任务发现长期 `PROCESSING` 且无 MySQL 订单时会幂等重投，重试耗尽后才补偿。对账和超时关单锁使用 Redisson watchdog 自动续期，慢任务不会因固定 lease 到期而与其他实例重叠。
- AI 客服通过 LangChain4j `AiServices` 和 `@Tool` 使用真实模型 Function Calling；查询工具只读，预约采用 `PREVIEWED → CONFIRMING → CONFIRMED / CANCELLED` 状态机、当前轮次显式确认、分布式互斥和结果幂等。
- FAQ 按标题和段落切块，使用百炼 Embedding 与进程内向量索引、关键词召回、Query Rewrite、加权混合召回和二阶段重排；低于阈值时拒答。项目不宣称使用 Milvus/FAISS，模型不可用时仅降级到关键词检索，不生成伪向量。
- AI Agent Workflow 显式编排 Planner → LangChain4j Tool Executor → Redis Conversation Memory → Guarded Response，工具层再次校验登录用户、资源归属和写操作确认。
- 生产代码不调用 Redis `KEYS`；Lua 的业务 Key 由 `RedisConstants` 统一生成并通过 `KEYS[]` 传入，避免散落硬编码。
- Prometheus 暴露 `localhub_outbox_pending`、`localhub_outbox_oldest_age_seconds`、`localhub_kafka_retry_count`、`localhub_compensation_count` 和 `localhub_seckill_processing_age_seconds` 等业务指标。

订单域职责对应关系：

| 组件 | 职责 |
|---|---|
| `SeckillService` | Lua 秒杀入口与 Kafka 首次投递 |
| `SeckillMessageConsumer` / `KafkaSeckillOrderConsumer` | Redis Stream / Kafka 消费 |
| `OrderCreateService` | 库存、订单与 Outbox 的原子事务 |
| `OrderStatusService` | 状态生命周期与所有权校验 |
| `OrderPaymentService` / `OrderCancelService` | 支付与取消 |
| `OrderCloseJob` | 超时自动关单 |
| `SeckillRollbackService` | Lua 原子幂等回滚与失败原因记录 |
| `OrderEventConsumer` / `OrderReleaseService` | 取消/关单后的 DB 与 Redis 库存恢复 |
| `SeckillReconciliationJob` | 长时间故障后的预约和库存释放对账 |

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

RAG 离线评测包含 56 条版本化问题，当前关键词降级路径实测 `Hit@3 = 94.64%`。运行 `mvn -Dtest=RagEvaluationTest test` 可复现；在线 Embedding/Agent 指标必须带模型版本和日期单独记录，避免把非确定性结果写死。

Testcontainers 测试需要 Docker；Docker 不可用时会自动跳过。Windows 也可运行 `.\scripts\verify.ps1`，通过容器执行后端和前端构建。

`verify.ps1` 会校验 Compose、构建并启动全部核心服务、检查健康状态、执行 Playwright；本机安装 Maven 时还会执行包含 Testcontainers 的 `mvn verify`。

## 演示流程

1. 打开登录页，发送验证码并登录；开发环境验证码可从后端日志查看。
2. 首页点击商家进入详情，真实触发 Bloom → Caffeine → Redis → MySQL 缓存链路，再查看优惠券并提交秒杀。
3. 复制返回的订单号，在订单页点击“自动轮询”，观察 `PROCESSING` 转为最终状态。
4. 在 AI 客服输入规则问题，观察 SSE 分块输出；输入“查询订单 订单号”触发真实订单状态工具。
5. 在 AI 预约助手填写店铺与时间，先预览，再确认创建；确认令牌只能使用一次。
6. 打开社区演示热门、关注流、点赞和评论；在用户中心查看订单、预约和签到。
7. 查看 `/actuator/prometheus`，或启用 monitoring profile 后在 Prometheus 查询 `http_server_requests_seconds_count`。

## 压测

JMeter 脚本位于 `tests/jmeter/localhub-smoke.jmx`。运行方法与安全注意事项见 [docs/jmeter.md](docs/jmeter.md)，结果记录模板见 [docs/load-test-report.md](docs/load-test-report.md)。仓库不会声称未经当前环境复现的 QPS/P99 数字。

Windows 可执行：

```powershell
.\scripts\run-load-test.ps1 -Threads 50 -Loops 20 -RampSeconds 20 -Token '登录令牌'
```

脚本会为每次运行建立独立目录，并保存 JTL、HTML 报告以及压测前后的 Prometheus 快照。正确性 SQL 位于 `tests/sql/verify-seckill.sql`。

## 进一步文档

- [总体架构、秒杀一致性和 AI 流程图](docs/architecture.md)
- [故障注入矩阵](docs/fault-injection.md)
- [AI/RAG 评测](docs/evals/README.md)
- [Docker Compose](docs/docker-compose.md)
- [Playwright](docs/playwright.md)
- [JMeter](docs/jmeter.md)
