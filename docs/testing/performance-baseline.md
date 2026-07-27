# 📊 慧财财务性能测试基线报告 (Baseline)

**版本：** V1.0 | **生成日期：** 2026-07-27 | **测试环境：** Staging (单节点 Docker Compose)  
**工具：** k6 v0.46+ | **基准场景：** 批量导入 + 报表聚合查询  

---

## 1. 性能指标定义

| 指标名称 | 定义 | 阈值告警 | 基线值 |
|----------|------|---------|--------|
| `batch_import_p95_ms` | 批量导入10张发票的总响应时间 P95 | > 2s (warning) / > 5s (block) | TBD (首次运行后确定) |
| `subject_balance_p95_ms` | 科目余额表查询 P95 | > 1.5s (warning) / > 3s (block) | TBD |
| `balance_sheet_p95_ms` | 资产负债表查询 P95 | > 3s (warning) / > 6s (block) | TBD |

> 💡 **阈值说明**：P95 超过阈值触发 GitHub Actions warning；连续两次 PR 导致 P95 增长 >20% 则阻断 merge。

---

## 2. 测试命令

```bash
# 批量导入基准 (1 VUS, 30s)
k6 run --env BASE_URL=http://staging.huicai.example.com \
       --env AUTH_TOKEN=<valid-token> \
       scripts/performance/batch-import-invoices.js

# 报表查询基准 (5 VU, 30s)
k6 run --env BASE_URL=http://staging.huicai.example.com \
       --env PERIOD=202607 \
       --env AUTH_TOKEN=<valid-token> \
       scripts/performance/month-end-report-queries.js
```

---

## 3. 基线记录区（每次 Release Candidate 前填写）

| 版本/Commit | 测试时间 | batch_import_P95(ms) | subject_balance_P95(ms) | balance_sheet_P95(ms) | 备注 |
|-------------|----------|---------------------|------------------------|----------------------|------|
| v1.0 (初始) | 2026-07-27 | `[待填充]` | `[待填充]` | `[待填充]` | 首次 baseline，需人工在 Staging 运行 |
|             |          |                     |                        |                      |      |

---

## 4. 回归判断规则

CI 脚本 `scripts/performance/check_regression.py` 对比当前 commit 与 baseline JSON 的 P95 值：

- **增长 ≤ 10%** → info 级别评论，不阻断
- **增长 10%-20%** → warning 级别评论，通知 Owner
- **增长 > 20%** → error 级别，**阻断 merge**

---

## 5. 首次 Baseline 采集步骤

1. 配置 GitHub Secrets: `STAGING_URL` + `E2E_TEST_TOKEN`
2. 在 Staging 环境执行 k6 脚本（手动或通过 workflow_dispatch）
3. 运行后 `performance-regression.yml` 会首次创建 `performance-baseline.json`
4. 提取 P95 值填入上表

---

## 6. 后续计划

- [ ] **Sprint F（当前）**：完善 baseline 文档 + CI gate 配置
- [ ] Sprint G：实际运行 k6 获取真实基线值
- [ ] Sprint G+：增加更复杂场景（并发核销、多租户隔离压力）
