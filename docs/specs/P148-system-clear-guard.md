# P148 — 清库端点加固（确认令牌 + 按企业隔离）

> 关联需求：REQ-2026-164
> 状态：✅ 已实施并验证（2026-10-30）
> 裁定：老丁 2026-10-30 —— 「②清掉这批演练单据重来，补确认令牌 + 按企业隔离」

---

## 一、事故回溯

### 1.1 现象

用户报障「山东 C（企业 4）凭证页面看不到凭证」，但业务单据与银行流水都在。

### 1.2 取证时间线（生产日志 + 数据库双向坐实）

| 时间（UTC+8） | 事件 | 证据 |
|---|---|---|
| 10-29 05:17 | 企业 4 制单，生成凭证 id 103–132 | `t_audit_log` 73 条 `CREATE/Voucher`（ent=4） |
| 10-30 05:10:58 | 非管理员调 `clear-vouchers` → **403 被拒** ✅ | `端点权限拒绝: POST /api/v1/system/clear-vouchers 需要=system:clear` |
| 10-30 **05:11:24** | 管理员调同一端点**成功** | `清空凭证: entries=52, vouchers=26, balance=3, cash_flow=0` |
| 之后 | 企业 4 的 202407 期间从 `closed` 退回 `open` | `t_period` |

### 1.3 后果（数据库实测）

```
dangling voucher refs = 26   -- t_business_doc.voucher_id 指向已不存在的凭证
t_voucher 现存 31 张, id 段 103-132 整段缺失
```

🔴 **审计日志零条 DELETE** —— 全部删除都不经应用层逻辑删除，本次是物理删除、无回收站。

### 1.4 根因

1. `SystemClearController` 的 **9 个清库端点全部是无 WHERE 的全表 DELETE**，一次调用跨**所有账套**生效；
2. **无任何二次确认** —— 持权者一次 POST 即不可逆；
3. `t_business_doc.voucher_id` **无外键约束**，删凭证不回收单据引用 ⇒ 静默产生悬空引用。

**权限收口本身没有问题**：`system:clear` 仅 ADMIN 持有（`V163` + 真库实测）。问题在于「持权者一次误触 = 全库不可逆丢失」。

---

## 二、方案

### D-1 确认令牌

所有清库端点必须携带与**动作一一对应**的 `confirm` 字面量：

| 端点 | 所需令牌 |
|---|---|
| `/clear-vouchers` | `CLEAR_VOUCHERS` |
| `/clear-bank-statements` | `CLEAR_BANK_STATEMENTS` |
| `/clear-invoice-records` | `CLEAR_INVOICE_RECORDS` |
| `/clear-report-data` | `CLEAR_REPORT_DATA` |
| `/clear-business-docs` | `CLEAR_BUSINESS_DOCS` |
| `/clear-receivables` | `CLEAR_RECEIVABLES` |
| `/clear-payables` | `CLEAR_PAYABLES` |
| `/clear-settlements` | `CLEAR_SETTLEMENTS` |
| `/clear-all` | `CLEAR_ALL` |

缺失 / 空串 / 不匹配 / 用错动作的令牌 ⇒ **400**，且 **JdbcTemplate 零调用**。

🔴 **令牌按动作隔离而非全局一个**：用 `CLEAR_ALL` 的令牌不能清凭证。否则“确认一次，任意动作都能执行”等于没确认。

### D-2 按企业隔离

- 未传 `enterpriseId` 时取 `EnterpriseContextHolder.get()`；
- **上下文为空 ⇒ 400 拒绝，绝不退化为「清全部企业」**；
- 上下文与请求企业冲突 ⇒ **403**；
- 每条 SQL 都带 `WHERE enterprise_id = ?`。

🔴 **为什么上下文为空必须拒绝而非清全部**：GUC NULL 是 P127/P139 反复踩到的形态。若「空上下文 = 清全部」，任一运维/测试路径漏设上下文就会静默跨企业清库 —— 这正是本次事故的等价形态。宁可 400 让调用方显式补参。

---

## 三、实现

| 文件 | 变更 |
|---|---|
| `SystemClearService.java` | **新增**。收口 9 个动作，`resolveAndVerify()` 统一做两道校验（在任何 DML 之前） |
| `SystemClearController.java` | 重写为**薄委托**，接收 `confirm` / `enterpriseId` 参数，去掉 `@Transactional`（事务边界下沉到 service） |
| `SystemClearServiceGuardTest.java` | **新增** 10 例守卫 |
| `SystemClearControllerIntegrationTest.java` | 同步改造：断言改为「按企业剩余行数」+ 新增跨企业不变量 |

