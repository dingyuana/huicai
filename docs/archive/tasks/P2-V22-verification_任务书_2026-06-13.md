# P2 任务书：V22 数据库迁移验证 — M4 业务单据自动生成前置

> 日期：2026-06-13 | 任务 ID：P2-V22-VERIFICATION
> 上游文档：`docs/需求分析/01-银行流水智能处理.md` §5 API 列表
> 关联 commit：`3daa958`（M4 业务单据自动生成 + 销售发票导入，V22 落地）
> 风险：🔴 **M4 整套 14K 代码 commit 时没在 PG 容器实跑过，违反"先验证再修复"**

## 目标

在 PostgreSQL 16 容器（`huicai-postgres`，端口 5432，用户 `huicai`）实跑 V22 迁移，验证 3 字段（`generated_doc_id` / `generated_voucher_id` / `generated_at`）已落到 `t_bank_statement` 表，并注册到 `flyway_schema_history` rank 22。**不写新代码、不改 V22 SQL**，纯验证。

## 实施步骤

1. **检查 PG 容器状态**
   ```bash
   docker ps | grep huicai-postgres
   # 若没起，启动：docker start huicai-postgres
   ```

2. **跑 Flyway 迁移**（走应用启动方式，不要手动跑 SQL）
   ```bash
   cd /root/data/disk/huicai/backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=18080 &
   # 等 "Started HuicaiApplication"，或看日志 "Flyway: Successfully applied X migrations"
   # 跑完后 Ctrl+C
   ```

3. **用 psql 验证 3 件事**：
   ```bash
   PGPASSWORD='huicai123' psql -h 127.0.0.1 -U huicai -d huicai
   ```
   - `SELECT installed_rank, version, description, success FROM flyway_schema_history WHERE installed_rank >= 22;` — 应有 1 行 rank=22 成功
   - `\d t_bank_statement` — 列出所有列，验证 3 列存在
   - `SELECT column_name, data_type, column_default FROM information_schema.columns WHERE table_name='t_bank_statement' AND column_name IN ('generated_doc_id','generated_voucher_id','generated_at');` — 应 3 行

4. **若迁移失败**：
   - 报具体错误信息（哪条 SQL、什么错）
   - **不要自己修 SQL**（我方规矩"改代码委 OpenCode"）
   - 把报错丢给我，我决定下一步

5. **幂等性验证**（防回滚无救）：
   - 重跑 `./mvnw spring-boot:run`（V22 已跑过）
   - 验证日志里 V22 显示 `Skipped: already validated` 或不重跑（具体看 Flyway 行为）
   - 再查 `flyway_schema_history` rank 22 仍只 1 行

## 验收标准

1. `flyway_schema_history` rank 22 存在且 success=true
2. `t_bank_statement` 表含 3 新列（generated_doc_id BIGINT / generated_voucher_id BIGINT / generated_at TIMESTAMP）
3. 现有 23 字段未变（P1 V17 加的 10 字段 + V5 13 字段）
4. 应用能正常启动到 `Started HuicaiApplication`（说明 Flyway 整链 OK）
5. 重跑幂等不报 duplicate column

## 不做的事（明确边界）

- ❌ 不改 V22 SQL（即便有错也只 report）
- ❌ 不动 V1-V21
- ❌ 不写新业务代码
- ❌ 不在迁移里 INSERT/UPDATE 演示数据

## 风险

- **Spring 启动可能撞 H2 之外的中间件缺失**（RabbitMQ/Redis/MinIO）—— 此时用 `spring.profiles.active=test-no-mq` 跳开，专注 Flyway
- **PG 容器密码**（已验证）：`huicai123`（应用配置同源：`backend/src/main/resources/application-dev.yml`）
- **PG 容器可能没起**：先 `docker start huicai-postgres`

## 提交

本任务不需新代码 → **不 commit**。验证通过后写一份验证报告 `docs/tasks/P2-V22-verification_验证报告_2026-06-13.md`，我（Hermes）亲自 commit + push。
