-- V52__fix_t_audit_log_id_identity.sql
--
-- 与 V28 同模式修复: 给 t_audit_log.id 加 GENERATED ALWAYS AS IDENTITY,
-- 解决 IdType.AUTO 在 PG 无 IDENTITY 时 INSERT 报 not-null 约束的问题。
-- V52 触发场景: 对账单 review 后 StatusChangeAspect 写 audit_log 失败,
-- 整个事务回滚导致 autoGenerate 创建的 doc/voucher/prepayment 全部丢失。
-- 详细根因 + 43 张待修复表见 docs/DESIGN.md §10.5。

-- V52 (幂等版本): 给 t_audit_log.id 加 IDENTITY, 避免 IdType.AUTO 在 PG 无 IDENTITY 时 INSERT 报 not-null 约束.
-- 兼容场景: 历史 dev/prod 库可能已手动修复, 重复执行应跳过.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 't_audit_log'
          AND column_name = 'id'
          AND is_identity = 'YES'
    ) THEN
        ALTER TABLE t_audit_log
            ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH 2065597735993499651);
    END IF;
END $$;