### 关键设计点

1. **校验位置**：`resolveAndVerify()` 在两个校验都通过**之后**才返回 enterpriseId，调用方拿到 id 才开始执行 DML ⇒ 结构上保证「没确认就一行都不动」。
2. **返回值为可审计摘要**（`message`）而非总删除行数 —— 新语义下“删了多少”不再跨企业可比，摘要里写明「企业 N 清空 X」更能支撑事后审计。
3. **穷尽式 switch**：`runActionWith()` 的 switch 覆盖全部枚举值，新增动作不登记 ⇒ default 分支 `fail()`，防漏校验静默复发。

---

## 四、验证

### 4.1 守卫清单（10 例全绿）

| 编号 | 断言 |
|---|---|
| AT-164-1 | confirm 缺失 ⇒ 400 且零 SQL |
| AT-164-2 | confirm 空串 ⇒ 400 且零 SQL |
| AT-164-3 | confirm 不匹配（大小写/他动作令牌/yes/true）⇒ 400 |
| AT-164-4 | 令牌按动作一一对应，不可跨动作使用 |
| AT-164-5 | 上下文空且未传参 ⇒ 400，**不退化为清全部** |
| AT-164-6 | 上下文与请求企业不一致 ⇒ 403 且零 SQL |
| AT-164-7 | 上下文空但显式传 enterpriseId ⇒ 放行 |
| AT-164-8 | 上下文与请求一致 ⇒ 放行 |
| AT-164-9 | 每条 DELETE 必带 WHERE + enterprise_id（SQL 文本扫描） |
| AT-164-10 | 全部 9 个动作都被令牌校验覆盖（穷尽式） |

### 4.2 反证（门禁必须能变红）

| 编号 | 注入 | 结果 |
|---|---|---|
| F-1 | 移除两处令牌校验 | **恰 5 例**转红（AT-164-1/2/3/4/10） |
| F-2 | 上下文空时 `return null`（复刻旧行为） | **恰 AT-164-5** 转红 |
| F-3 | 凭证删除改回 `DELETE FROM t_voucher`（无 WHERE） | **恰 AT-164-9** 转红 |

三组均还原即绿。

### 4.3 全量回归

L1 与定向回归见 AGENTS §0 记录。

---

## 五、数据处置（老丁裁定选项②：清掉重来）

清理前已 `pg_dump` 快照至 `tmp_backup/huicai_before_cleanup_*.sql`（约 1 MB）。

| 表 | 企业 4 清理量 |
|---|---|
| `t_business_doc` | 26 |
| `t_bank_statement` | 26 |
| `t_bank_journal` | 5（解绑，未删） |
| 其余子表（核销/对账/账龄） | 0 |

**验证**：悬空引用 **26 → 0**；企业 1 的 20 单据 / 29 凭证**原样保留**（未误伤）。

🔴 **踩坑登记**：首次用 `docker exec psql` + heredoc 执行时**全程无报错但零生效**。排查发现 `t_business_doc` 的 `relforcerowsecurity=t`，但 cleanup 会话是**超级用户**（绕过 RLS）—— 真正原因是 heredoc 经 `docker exec -i` 传递时未按预期逐条执行。改**写 SQL 文件 + 重定向**后正常，且每步打印 `\echo` 便于定位。判据：批量 DDL/DML 必须**逐步可观测**（`\echo` + 事务内验证 SELECT），否则「无报错」不等于「已执行」。

---

## 六、已知局限（如实登记）

1. **清库仍为物理删除**，无回收站、无撤销。本 SPEC 只缩小爆炸半径并使其可审计，**不等于“清库变安全了”**。
2. **`t_business_doc.voucher_id` 仍无外键约束**（违反项目铁律 #12「财务表全部逻辑删除」的衍生问题：逻辑删除下外键本就不触发，见 REQ-2026-150）。历史悬空引用需另行清理，本次未加约束。
3. **确认令牌是防误触而非防恶意** —— 知道端点的人可自行传入令牌。真正的边界仍是 `system:clear` 权限。
4. **前端未同步改造**：当前无 UI 调用这些端点；若将来加 UI，必须带令牌与当前企业，不得写成全局按钮。
5. 上下文与请求企业冲突时返回 **403**（而非 400），因语义是“越权”而非“参数错”。
