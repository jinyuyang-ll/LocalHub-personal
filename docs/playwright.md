# Playwright E2E 测试

## 安装依赖

```bash
cd frontend
npm install
npx playwright install
```

## 运行测试

确保后端已启动：

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
