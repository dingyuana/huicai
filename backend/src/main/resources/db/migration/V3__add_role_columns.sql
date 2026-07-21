-- ============================================================
-- V3: 为 t_role 补充 status/sort_order/dataScope 列
-- 这些列在 RoleEntity 中引用，但 V1 baseline 未包含
-- ============================================================

ALTER TABLE t_role
    ADD COLUMN IF NOT EXISTS status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS sort_order  INTEGER      NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS data_scope VARCHAR(50)  DEFAULT 'ALL';

COMMENT ON COLUMN t_role.status      IS '角色状态: ACTIVE/INACTIVE';
COMMENT ON COLUMN t_role.sort_order  IS '排序号';
COMMENT ON COLUMN t_role.description IS '角色描述';
COMMENT ON COLUMN t_role.data_scope  IS '数据权限范围: ALL/DEPT/SELF/CUSTOM';