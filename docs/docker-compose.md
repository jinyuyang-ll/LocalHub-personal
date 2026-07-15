# LocalHub Docker Compose 一键运行

## 前置条件

- 安装 Docker Desktop，确认命令可用：

```bash
docker --version
docker compose version
```

## 启动完整环境

```powershell
Copy-Item .env.example .env
.\scripts\start.ps1
```

启动后访问：

- 前端：http://localhost:5173
- 后端：http://localhost:8081
- MySQL：localhost:3306，root / 123456
- Redis：localhost:6379
- Kafka：localhost:9092

## 启用 AI Key

默认 AI 使用本地 RAG 兜底。如果要调用真实模型：

在 `.env` 中设置 `LOCALHUB_AI_ENABLED=true`、`LOCALHUB_AI_API_KEY`、模型名和 Base URL，然后重新执行启动命令，不需要修改 Compose 文件。

## 启用 Canal

Canal 默认不启动。如需启动：

```bash
docker compose --profile canal up --build
```

同时在 `.env` 设置 `LOCALHUB_CANAL_ENABLED=true`。

## 启用监控

```bash
docker compose --profile monitoring up --build -d
```

Prometheus：http://localhost:9090；Grafana：http://localhost:3000。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend
docker compose down
docker compose down -v
```
