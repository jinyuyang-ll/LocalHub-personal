# LocalHub Docker Compose 一键运行

## 前置条件

- 安装 Docker Desktop，确认命令可用：

```bash
docker --version
docker compose version
```

## 启动完整环境

```bash
docker compose up --build
```

启动后访问：

- 前端：http://localhost:5173
- 后端：http://localhost:8081
- MySQL：localhost:3306，root / 123456
- Redis：localhost:6379
- Kafka：localhost:9092

## 启用 AI Key

默认 AI 使用本地 RAG 兜底。如果要调用真实模型：

```bash
set LOCALHUB_AI_API_KEY=你的Key
docker compose up --build
```

并把 `backend.environment.LOCALHUB_AI_ENABLED` 改为 `"true"`。

## 启用 Canal

Canal 默认不启动。如需启动：

```bash
docker compose --profile canal up --build
```

同时把 backend 的 `LOCALHUB_CANAL_ENABLED` 改为 `"true"`。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend
docker compose down
docker compose down -v
```
