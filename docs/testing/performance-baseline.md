# 📊 慧财财务性能测试基线报告 (Baseline)

**版本：** V1.0 | **生成日期：** 2026-07-27 | **测试环境：** Staging (单节点 Docker Compose)  
**工具：** k6 v0.46+ | **基准场景：** 批量导入 + 报表聚合查询  

---

## 1. 性能指标定义

| 指标名称 | 定义 | 阈值告警 | 基线值 |
|----------|------|---------|--------|
| `batch_import_duration_ms` | 批量导入10张发票的总响应时间 (P95) | > 2s (告发) / > 5s (阻断) | TBD (首次运行后确定) |
| `subject_balance_duration_ms` | 科目余额表查询 P95 | > 1.5s (告发) / > 3s (阻断) | TBD |
| `balance_sheet_duration_ms` | 资产负债表查询 P95 | > 3s (告发) / > 6s (阻断) | TBD |
| `tps_batch_import` | 批量导入 QPS | < 5 (告发) | TBD |
| `tps_report_query` | 报表查询 QPS | < 10 (告发) | TBD |

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
| v1.0 (初始) | 2026-07-27 | `[待填充]` | `[待填充]` | `[待填充]` | 首次 baseline |
|             |          |                     |                        |                      |      |
|             |          |                     |                        |                      |      |

---

## 4. 回归判断规则

在 GitHub Actions 性能回归 Job 中，自动对比当前 commit 与 baseline 的 P95 值：

- **增长 ≤ 10%** → info 级别评论，不阻断
- **增长 10%-20%** → warning 级别评论，不阻断但通知 Owner
- **增长 > 20%** → error 级别评论，**阻断 merge**

---

## 5. 后续计划

- [ ] Sprint C: 实际运行 k6 获取真实基线值并填入上表
- [ ] Sprint D: 将性能回归整合进 CI 流水线 (`performance-regression.yml`)
- [ ] Sprint E: 增加更复杂场景（并发核销、多租户隔离压力）
