-- V28__fix_main_table_id_identity.sql
-- 修 4 张主表 id 列缺 IDENTITY 的问题
-- 问题: t_voucher / t_business_doc / t_voucher_entry / t_subject 在 V1/V4/V5 建表时只用
--       `id BIGINT PRIMARY KEY` (无 IDENTITY, 无 DEFAULT), 但 Entity 用 IdType.AUTO,
--       导致任何 INSERT 都报 not-null 约束错误。
-- 修复: 把 id 列改为 GENERATED ALWAYS AS IDENTITY, 复制 V23 (t_voucher_template) 的正确写法。
--
-- 现状 (查 PG):
--   t_voucher         | 0 行  | max(id)=0
--   t_business_doc    | 0 行  | max(id)=0
--   t_voucher_entry   | 0 行  | max(id)=0
--   t_subject         | 95 行 | max(id)=2065278535840169986 (snowflake-style 历史数据, 已有人工填的 ID)
--
-- 注意点:
--   1. ALTER TABLE ... ALTER COLUMN ... ADD GENERATED ALWAYS AS IDENTITY
--      如果列已有 DEFAULT (nextval(...)), 需要先 DROP DEFAULT, 再 ADD GENERATED.
--      这 4 张表目前没有 DEFAULT (已查 information_schema), 直接 ADD 即可.
--   2. t_subject 有 95 行现存量, max_id = 2065278535840169986.
--      默认 IDENTITY 序列从 1 开始会与 min_id=100 冲突 (虽然 PG 会允许, 但语义混乱).
--      显式 START WITH (max_id + 1) 保证新 INSERT 不与历史 ID 冲突, 也明确语义.
--   3. 另外 3 张空表 max_id=0, START WITH 1 与 V23 默认行为一致.
--   4. 不重建表, 不破坏现有数据, 不影响 t_voucher_template / t_bank_statement (已有 IDENTITY).

-- 1. t_voucher (0 行)
ALTER TABLE t_voucher
    ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1);

-- 2. t_business_doc (0 行)
ALTER TABLE t_business_doc
    ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1);

-- 3. t_voucher_entry (0 行)
ALTER TABLE t_voucher_entry
    ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 1);

-- 4. t_subject (95 行, max_id = 2065278535840169986, START WITH 2065278535840169987 避免与历史冲突)
ALTER TABLE t_subject
    ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065278535840169987);
