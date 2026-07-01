---
name: three-phase-loop
description: >-
  规划→开发→测试 三点式闭环。用户提交需求后自动执行 PLAN→BUILD→VERIFY
  三阶段，每阶段有明确的检查点和输出物。V2.0 升级：微循环 + Contract-First
  + 可执行断言 + 自动 Gate。
version: 2.0.0
author: Hermes
trigger: 任何新需求、功能请求、修复请求
---

# Three-Phase Loop Engineer v2.0

## 核心变化（V1 → V2）

| 维度 | V1（旧） | V2（新） |
|------|---------|---------|
| 循环粒度 | 整功能一次循环（小时级） | 微循环（5-15 分钟），每步可回退 |
| 验证时机 | BUILD 完才 VERIFY | Contract-First：先写契约（测试/断言）再实现 |
| SPEC 可执行 | Markdown 文档（人类读） | YAML 契约块（机器可验证） |
| 自动化门禁 | 手动确认 | 内置 5 道 Gate + 可选 CI |
| 容错 | 踩坑后手动修 | 每个循环前打 checkpoint，失败回滚 |
| 并行度 | 串行 | 可并行：主循环 + 契约检查 agent |

---

## 微循环结构

```
                   ┌──────────────────────────────┐
                   │  1 个微循环 = 1 个可验证断言  │
                   │  （5-15 分钟）                │
                   │                              │
                   │  ① 写契约（测试/断言/SQL）   │
                   │  ② 写刚好通过的实现          │
                   │  ③ 验证 + Git checkpoint     │
                   └──────┬───────────────────────┘
                          │
                  ┌───────┴───────┐
                  │   通过？       │
                  ├── YES ─→ 下一个微循环
                  └── NO  ─→ git reset --hard + 缩小范围重试
```

### 契约（Contract）怎么写

契约 = 一段可自动执行的代码/断言，定义了"这个微循环验证什么"。

**契约三选一**（按优先级）：

| 类型 | 适用范围 | 示例 |
|------|---------|------|
| **Contract Test** | Java 后端 | `assertEquals(30, doc.getStatus()); // 期望 DRAFT` |
| **DB Query Assert** | 数据迁移/状态变更 | `SELECT status FROM t_business_doc WHERE id=X → DRAFT` |
| **API Contract** | 端到端 | `POST /api/v1/business-docs/{id} → 200 + status=DRAFT` |

**先写契约，再实现。** 契约通过了就说明这个微循环完成了。

---

## V2 全流程

```
用户提交需求
  ↓
① PLAN ── 拆成 N 个微循环 + 写 SPEC
  ↓ 确认
② BUILD ── 循环执行 [Contract → Implement → Verify → Commit]
  ↓ 每个微循环 5-15 分钟
③ VERIFY ── 全量回归 + 跨实体确认
  ↓
✅ 汇报 或 🔄 回到①
```

---

## ① PLAN 阶段（宏观规划）

### 输出物

1. `docs/specs/P{编号}-{功能名称}.md` — SPEC 文档
2. **任务拆解清单** — N 个微循环的列表，每个微循环后附 ONE LINE CONTRACT

### SPEC 末尾必须加 YAML 契约块

```yaml
---
contracts:
  - id: P34-M3-C1
    description: 业务单据创建后状态为 DRAFT
    type: unit_test
    target: OutputInvoiceStateMachineServiceImplTest
    assertion: doc.getStatus() == "DRAFT"
  - id: P34-M3-C2
    description: 凭证创建后关联 businessDocId
    type: db_query
    assertion: |
      SELECT business_doc_id FROM t_voucher 
      WHERE source_doc_id = :invoiceId → NOT NULL
  - id: P34-M3-C3
    description: 核销结算字段对齐
    type: api
    endpoint: GET /api/v1/business-docs/{id}
    expected: data.settledAmount == 0
```

### 任务拆解模板

```
SPEC P34-M3：
├── 微循环 1：改 createBusinessDocFromInvoice status → DRAFT
│   └─ 契约：doc.getStatus() == "DRAFT"
├── 微循环 2：改 generateVoucherFromInvoiceDirect 写 voucherId
│   └─ 契约：voucher.businessDocId == doc.id
├── 微循环 3：populateVoucherNos 兜底
│   └─ 契约：VO.voucherNo == inv.voucherNo
└── 微循环 4：时间格式 yyyy-MM-dd HH:mm
    └─ 契约：response.createdAt 不含 "T" 无秒
```

**每个微循环 ≤ 30 分钟。如果一个微循环预估超过 30 分钟 → 拆。**

### 检查点

```
- [ ] 微循环清单 ≤ 8 个（超过说明粒度太粗）
- [ ] 每个微循环有明确契约（人工可解释、机器可运行）
- [ ] YAML 契约块已追加到 SPEC 末尾
- [ ] 状态原则确认：自动创建的单据只能到 DRAFT
```

---

## ② BUILD 阶段（微循环主体）

每个微循环固定 5 步：**Contract → Implement → Verify → Gate → Commit**

