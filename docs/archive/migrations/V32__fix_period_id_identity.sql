-- V32: t_period.id 添加 GENERATED ALWAYS AS IDENTITY
-- 此前创建时漏写了 IDENTITY, 导致 IdType.AUTO 无法自增.
ALTER TABLE t_period ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY;
SELECT setval(pg_get_serial_sequence('t_period', 'id'), COALESCE((SELECT MAX(id) FROM t_period), 0) + 1, false);
