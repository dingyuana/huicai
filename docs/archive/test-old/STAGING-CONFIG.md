# Staging 环境配置说明

> **版本**：V1.0 | **最后修改**：2026-07-30

## 需要配置的 GitHub Secrets

在仓库 Settings → Secrets and variables → Actions 中，添加以下 Secrets：

| Secret | 说明 | 示例值 |
|--------|------|--------|
| `STAGING_URL` | Staging 环境后端 API 地址 | `https://staging.huicai.example.com` |
| `STAGING_API_TOKEN` | 调用 staging API 的认证 Token（JWT 或 API Key） | `eyJhbGci...` |

## 受影响的 CI Workflow

### performance-regression.yml
- **触发方式**：每周末定时（北京时间周日 06:00）或手动 `workflow_dispatch`
- **配置前状态**：使用硬编码假数据（850ms/420ms/1200ms），从未真实压测
- **配置后行为**：调 `k6` 对 staging 真实执行性能压测，p95 > 3000ms 时 CI 失败

## 配置步骤

1. 部署后端到 staging 环境，记录 URL
2. 生成一个有效的 API Token（JWT）
3. 在 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret**：
   - `STAGING_URL` = staging 后端地址
   - `STAGING_API_TOKEN` = API Token
4. 下次 PR push 或手动触发 `workflow_dispatch` 时，性能测试将自动对 staging 执行

## 验证配置

```bash
# 手动触发一次看是否成功
gh workflow run performance-regression.yml --field scenario=all
```

或在 GitHub Actions 页面点 "Run workflow"。
