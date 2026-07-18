-- ============================================================
-- V63: 核心财务实体乐观锁版本 + 数据维护菜单权限
-- 合并: V63__add_version_columns.sql + V63__add_clear_data_menu_permission.sql
-- ============================================================

-- ========================
-- Part 1: 乐观锁 version 字段
-- ========================
-- V63: 核心财务实体加乐观锁 version 字段
-- P32: 财务数据完整性与并发控制增强
-- 2026-06-27

-- t_output_invoice
ALTER TABLE t_output_invoice ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_output_invoice.version IS '乐观锁版本号';

-- t_business_doc
ALTER TABLE t_business_doc ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_business_doc.version IS '乐观锁版本号';

-- t_voucher
ALTER TABLE t_voucher ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_voucher.version IS '乐观锁版本号';

-- t_bank_statement
ALTER TABLE t_bank_statement ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;
COMMENT ON COLUMN t_bank_statement.version IS '乐观锁版本号';

-- ========================
-- Part 2: 数据维护菜单权限
-- ========================
-- ============================================================
-- V63: 添加数据维护菜单权限
-- ============================================================

-- 数据维护菜单
-- 注意: t_menu.id 已被 V54 改为 GENERATED ALWAYS AS IDENTITY, 不能用 INSERT ... VALUES (id, ...)
-- 改用 DO block 处理序列问题
DO $$
DECLARE
    v_menu_id BIGINT := 97;
    v_exists BOOLEAN;
BEGIN
    -- 检查菜单是否已存在
    SELECT EXISTS(SELECT 1 FROM t_menu WHERE id = v_menu_id) INTO v_exists;
    
    IF NOT v_exists THEN
        -- 先更新序列确保可以从 97 开始
        IF (SELECT MAX(id) FROM t_menu) < v_menu_id THEN
            EXECUTE format('ALTER TABLE t_menu ALTER COLUMN id RESTART WITH %s', v_menu_id + 1);
        END IF;
        
        -- 插入菜单 (id 由序列自动生成)
        INSERT INTO t_menu (name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
        VALUES ('数据维护', 'system:clear-data:list', 'menu', 1, '/system/clear-data', 'system/clear-data/ClearDataView', 'Database', 6, TRUE, TRUE)
        RETURNING id INTO v_menu_id;
    END IF;
END $$;

-- 关联超级管理员角色到新菜单
-- 注意: t_role_menu.id 也被 V54 改为 GENERATED ALWAYS AS IDENTITY
DO $$
DECLARE
    v_role_id BIGINT;
    v_menu_id BIGINT;
BEGIN
    SELECT id INTO v_menu_id FROM t_menu WHERE permission_code = 'system:clear-data:list' LIMIT 1;
    IF v_menu_id IS NOT NULL THEN
        SELECT id INTO v_role_id FROM t_role WHERE code = 'admin';
        IF NOT EXISTS (SELECT 1 FROM t_role_menu WHERE role_id = v_role_id AND menu_id = v_menu_id) THEN
            INSERT INTO t_role_menu (role_id, menu_id) VALUES (v_role_id, v_menu_id);
        END IF;
    END IF;
END $$;
