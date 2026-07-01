---
name: three-phase-loop
description: 规划→开发→测试 三点式闭环。用户提交需求后自动执行 PLAN→BUILD→VERIFY 三阶段，每阶段有明确的检查点和输出物。
version: 1.0.0
author: Hermes
trigger: 任何新需求、功能请求、修复请求
---

# Three-Phase Loop Engineer

## 核心规则

**当用户提交一个新需求时，强制进入三阶段循环：**

```
提交需求
  ↓
① PLAN ── 写/完善 SPEC + 验收标准
  ↓        确认：用户认可了吗？
② BUILD ─ 按 SPEC 实现代码
  ↓        确认：只做了 SPEC 约定的事吗？
③ VERIFY ─ 正向+负向+跨实体验证
  ↓        确认：验收标准全满足了吗？
  ↓
完成汇报 或 回到①
```

**每阶段结束必须等待用户确认才进入下一阶段。**

---

## ① PLAN 阶段

### 输出物

1. `docs/specs/P{编号}-{功能名称}.md` — SPEC 文档
2. 验收标准清单（≤8 条，在 SPEC 末尾）

### SPEC 必须包含

```markdown
## 目标
[一句话描述做什么]

## 不做
[明确排除什么]

## 数据模型
[Entity 变化 / 字段变化 / 迁移]

## 核心逻辑
[关键算法 / 状态转换 / 触发条件]

## 验收标准
- [ ] [验收条件1]
- [ ] [验收条件2]
```

### 检查点——验证 SPEC 完整性

| 检查项 | 说明 |
|--------|------|
| 问题定义 | 为什么做这件事？ |
| 范围边界 | 明确哪些不做 |
| 数据变更 | Entity/DB 改动清单 |
| API 变更 | 端点签名 |
| 验收标准 | 可客观验证的条件 |
| 测试策略 | 正向+负向+跨实体 |
| 状态原则 | 自动创建的单据是否只能到 DRAFT？ |

### ⚠️ PLAN Pitfall：不要替用户决定删功能

当用户报告一个 bug（如"业务单据状态不对"）时，**只改用户说了有问题的部分**，不要衍生出"既然改了这里顺便重构流程"。

正确做法：
- 用户说"状态不对" → 改状态值 ✅
- 用户没说"去掉凭证自动生成" → ❌ 不要提议去掉

先列最小改动方案，等用户确认后再看是否还有延伸需求。

### 向用户确认

```
**① PLAN 完成。** SPEC 已写入 docs/specs/，验收标准：
1. [条件1]
2. [条件2]

请确认是否按此方案进行 → 进入② BUILD。
```

---

## ② BUILD 阶段

### 铁律

1. **只改动 SPEC 约定的文件** — 不移除无关代码、不改格式、不重构
2. **每次都跑编译验证** — `mvn compile -q` 或等效
3. **只改 SPEC 范围的代码** — 超出范围先改 SPEC 再改代码

### 检查点——commit 前核实

```bash
git diff --stat HEAD              # 改了什么
# 每个文件问：这能对到 SPEC 的某一条吗？
```

### 向用户确认

```
**② BUILD 完成。** 改动清单：
- [文件1]: [改动说明]
- [文件2]: [改动说明]

编译验证通过。
```

---

## ③ VERIFY 阶段

### 必须跑的三层验证

| 层 | 内容 | 命令 |
|----|------|------|
| 正向 | SPEC 约定的 full path 能走通 | `mvn test -Dtest=...` |
| 负向 | 不该做的确实被拒绝 | 状态拒绝 / 错误码 |
| 跨实体 | 关联数据一致 | 查 DB / VO 字段 |

### 正向测试

```bash
# 1. 跑受影响的测试类
mvn test -Dtest="XxxServiceImplTest"
# 期望: Tests run: N, Failures: 0, Errors: 0

# 2. 跑全量测试（确认无回归）
mvn test
# 期望: Failures: 0（Errors 保持基线）
```

### 负向测试要求

每个状态机方法必须有至少一个负向断言：
- 非法状态 → BusinessException
- 重复操作 → BusinessException
- 缺少参数 → BusinessException

### 跨实体验证（DB 级别）

对于发票→业务单据→凭证的关联：

```sql
-- 1. 发票 → 业务单据：docId/docNo/voucherId/voucherNo 正确填写
SELECT doc_id, doc_no, voucher_id, voucher_no FROM t_output_invoice WHERE id = ?

-- 2. 业务单据 → 凭证：voucherId/voucherNo + status=DRAFT
SELECT voucher_id, voucher_no, status FROM t_business_doc WHERE invoice_no = ?

-- 3. 凭证 → 发票/业务单据：sourceDocId + businessDocId 正确
SELECT source_doc_id, source_doc_type, business_doc_id FROM t_voucher WHERE id = ?
```

### 🔴 VERIFY Pitfall：Flyway 迁移未应用的"透明"陷阱

当 BUILD 阶段提交了新的 Flyway 迁移文件（`V${n}__*.sql`），但在应用中验证时发现 DB 没有变化：

**根因**：迁移文件 commit 后**不会被自动执行**。应用（特别是 Docker 部署的）必须重新构建 + 重启，Flyway 才能在启动时运行新迁移。

**验证方法**（BUILD 阶段完成后必须执行）：

```bash
# 1. 确认迁移在 git 中
git diff --stat HEAD 或 git log --oneline -3

# 2. 确认应用已重启（Docker 环境）
docker ps --filter name=huicai-backend --format '{{.Status}}'

# 3. 确认迁移已应用
psql -h localhost -U huicai -d huicai -c "
SELECT version, description, success FROM flyway_schema_history ORDER BY version DESC LIMIT 5;"
# 最新版本应 >= 刚才提交的 V{n}

# 4. 确认字段存在
psql -h localhost -U huicai -d huicai -c "
SELECT column_name FROM information_schema.columns
WHERE table_name='t_business_doc' AND column_name IN ('invoice_id','reversed_from');"
```