```
========================================
微循环 N：{契约描述}
========================================

┌─────────┐
│ Step 1  │ 写契约
│         │ 先在测试文件里写失败断言
│         │ 或者写 DB Query / API Contract 文件
│         │ 执行一次 → 确认它是红的（Contract Red）
└─────────┘
     ↓
┌─────────┐
│ Step 2  │ 写实现
│         │ 刚好通过契约的最小代码
│         │ 只改 SPEC 约定的文件
│         │ 不改无关代码
└─────────┘
     ↓
┌─────────┐
│ Step 3  │ 验证
│         │ mvn test -Dtest="XxxTest"
│         │ 或者在 DB / API 上验证契约
│         │ 契约变绿（Contract Green）
└─────────┘
     ↓
┌─────────┐
│ Step 4  │ Gate 检查
│         │ □ 只改了 SPEC 约定的文件
│         │ □ 编译无错误
│         │ □ 契约通过
│         │ □ 没有多余的代码
│         │ □ 测试覆盖率没降
│         │
│         │ 任意一项 FAIL → 缩小重试或暂停
└─────────┘
     ↓
┌─────────┐
│ Step 5  │ Git Commit
│         │ git add + git commit -m "P34-M3-C1: ..."
│         │ 每个微循环单独提交
│         │ 失败时 git reset --hard HEAD~1 回滚
└─────────┘
     ↓
   进入下一个微循环
```

### 契约红 → 绿的生命周期

```
Step 1: 写契约          → 执行 → RED    （确认测试是有效的）
Step 2: 写实现          → (编译)         （刚够通过）
Step 3: 验证            → 执行 → GREEN  （契约通过）
                       ↓
              如果依然是 RED：
              → 契约太严格？检查契约定义
              → 代码写错了？缩小范围重试
              → 理解错了？回 PLAN 阶段修正 SPEC
```

### 🔴 Pitfall：不要一次编太多代码

**坏习惯**：
```java
// 一次改了 5 个方法，然后 mvn test → 3 个失败
// 不知道哪个方法导致的
```

**好习惯**：
```
微循环 1: 只改 createBusinessDocFromInvoice.status → DRAFT
微循环 2: 只改 generateVoucherFromInvoiceDirect 写 voucherId
...
```

每步可回退。多步混在一起就回退不了了。

---

## ③ VERIFY 阶段（最终验收）

### 必须跑的三层验证

| 层 | 内容 | 命令 |
|----|------|------|
| 回归 | 全量测试，确认无破坏 | `mvn test` |
| 契约 | SPEC 中所有 YAML contracts 逐条执行 | `run-contracts.py docs/specs/P34.md` |
| 跨实体 | DB 级别链路段 | 查发票→业务单据→凭证 |

### VERIFY Gate

```
- [ ] 全量测试通过（Failures=0）
- [ ] 所有 YAML contracts 执行通过
- [ ] 发票→业务单据→凭证 DB 链路完整
- [ ] 无 CircularReference / 配置冲突
- [ ] 迁移已应用（如果涉及 Flyway）
```

### 旧数据修复检查

如果涉及旧数据兼容：

```
- [ ] 新建数据：走新代码路径 → 关联完整
- [ ] 旧数据：兜底策略有效 → VO 层显示正确
- [ ] 如果旧数据丢失 → rebuild SQL 已执行
```

---

## VERIFY Pitfalls（V2 新增）

### 🔴 Contract-First 陷阱：契约写得太复杂

```
❌ 坏契约：
  UUID 生成规则 + 状态机 5 步 + 3 个表 JOIN

✅ 好契约：
  SELECT status FROM t_business_doc WHERE id = X → "DRAFT"
```

**规则**：每个契约只能验证一件事。如果一个契约需要 3 次 DB 查询 → 拆成 3 个微循环。

### 🔴 灰烬陷阱：回滚失败后修复方向错误

```
微循环 N 失败 → git reset --hard HEAD~1
               ↓
        重新理解契约
               ↓
        是代码错了 → 缩小范围重试
        是契约错了 → 先改契约再重试
```

**关键**：不要原地重试。失败后的第一件事是**缩小范围**，不是"再试一次一样的"。

### 🔴 循环膨胀中毒

```
每轮循环都往里加"顺便修一下..."的代码
→ 循环永远不结束
```

**解法**：每个微循环只能改 1 个契约对应的文件。用户的"顺便"需求 → 写到"待办"中，下次再来。

---

## 可选升级：CI Contract Runner

在项目根目录加 `.github/workflows/contracts.yml`：

```yaml
name: Contract Verification
on: pull_request
jobs:
  contracts:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Validate SPEC contracts
        run: python scripts/validate-contracts.py docs/specs/
```

配套脚本 `scripts/validate-contracts.py` 读取 SPEC 末尾的 YAML 块，逐条断言执行。

---

## 使用示例（基于本轮实战）

```
老丁：FPS2025120008 业务单据状态不对，凭证号未生成
  ↓
① PLAN：3 个微循环
  ├─ MC1: status APPROVED → DRAFT（契约: doc.status == "DRAFT"）
  ├─ MC2: voucherNo 兜底显示（契约: VO.voucherNo == inv.voucherNo）
  └─ MC3: 时间格式去秒（契约: createdAt 不含 "T" 无秒）
  ↓
② BUILD：
  微循环 1: 写 testRed → 实现 → verifyGreen → commit ✅
  微循环 2: 写 testRed → 实现 → verifyGreen → commit ✅
  微循环 3: 写 testRed → 实现 → verifyGreen → commit ✅
  ↓
③ VERIFY：
  mvn test   → 全量 42/42 ✅
  DB 链路    → 发票→业务单据→凭证 ✅
  旧数据修复 → invoiceId 回填 ✅
```