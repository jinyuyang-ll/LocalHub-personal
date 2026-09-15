# LocalHub 架构图

## 总体架构

```mermaid
flowchart LR
  UI[Vue 3 / Playwright] --> API[Spring Boot API]
  API --> Cache[Caffeine → Bloom → Redis]
  Cache --> DB[(MySQL)]
  API --> Lua[Redis Lua 秒杀]
  Lua --> Kafka[Kafka]
  Kafka --> Order[订单域服务]
  Order --> DB
  Order --> Outbox[(Outbox)]
  Outbox --> Relay[Claim/Lease Relay]
  Relay --> Kafka
  API --> AI[LangChain4j Agent]
  AI --> RAG[Rewrite / Hybrid / Rerank]
  AI --> Tools[鉴权业务工具]
  Canal[Canal CDC] --> Cache
  API --> Metrics[Actuator / Prometheus / Grafana]
```

## 秒杀与最终一致性

```mermaid
sequenceDiagram
  participant U as HTTP 请求
  participant R as Redis Lua
  participant K as Kafka
  participant C as Order Consumer
  participant D as MySQL
  participant O as Outbox Relay
  U->>R: 原子校验、预扣、PROCESSING、审计记录
  R-->>U: 立即返回 orderId
  R-)K: 异步发送（不等待 ACK）
  alt 发送失败
    K-->>R: Lua claimToken + lease 的延迟重试队列
  end
  K->>C: 至少一次投递
  C->>D: 库存-1 + 订单 + Outbox（同事务）
  D-->>C: PK/(user,voucher) 幂等
  O->>D: SKIP LOCKED + lease 抢占
  O->>K: 发布 Event Envelope
  K->>D: eventId 去重 + 取消/关单幂等库存恢复
  K->>R: Lua 恢复资格和库存
```

## 两套订单状态的边界

技术处理状态与业务订单状态描述的是不同事实，不能合并为一个枚举：

```mermaid
stateDiagram-v2
  state "Redis 技术处理状态" as RedisState {
    [*] --> PENDING
    PENDING --> PROCESSING: Lua 预扣成功
    PROCESSING --> SUCCESS: MySQL 订单事务提交
    PROCESSING --> FAILED: 投递/建单重试耗尽并补偿
    SUCCESS --> CLOSED: 未支付取消或超时关单
  }
  state "MySQL 业务订单状态" as DbState {
    [*] --> UNPAID: 创建订单
    UNPAID --> PAID: 支付 CAS 成功
    UNPAID --> CANCELLED: 用户取消/超时关单 CAS 成功
  }
```

| 时间点 | `SeckillOrderState`（Redis） | `OrderStatus`（MySQL） |
|---|---|---|
| Lua 接受请求 | `PROCESSING` | 尚无订单 |
| 建单事务提交 | `SUCCESS` | `UNPAID` |
| 支付完成 | `SUCCESS` | `PAID` |
| 取消/超时并发出 Outbox | `CLOSED` | `CANCELLED` |
| 投递或建单永久失败 | `FAILED` | 尚无订单，Redis 预扣已补偿 |

Redis 状态用于轮询异步处理进度并带 TTL；MySQL 状态是订单业务事实源。数据库 PK 与
`(user_id, voucher_id)` 唯一键始终是最终幂等防线。

## AI Agent 与 RAG

```mermaid
flowchart LR
  Q[用户问题] --> Guard[Input Guardrail]
  Guard --> Rewrite[Query Rewrite]
  Rewrite --> Hybrid[关键词 + Embedding 混合召回]
  Hybrid --> Rerank[二阶段 Rerank / 阈值拒答]
  Rerank --> Planner[Planner]
  Planner --> LLM[百炼 Qwen]
  LLM -->|选择工具| ToolGuard[Tool Guard / 用户归属校验]
  ToolGuard --> Tool[查询 / 预约预览 / 确认]
  Tool --> Memory[Redis Conversation Memory]
  LLM --> Memory
  Memory --> Output[Output Guardrail / SSE]
```
