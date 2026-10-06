# P106 D-2b + AccountMappingRule 接入待决策备忘（2026-10-06）

> **状态**：🚧 **待决策** — 只取证与列出选项，未改生产代码
> **来源**：P106 §10.1 D-2b 阻塞项（`t_sys_config` / `t_audit_log`）+ P110 Phase 2「AccountMappingRuleEntity 接入真实发票品名→科目匹配流程」候选项
> **铁律约束**：本备忘仅作为 P106 V1.81 版账户配置语义与 AccountMappingRule 接入入口 issue；代码改动必须等三选一有人选定

---

## 1. T_sys_config — 《会计年度起始两条的「全局还是按企业」》

实测：`t_sys_config` 5 条种子数据：

| config_key | config_type | config_value | 备注 |
|---|---|---|---|
| company.name | system | 慧财财务 | 必然全局 |
| company.tax_id | system | | 应全局（一个企业） |
| accounting.start_year | accounting | 2026 | ⚠️ D-2b 讨论项 |
| accounting.start_month | accounting | 1 | ⚠️ D-2b 讨论项 |
| accounting.default_currency | accounting | CNY | ⚠️ D-2b 讨论项，同属会计科目设定范畴 |

`t_sys_config` **不含 `enterprise_id` 列**；目前按全局共享使用。本表目前没有触发 P106「D-1 废弃 `tenant_id`」的双列问题，但 D-2b 的语音是:

### 决策点 🔵 D-2b.1 — accounting.start month/year/currency 是否按企业分立

| 选项 | 语义 | 数据影响 | 产品期望（推断） | 代价 |
|---|---|---|---|---|
| A 全局共享不动 | 所有企业共用同一建账期起点 | 零 DDL、零数据迁移 | 单一部署/单账套预期下可接受 | 「年年新增企业」会以同一年度初始化，需人工改值 |
| B 收编到 `t_enterprise` 表 | 改为 3 列挂到企业记录上 | V-NEW migration：新增 `start_year/start_month/default_currency` 3 列、`UPDATE t_enterprise SET ... FROM t_sys_config`、删 key | 企业实体模型更自然（company_name、tax_id 同步迁过去） | 两条查询路径改写（SysConfigServiceImpl），UI 参数位置变 |
| C 保持 t_sys_config 但补 enterprise_id | 按租户隔离配置 | migration 加列/唯一唯 index 按(enterprise_id,config_key) | 共享的模型 + 新企业的实例拷贝 | 老价值承接现状 (5 行) → 所有企业每行 3 份 = 5×N 行 |

门禁全绿需二进制跳过：B 是 D-2b 本意但改程最重；C 是 JulianneLIQ 最小；A 是「已验证可行、延后」

---

## 2. T_audit_log — 历史记录归属问题

实测：`t_audit_log` 当前**不含 `enterprise_id` 列**，`created_at` 改 `updated_at` 列无记录；`module/operation/entity_type` 以非空方式组织。

D-2b 已明确阻塞：历史记录一旦加列，**旧数据的 `enterprise_id` 填什么**？（企业归属未能从 module/entity_type 稳定反推）

### 决策点 🔵 D-2b.2 — audit_log 企业归属

| 选项 | 语义 | 迁移形态 | 风险 |
|---|---|---|---|
| A 永不加列（安全保底） | 审计日志作为平台级：查询时段内当前操作人自带上下文企业 | V-NEW Lite：只加 `operator_enterprise_id` 到写入路径，不对历史行回填（漏填为 null）| 历史审计记录归属不可分 |
| B 加列 + 标记 NULL（当前做法） | migration:`ALTER TABLE t_audit_log ADD COLUMN enterprise_id INT NULL`，老行填 UNKNOWN=-1 | 历史行可区分「已知」 vs「历史」 | 查询必须 OR 过滤 NULL |
| C 加列 + 写入时强制填当前企业 | 新记入条必带；v-b 旧条回填 UNKNOWN=-1 | 按行主键 SELECT+UPDATE | 历史可读性较 OK |

---

## 3. AccountMappingRuleEntity 的接入需要先明确的产品语义

本次 P110 Phase 2 已把 `AccountMappingRuleEntity` 重与对齐 DB 全列，但**零业务调用方**：尚无任何 service 读写它。流程假定：

- 发票品名 → `t_account_mapping_rule` 匹配（`source_type='INVOICE_IN'|'INVOICE_OUT'`）
- 命中优先：`match_pattern` 命名 LIKE/正则 → `target_subject_id` 作为科目
- 未命中：回退默认科目/人工审核

需要产品方答复后才能接到 `SalesInvoiceImportService` 的『parseInvoiceRow → 记账科目路由』阶段。

### 需要确认的问题

- 规则匹配是否只用 `match_pattern`？`source_type` 是按进项/销项分流还是事务层面？
- 命中优先级：多规则符合时按 `rule_code` 还是按加载顺序？是否允许强制覆盖（Quantity vs SET DEFAULT）
- `ai_result` — 字段语义是"命中后把规则的 AI 输出写入助款单"还是"用 AI 决定匹配"？
- 是先问“如果你需要我就也能把 service stub 写出并以具体邮件汇总给老丁确认”作为选项 D（不自动把上下文跳脸）

> **结论**：本任务在代码层面已经不对齐（Phase 2 之后 台账 11 → 4；本次聚会 4 → 0）。三者由此自动以「P110 结清」的纪律收口。

---

## 4. 版本历史

| 版本 | 日期 | 说明 |
|---|---|---|
| V1.0 | 2026-10-06 | 首次记录 D-2b 阻塞点 + AccountMappingRuleEntity 接入待决策 memo（取证完成，未改代码） |