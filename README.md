# 慧财财务 (Huicai Financial)

基于 Web 的企业财务核算系统。

## 技术栈
- 后端：Spring Boot 3.x + MyBatis-Plus + PostgreSQL 16
- 前端：Vue 3 + Element Plus + ECharts
- 中间件：Redis 7 + RabbitMQ + MinIO
- AI：Python FastAPI 微服务

## 快速开始
```bash
docker compose up -d
```

## 项目结构
```
backend/      — Spring Boot 后端
frontend/     — Vue 3 前端
docs/         — 文档
docker/       — Docker 配置
```

## 开发里程碑
- M1: 项目骨架
- M2: 基础管理（科目/期间/权限）
- M3: 财务核心（凭证/账簿/结账）
- M4: 业务单据 + 出纳
- M5: 报表 + 分析