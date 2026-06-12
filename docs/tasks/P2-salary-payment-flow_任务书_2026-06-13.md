# P2 任务书：salary_payment 业务闭环验证 — 验收第 10 条

> 日期：2026-06-13 | 任务 ID：P2-SALARY-PAYMENT-FLOW
> 上游文档：`docs/specs/P1-bank-import-classification.md` 验收第 10 条
> 关联 commit：`3daa958`（V22 + AutoGenerationService 14K + review 端点）
> 风险：🟡 **P1 验收第 10 条未真闭环——review() 内 TODO 注释可能没接上**

## 目标

验证 P1 验收第 10 条："salary_payment 分类识别后出纳确认时生成付款单（DRAFT，来源 FROM_BANK_TXN）"。**端到端真跑**：导入 → 分类（含兜底命中 salary_payment）→ review → `t_business_doc` 出现 DRAFT 付款单 + `t_bank_statement.generated_doc_id` 被回填。

## 实施步骤

### Step 0：先看代码现状（最重要）

**先读 3 个文件**确认逻辑是否真接上：

```bash
cd /root/data/disk/huicai
echo "=== AutoGenerationService.java 看 salary_payment 分支 ==="
grep -n "salary_payment\|payment_order\|business_doc" backend/src/main/java/com/huicai/module/finance/service/impl/AutoGenerationService.java | head -20
echo "---"
echo "=== BankStatementServiceImpl.java 看 review() 方法 ==="
grep -n "review\|TODO\|salary_payment\|generated_doc_id" backend/src/main/java/com/huicai/module/finance/service/impl/BankStatementServiceImpl.java
echo "---"
echo "=== t_business_doc 表是否真存在 ==="
PGPASSWORD='huicai123' psql -h 127.0.0.1 -U huicai -d huicai -c "\d t_business_doc" 2>&1 | head -20
```

**如果 review() 内是 TODO 注释**（即 M4 没接上）：
- 报具体行号和上下文
- **不修**（按用户硬规矩改代码委 OpenCode；我委你修）
- 走 Step 1.5：写修复子任务并立即执行

**如果代码已接上**（M4 真接了）：
- 跳过 Step 1.5
- 直接走 Step 2 端到端

### Step 1.5：（如需要）补 review() → AutoGenerationService 调用

**仅当 Step 0 发现 TODO 时**执行：

1. 在 `BankStatementServiceImpl.review()` 方法（按 commit `1ad0050` 落地）里加：
   ```java
   if ("CONFIRMED".equals(reviewStatus) && "salary_payment".equals(stmt.getClassification())) {
       Long docId = autoGenerationService.generatePaymentOrder(stmt);
       stmt.setGeneratedDocId(docId);
       stmt.setGeneratedAt(LocalDateTime.now());
       bankStatementMapper.updateById(stmt);
   }
   ```
2. 在 `AutoGenerationService` 实现 `generatePaymentOrder(BankStatementEntity stmt)`：
   - 构造 `BusinessDocEntity`（doc_type='PAYMENT_ORDER', status='DRAFT', source='FROM_BANK_TXN', amount=stmt.getAmount(), counter_account=stmt.getCounterAccount(), summary=stmt.getSummary()）
   - 关联 employee（**先查 tenant_id=1 下的默认员工**——若没员工档案则建一条测试员工）
   - INSERT 到 t_business_doc，返回新 ID

3. 写对应单测（mock 模式即可，至少 1 个 happy path + 1 个非 salary_payment 不触发的 negative test）

4. 实跑 `./mvnw test` 验证（**不许编造"通过"**）

### Step 2：端到端真跑（依赖 P2-HTTP-E2E 已完成）

```bash
# 准备 1 条 salary_payment 流水样本
cat > /tmp/salary_txn.csv <<'EOF'
tx_date,amount,summary,counter_account
2026-06-13,15000.00,工资发放,某员工A
EOF

# 走 P1 5 端点
curl -X POST http://127.0.0.1:18080/api/finance/bank-statement/import-csv \
  -F "file=@/tmp/salary_txn.csv"
# 拿返回的 stmt_id

# 看 classification 是否被分类为 salary_payment（兜底启发式命中"工资"关键词）
PGPASSWORD='huicai123' psql -h 127.0.0.1 -U huicai -d huicai \
  -c "SELECT id, classification, review_status FROM t_bank_statement ORDER BY id DESC LIMIT 5;"

# review 单条
curl -X POST "http://127.0.0.1:18080/api/finance/bank-statement/review/{id}?action=confirm"
```

**期望**：
- 该流水 `classification = 'salary_payment'`
- `review_status = 'CONFIRMED'`
- `generated_doc_id IS NOT NULL`
- `t_business_doc` 多 1 行（`doc_type='PAYMENT_ORDER'`, `status='DRAFT'`, `source='FROM_BANK_TXN'`）

### Step 3：写验证报告

`docs/tasks/P2-salary-payment-flow_验证报告_2026-06-13.md`：
- 贴 psql 实际查询结果
- 贴 curl 实际响应
- 标注：是否走 Step 1.5 修复 + 修复 commit
- 标注：端到端是否真闭环

## 验收标准

1. **代码层**：review() 内不再有 salary_payment 相关的 TODO 注释（Step 1.5 视情况）
2. **数据层**：1 条 salary_payment 流水 review 后，`t_business_doc` 真多 1 行 DRAFT，`t_bank_statement.generated_doc_id` 真非 NULL
3. **单测层**：AutoGenerationService 单测全绿（若 Step 1.5 触发）
4. **可重复性**：再 import 1 条非 salary_payment 流水，review 后 `generated_doc_id` **仍为 NULL**（negative test 验证逻辑分支正确）

## 不做的事（明确边界）

- ❌ 不改 P1 业务核心代码（review 端点逻辑已 commit，**只**补 salary_payment 分支）
- ❌ 不动 Flyway SQL
- ❌ 不改前端
- ❌ 不引入新表
- ❌ 不委 OpenCode 写新业务代码（如果 Step 1.5 触发，由我亲自 patch，**小改动 < 50 行可破例**——用户已开过"小改可亲自"的口子）

## 风险

| 风险 | 应对 |
|---|---|
| t_business_doc 表缺字段（如 source 列）| 报具体缺什么；不修 SQL，挂账给 M3 |
| 员工档案无默认 | dev 环境建 1 条测试员工 |
| M4 接错分支（非 salary_payment 也触发）| Step 3 negative test 必跑 |

## 提交

- Step 1.5 触发 → OpenCode/我 patch 后 commit（按规矩）
- Step 2 完成 → 写验证报告，我 commit
- Step 3 完成 → 全部完成，状态通知用户