**修复**：如果迁移未应用 → 重建 Docker 镜像 + 重启容器：
```bash
docker build -t huicai_backend ./backend
docker rm -f huicai-backend
docker run -d --name huicai-backend --network huicai_huicai-net -p 8000:8000 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres16:5432/huicai \
  ... 其他环境变量 ... \
  huicai_backend:latest
```

如果 Flyway 迁移失败（SQL 错误）：
1. 修复 migration SQL 文件
2. `psql -c "DELETE FROM flyway_schema_history WHERE version='{n}';"` 删除失败记录
3. 重建镜像 + 重启容器

### 向用户汇报

```
**③ VERIFY 完成。**

| 项目 | 结果 |
|------|------|
| 正向测试 | N/0/0 |
| 全量测试 | N/0/E |
| 跨实体验证 | ✅ 发票→业务单据→凭证 链路完整 |

**验收标准达成情况：**
- [条件1] ✅
- [条件2] ✅
```

---

## 完整循环示例

用户：发票审核后要同时创建业务单据和凭证

```
① PLAN:
   写 SPEC → 确认验收标准 → 用户确认
   ↓
② BUILD:
   改 OutputInvoiceStateMachineServiceImpl.confirm() → 编译通过 → 用户确认
   ↓
③ VERIFY:
   跑 mvn test → 查 DB 链路段 → 汇报
   ↓
   ✅ 完成 或 🔄 回到①修 SPEC
```

### 🔴 VERIFY Pitfall：旧数据 voucher_id 为 NULL 时的兜底策略

当发票的 `voucher_id` 是 NULL（旧数据，`markVouchered` 在 V72 之前执行），但 `voucher_no` 有值时：

**根因**：V72 migration 之前生成的凭证，`voucher_id` 列不存在。后来加了该列但旧数据未回填。

**修复策略**：
1. **后端 VO 层兜底**：`populateVoucherNos()` 中增加 fallback — 如果 `voucherId` 为空但 `invoiceId` 有值，从发票表查 `voucherNo`，再通过 `voucherNo` 反向查 `t_voucher` 获取 `status`
2. **前端判断**：用 `!doc?.voucherNo` 而不是 `!doc?.voucherId` 判断是否显示"生成凭证"按钮
3. **旧数据重建**：如果业务单据表被清空（如 V74 DROP TABLE 误删），从发票表 `doc_id` 字段反向重建

**关键代码模式**：
```java
// BusinessDocServiceImpl.populateVoucherNos() 兜底逻辑
if (vo.getVoucherId() == null && vo.getInvoiceId() != null) {
    OutputInvoiceEntity inv = outputInvoiceMapper.selectById(vo.getInvoiceId());
    if (inv != null && inv.getVoucherNo() != null) {
        vo.setVoucherNo(inv.getVoucherNo());
        // 通过 voucherNo 反向查 voucher 表获取 status
        VoucherEntity v = voucherMapper.selectOne(
            new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getVoucherNo, inv.getVoucherNo())
                .last("LIMIT 1"));
        if (v != null) vo.setVoucherStatus(v.getStatus());
    }
}
```

### 🔴 VERIFY Pitfall：Docker 部署时 Controller 路径不匹配

前端 axios baseURL 是 `/api/v1`，API 调用路径是 `/business-docs/page`，最终请求 `POST /api/v1/business-docs/page`。

**常见错误**：
- 手动 curl 测试时用了 `/api/v1/finance/business-docs/page`（多了 `/finance`）→ 404/500
- Controller `@RequestMapping("/api/v1/business-docs")`，不是 `"/api/v1/finance/business-docs"`
- 前端 `src/api/modules/businessDoc.ts` 中的路径必须与 Controller 的 `@RequestMapping` 一致

**验证方法**：
```bash
# 正确路径
curl -X POST 'http://localhost:8000/api/v1/business-docs/page' \
  -H 'Authorization: Bearer TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"current":1,"size":10}'
```

### 🔴 VERIFY Pitfall：业务单据数据丢失时的重建

当 `t_business_doc` 表为空但发票表有 `doc_id` 和 `doc_no` 时：

```sql
-- 从发票表反向重建业务单据
INSERT INTO t_business_doc (
    doc_no, doc_type, doc_date, period, amount, status,
    customer_id, summary, source, voucher_id, voucher_no,
    invoice_no, invoice_id, created_by, created_at,
    settled_amount, unsettled_amount
)
SELECT 
    inv.doc_no, 'INVOICE_OUT', inv.invoice_date, inv.period,
    inv.total_amount,
    CASE WHEN inv.voucher_id IS NOT NULL THEN 'VOUCHERED' ELSE 'DRAFT' END,
    inv.customer_id,
    COALESCE(inv.customer_name, '未知客户') || '-' || COALESCE(inv.invoice_no, ''),
    'INVOICE', inv.voucher_id, inv.voucher_no,
    inv.invoice_no, inv.id, inv.created_by, inv.created_at,
    0, inv.total_amount
FROM t_output_invoice inv
WHERE inv.doc_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_business_doc bd WHERE bd.doc_no = inv.doc_no);
```

## 循环入口

当检测到用户需求时（新功能、bug 修复、重构），自动进入此循环：

```python
def on_new_requirement(req):
    # 1. 判断是否是 SPEC 变更
    if requires_planning(req):
        enter_phase_1_plan(req)
    else:
        # 简单 bugfix（≤5 行、单文件、无架构影响）：直接走②→③
        enter_phase_2_build(req)
```
